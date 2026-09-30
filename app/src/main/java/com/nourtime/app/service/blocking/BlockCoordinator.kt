package com.nourtime.app.service.blocking

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import com.nourtime.app.core.blocking.BlockDecision
import com.nourtime.app.core.blocking.BlockInput
import com.nourtime.app.core.blocking.BlockPolicy
import com.nourtime.app.core.blocking.BlockReason
import com.nourtime.app.core.blocking.ParentPass
import com.nourtime.app.core.blocking.ScreenOffTimer
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.detection.ForegroundState
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides, about once a second and on every change, whether something must be blocked, and shows
 * or hides the lock overlay accordingly (brief §3, §4, §5).
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
) {
    private val screenOff = ScreenOffTimer()

    private data class Inputs(
        val fg: ForegroundState,
        val status: TimerStatus?,
        val settings: ParentSettings,
        val periods: List<SchedulePeriod>,
    )

    /** What the child dismissed with "OK"; the screen stays hidden until what's on screen changes. */
    @Volatile private var dismissedFor: Pair<String?, Set<String>>? = null
    @Volatile private var lastInputs: Inputs? = null

    suspend fun run() {
        withContext(Dispatchers.Main) {
            overlay.onChildDismiss = ::onChildDismiss
        }
        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(TICK_MS)
            }
        }
        combine(tracker.state, engine.status, settings.settings, schedule.periods, pass.state) { fg, status, s, periods, _ ->
            Inputs(fg, status, s, periods)
        }.combine(ticker) { inputs, _ -> inputs }
            .collect { evaluate(it) }
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
                phoneCallActive = phoneInUse(fg.foreground),
                allowedDuringLock = s.allowedDuringLock,
            ),
        )

        val key = fg.foreground to fg.visible
        if (decision == null) dismissedFor = null
        val hidden = decision != null && !decision.wholeDevice && dismissedFor == key

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
            soundEnabled = s.soundEnabled,
            allowedApps = if (decision.reason == BlockReason.TIME_UP || decision.reason == BlockReason.BEDTIME) {
                allowedApps(s.allowedDuringLock)
            } else {
                emptyList()
            },
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
            "timeUp=$timeUp pass=${pass.deviceActive()}/${pass.fullActive()}"
        if (why != lastWhy) {
            Log.i(TAG, why)
            lastWhy = why
        }
        withContext(Dispatchers.Main) { overlay.render(screen) }
    }

    private var lastWhy: String? = null

    private val audioManager by lazy { context.getSystemService(AudioManager::class.java) }
    private val telecom by lazy { context.getSystemService(TelecomManager::class.java) }

    /** Ringing, in a call, or the phone app (possibly a third-party default dialer) is open. No permission needed. */
    private fun phoneInUse(foreground: String?): Boolean {
        val mode = runCatching { audioManager.mode }.getOrDefault(AudioManager.MODE_NORMAL)
        if (mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mode == AudioManager.MODE_CALL_SCREENING) return true
        val dialer = runCatching { telecom.defaultDialerPackage }.getOrNull()
        return foreground != null && foreground == dialer
    }

    /** Launchable allowed apps, by name. Labels and icons are cached by [InstalledAppsRepository]. */
    private suspend fun allowedApps(packages: Set<String>): List<AllowedApp> = packages
        .filter { context.packageManager.getLaunchIntentForPackage(it) != null }
        .map { AllowedApp(it, apps.label(it), apps.icon(it)) }
        .sortedBy { it.label }

    /** The child tapped "OK": go home and keep the screen hidden while nothing changes. */
    private fun onChildDismiss() {
        val fg = lastInputs?.fg ?: return
        dismissedFor = fg.foreground to fg.visible
        overlay.goHome()
    }

    private fun ZonedDateTime.minuteOfDay() = hour * 60 + minute

    private fun untilMinute(now: ZonedDateTime, minute: Int): Long {
        var end = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
        if (!end.isAfter(now)) end = end.plusDays(1)
        return Duration.between(now, end).toMillis()
    }

    private companion object {
        const val TAG = "BlockCoordinator"
        const val TICK_MS = 1_000L
    }
}
