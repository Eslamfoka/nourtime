package com.nourtime.app.service.blocking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.telecom.TelecomManager
import android.util.Log
import com.nourtime.app.core.permissions.NourPermission
import com.nourtime.app.core.permissions.PermissionChecker
import com.nourtime.app.core.blocking.BlockDecision
import com.nourtime.app.core.blocking.BlockInput
import com.nourtime.app.core.blocking.BlockPolicy
import com.nourtime.app.core.blocking.BlockReason
import com.nourtime.app.core.blocking.ChildDismissal
import com.nourtime.app.core.blocking.RingingGrace
import com.nourtime.app.core.blocking.ParentPass
import com.nourtime.app.core.blocking.ScreenOffTimer
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.detection.ForegroundState
import com.nourtime.app.core.media.MediaRules
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.TrustedClock
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.core.timer.TimerStatus
import com.nourtime.app.data.db.SchedulePeriod
import com.nourtime.app.data.schedule.ScheduleRepository
import com.nourtime.app.data.schedule.periodAt
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.data.settings.DayRules
import com.nourtime.app.data.settings.ParentSettings
import com.nourtime.app.data.settings.ParentSettingsRepository
import com.nourtime.app.data.apps.InstalledAppsRepository
import com.nourtime.app.feature.lock.AllowedApp
import com.nourtime.app.feature.lock.LockScreenState
import com.nourtime.app.feature.lock.templateFor
import com.nourtime.app.service.admin.ScreenLocker
import com.nourtime.app.service.media.MediaWatcher
import androidx.core.content.ContextCompat
import com.nourtime.app.core.locale.AppLocales
import com.nourtime.app.core.power.LoopPacing
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides, on every change and about once a second while the screen is on (every 30 s while it's off,
 * [LoopPacing]), whether something must be blocked, and shows or hides the lock overlay accordingly
 * (brief §3, §4, §5).
 */
@Singleton
class BlockCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tracker: ForegroundAppTracker,
    private val engine: TimeEngine,
    private val settings: ParentSettingsRepository,
    private val schedule: ScheduleRepository,
    private val pass: ParentPass,
    private val trustedClock: TrustedClock,
    private val overlay: LockOverlay,
    private val clock: DeviceClock,
    private val screenLocker: ScreenLocker,
    private val apps: InstalledAppsRepository,
    private val permissions: PermissionChecker,
    private val media: MediaWatcher,
) {
    private val screenOff = ScreenOffTimer()

    private data class Inputs(
        val fg: ForegroundState,
        val status: TimerStatus?,
        val settings: ParentSettings,
        val periods: List<SchedulePeriod>,
    )

    /** What the child dismissed with "OK"; the screen stays hidden until what's on screen changes. */
    private val dismissal = ChildDismissal()
    private val ringingGrace = RingingGrace()
    @Volatile private var lastInputs: Inputs? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run() {
        withContext(Dispatchers.Main) {
            overlay.onChildDismiss = ::onChildDismiss
        }
        // Every second while the screen is on; slowly while it's off. Restarted when the screen
        // changes, so the first check after it comes on is immediate and the next a second later.
        val ticker = tracker.state.map { it.screen.interactive }.distinctUntilChanged().transformLatest { interactive ->
            while (true) {
                emit(Unit)
                delay(LoopPacing.blockTickMs(interactive))
            }
        }
        // Only the system sends package broadcasts.
        ContextCompat.registerReceiver(context, packageReceiver, PACKAGE_CHANGES, ContextCompat.RECEIVER_EXPORTED)
        try {
            combine(tracker.state, engine.status, settings.settings, schedule.periods, pass.state) { fg, status, s, periods, _ ->
                Inputs(fg, status, s, periods)
            }.combine(ticker) { inputs, _ -> inputs }
                // A limited app starting to play (a headset button with the screen off) is checked at once.
                .combine(media.sessions) { inputs, _ -> inputs }
                .collect { evaluate(it) }
        } finally {
            runCatching { context.unregisterReceiver(packageReceiver) }
        }
    }

    /** Bumped when an app is installed, removed, updated or turned on or off: the allowed apps are looked up again. */
    private val installedVersion = AtomicInteger()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            installedVersion.incrementAndGet()
        }
    }

    private data class AllowedKey(val packages: Set<String>, val language: String, val installedVersion: Int)

    private val allowedCache = KeyedCache<AllowedKey, List<AllowedApp>>()

    /**
     * Limited apps' background audio, screen on or off: paused while they're blocked, and again as soon
     * as the child presses play in the notification shade (or on a headset).
     */
    private fun pauseLimitedMedia(limited: Set<String>, limitedBlocked: Boolean) {
        val toPause = MediaRules.toPause(media.sessions.value, limited, limitedBlocked, fullPass = pass.fullActive())
        for (pkg in toPause) {
            Log.i(TAG, "pausing $pkg's media")
            media.pause(pkg)
        }
    }

    private suspend fun evaluate(inputs: Inputs) {
        lastInputs = inputs
        val (fg, status, s, periods) = inputs
        if (!fg.screen.interactive) pass.revoke()

        val now = trustedClock.now()
        // The night before a weekend day can have its own bedtime (Phase 4a).
        val activeBedtime = DayRules.activeBedtime(s, now.toLocalDateTime())
        val bedtime = activeBedtime != null
        val timeUp = status?.phase == TimerPhase.LOCKED

        val audioMode = audioMode()
        val decision = BlockPolicy.decide(
            BlockInput(
                foreground = fg,
                limitedApps = s.limitedApps,
                lockType = s.lockType,
                timeUp = timeUp,
                bedtime = bedtime,
                protectionDegraded = status?.protectionDegraded ?: false,
                protectSystemSettings = s.protectSystemSettings,
                ownPackage = context.packageName,
                devicePass = pass.deviceActive(),
                fullPass = pass.fullActive(),
                // Read from the audio mode, so bounded: a minute per ring, three per half hour.
                phoneRinging = ringingGrace.exempt(
                    ringing = audioMode == AudioManager.MODE_RINGTONE || audioMode == MODE_CALL_SCREENING,
                    nowElapsed = clock.elapsedRealtime(),
                ),
                inCall = audioMode == AudioManager.MODE_IN_CALL,
                dialerPackage = runCatching { telecom.defaultDialerPackage }.getOrNull(),
                extraSettingsPackages = settingsScreens,
                // Asked only while an app store is in front, not on every tick.
                deviceAdminActive = fg.foreground !in BlockPolicy.APP_STORE_PACKAGES ||
                    permissions.isGranted(NourPermission.DEVICE_ADMIN),
                allowedDuringLock = s.allowedDuringLock,
            ),
        )

        pauseLimitedMedia(s.limitedApps, limitedBlocked = timeUp || bedtime || status?.protectionDegraded == true)

        val hidden = dismissal.isHidden(decision, fg, s.limitedApps, clock.elapsedRealtime())

        val screen = if (decision == null || hidden) null else LockScreenState(
            decision = decision,
            template = templateFor(decision.reason, periods.periodAt(now.minuteOfDay())?.kind),
            ageGroup = s.ageGroup ?: AgeGroup.AGES_3_6,
            gender = s.gender ?: ChildGender.GIRL,
            countdownMs = when (decision.reason) {
                BlockReason.TIME_UP -> status?.lockRemainingMs
                BlockReason.BEDTIME -> activeBedtime?.let { untilMinute(now, it.endMinute) }
                else -> null
            },
            countdownTotalMs = when (decision.reason) {
                // The lock period's full length: what has run plus what is left.
                BlockReason.TIME_UP -> status?.let { it.lockRemainingMs + it.lockElapsedMs }
                BlockReason.BEDTIME -> activeBedtime?.let { bedtimeLengthMs(it.startMinute, it.endMinute) }
                else -> null
            },
            soundEnabled = s.soundEnabled,
            allowedApps = if (decision.reason == BlockReason.TIME_UP || decision.reason == BlockReason.BEDTIME) {
                allowedApps(s.allowedDuringLock)
            } else {
                emptyList()
            },
            inCall = audioMode == AudioManager.MODE_IN_CALL,
        )
        val turnScreenOff = screenOff.update(
            nowElapsed = clock.elapsedRealtime(),
            lockPeriod = timeUp || bedtime,
            lockType = s.lockType,
            // Not while the child is playing a learning game on the lock screen.
            wholeDeviceBlocking = decision?.wholeDevice == true && !overlay.learningOpen,
            screenOn = fg.screen.interactive,
        )
        if (turnScreenOff) Log.i(TAG, "whole-device lock started: screen off=${screenLocker.lockNow()}")

        // Logged on any change of what the decision depends on, so "why wasn't it blocked" can be read later.
        val why = "decision=$decision fg=${fg.foreground} visible=${fg.visible} source=${fg.source} hidden=$hidden " +
            "usable=${fg.screen.usable} interactive=${fg.screen.interactive} keyguard=${fg.screen.keyguardLocked} " +
            "timeUp=$timeUp lockType=${s.lockType} pass=${pass.deviceActive()}/${pass.fullActive()}"
        if (why != lastWhy) {
            Log.i(TAG, why)
            lastWhy = why
        }
        withContext(Dispatchers.Main) { overlay.render(screen) }
    }

    private var lastWhy: String? = null

    private val audioManager by lazy { context.getSystemService(AudioManager::class.java) }
    private val settingsScreens: Set<String> by lazy { SettingsScreens.resolve(context) }

    private val telecom by lazy { context.getSystemService(TelecomManager::class.java) }

    /** Ringing, call screening or in a call. No permission needed. */
    private fun audioMode(): Int = runCatching { audioManager.mode }.getOrDefault(AudioManager.MODE_NORMAL)

    /** Launchable allowed apps, by name; resolved again only when the set, the language or the installed apps change. */
    private suspend fun allowedApps(packages: Set<String>): List<AllowedApp> {
        val key = AllowedKey(packages, AppLocales.locale(context).toLanguageTag(), installedVersion.get())
        return allowedCache.get(key) { k ->
            k.packages
                .filter { context.packageManager.getLaunchIntentForPackage(it) != null }
                .map { AllowedApp(it, apps.label(it), apps.icon(it)) }
                .sortedBy { it.label }
        }
    }

    /** The child tapped "OK": go home and keep the screen hidden while nothing changes. */
    private fun onChildDismiss() {
        val fg = lastInputs?.fg ?: return
        dismissal.dismiss(fg, clock.elapsedRealtime())
        overlay.goHome()
    }

    private fun ZonedDateTime.minuteOfDay() = hour * 60 + minute

    /** Bedtime from [start] to [end] (minutes of the day; may cross midnight). */
    private fun bedtimeLengthMs(start: Int, end: Int): Long {
        val day = 24 * 60
        val minutes = ((end - start + day) % day).let { if (it == 0) day else it }
        return minutes * 60_000L
    }

    private fun untilMinute(now: ZonedDateTime, minute: Int): Long {
        var end = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
        if (!end.isAfter(now)) end = end.plusDays(1)
        return Duration.between(now, end).toMillis()
    }

    private companion object {
        const val TAG = "BlockCoordinator"
        val PACKAGE_CHANGES = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        /** `AudioManager.MODE_CALL_SCREENING` (API 30); older phones never report it. */
        const val MODE_CALL_SCREENING = 4
    }
}
