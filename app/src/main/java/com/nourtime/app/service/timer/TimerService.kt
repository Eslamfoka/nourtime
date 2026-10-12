package com.nourtime.app.service.timer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import com.nourtime.app.core.blocking.BlockPolicy
import com.nourtime.app.data.apps.InstalledAppsRepository
import com.nourtime.app.data.apps.NewApps
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import android.provider.Settings
import com.nourtime.app.data.protection.ProtectionAlert
import com.nourtime.app.data.protection.ProtectionAlertsRepository
import android.util.Log
import com.nourtime.app.service.watchdog.WatchdogJobService
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nourtime.app.core.blocking.ParentPass
import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.detection.UsageStatsSource
import com.nourtime.app.core.security.ParentSession
import com.nourtime.app.core.time.TrustedClock
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.core.timer.TimerStatus
import com.nourtime.app.data.settings.ParentSettingsRepository
import com.nourtime.app.data.usage.UsageRepository
import com.nourtime.app.remote.child.DeviceIdentity
import com.nourtime.app.remote.child.RemoteSync
import com.nourtime.app.service.blocking.BlockCoordinator
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Runs the time engine in the background (brief §9): charges the budget while a limited app is on a
 * usable screen, polls usage stats while Accessibility is off, and keeps the status notification.
 */
@AndroidEntryPoint
class TimerService : Service() {

    @Inject lateinit var tracker: ForegroundAppTracker
    @Inject lateinit var pass: ParentPass
    @Inject lateinit var session: ParentSession
    @Inject lateinit var engine: TimeEngine
    @Inject lateinit var settings: ParentSettingsRepository
    @Inject lateinit var usageStats: UsageStatsSource
    @Inject lateinit var blocking: BlockCoordinator
    @Inject lateinit var usage: UsageRepository
    @Inject lateinit var trustedClock: TrustedClock
    // Lazy: Firebase is only started once this phone pairs with a parent's phone.
    @Inject lateinit var remoteSync: Lazy<RemoteSync>
    @Inject lateinit var deviceIdentity: DeviceIdentity
    @Inject lateinit var apps: InstalledAppsRepository
    @Inject lateinit var alerts: ProtectionAlertsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                pass.onScreenOff()
                session.onScreenOff()
            }
            tracker.refreshScreen()
        }
    }

    private val appsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            appsChanged.tryEmit(Unit)
        }
    }

    // Sent to this user's running apps when the phone switches to another user (guest, second account).
    private val userReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.w(TAG, "switched to another user")
            scope.launch { alerts.recordOtherUser(System.currentTimeMillis()) }
            ProtectionNotifications.showProtectionAlert(this@TimerService, ProtectionAlert.OTHER_USER)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        // Foreground first, before Hilt builds the dependencies in super.onCreate(): a foreground start
        // has only a few seconds, and a slow phone missed them (the status text follows once the
        // engine runs).
        goForeground(status = null)
        super.onCreate()
        running = true
        WatchdogJobService.schedule(this)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            // Exported: on Honor, USER_PRESENT comes from System UI's own uid, and a not-exported
            // receiver drops it, so the phone stayed "locked" after the first unlock (no blocking,
            // no counting). Only the system can send these protected broadcasts.
            ContextCompat.RECEIVER_EXPORTED,
        )
        // Only the system sends package broadcasts.
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") },
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            userReceiver,
            IntentFilter(Intent.ACTION_USER_BACKGROUND),
            ContextCompat.RECEIVER_EXPORTED,
        )
        tracker.refreshScreen()
        if (!tracker.accessibilityConnected) tracker.onAccessibilityDisconnected(usageStats.available)

        scope.launch { resilient("timer") { runTimer() } }
        scope.launch { resilient("usage-stats fallback") { runUsageStatsFallback() } }
        scope.launch { resilient("notifications") { runNotifications() } }
        scope.launch { resilient("degraded alert") { runDegradedAlert() } }
        scope.launch { resilient("blocking") { blocking.run() } }
        scope.launch { resilient("stats cleanup") { runStatsCleanup() } }
        scope.launch { resilient("new apps") { runNewApps() } }
        scope.launch { resilient("protection alerts") { runProtectionAlerts() } }
        // Phase 2: only starts once this phone is paired with a parent's phone.
        scope.launch {
            deviceIdentity.pairedOwner.filterNotNull().first()
            resilient("remote sync") { remoteSync.get().run() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every foreground start needs startForeground(), also when the service was already running
        // (it may not have been allowed in the foreground the first time).
        goForeground(engine.status.value)
        return START_STICKY
    }

    private fun goForeground(status: TimerStatus?) {
        ProtectionNotifications.ensureChannels(this)
        // Android 12+ can refuse this when the service was started from the background; it then keeps
        // running as a plain service rather than crashing the app.
        runCatching {
            ServiceCompat.startForeground(
                this,
                ProtectionNotifications.STATUS_ID,
                ProtectionNotifications.status(this, status),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        }.onFailure { Log.w(TAG, "Couldn't go foreground", it) }
    }

    override fun onDestroy() {
        running = false
        unregisterReceiver(screenReceiver)
        unregisterReceiver(packageReceiver)
        unregisterReceiver(userReceiver)
        runBlocking { withContext(NonCancellable) { engine.flush() } }
        scope.cancel()
        super.onDestroy()
    }

    /** Re-evaluates on every detection or settings change, and ticks while something may be counting. */
    private suspend fun runTimer() {
        combine(tracker.state, settings.settings) { fg, s -> fg to s }.collectLatest { (fg, s) ->
            val inUse = fg.limitedInUse(s.limitedApps)
            while (true) {
                engine.update(inUse, fg.source, s)
                delay(if (fg.screen.usable) TICK_MS else IDLE_TICK_MS)
            }
        }
    }

    /** While Accessibility is off, usage stats keep detection (and fail-closed blocking) going. */
    private suspend fun runUsageStatsFallback() {
        var firstPoll = true
        while (true) {
            if (!tracker.accessibilityConnected && tracker.state.value.screen.usable) {
                val available = usageStats.available
                val pkg = if (available) {
                    usageStats.latestForeground(tracker.ignoredPackages, if (firstPoll) FIRST_LOOKBACK_MS else POLL_LOOKBACK_MS)
                } else {
                    null
                }
                firstPoll = false
                tracker.onUsageStats(pkg, available)
            } else if (tracker.accessibilityConnected) {
                firstPoll = true
            }
            delay(POLL_MS)
        }
    }

    private suspend fun runNotifications() {
        engine.status.filterNotNull()
            .map { s ->
                // Minute resolution is enough for the notification; avoid re-posting every second.
                s.phase to (if (s.phase == TimerPhase.LOCKED) s.lockRemainingMs else s.remainingMs) / 60_000
            }
            .distinctUntilChanged()
            .collect {
                ProtectionNotifications.notify(
                    this@TimerService,
                    ProtectionNotifications.STATUS_ID,
                    ProtectionNotifications.status(this@TimerService, engine.status.value),
                )
            }
    }

    /**
     * Alerts the parent when protection is weakened. Waits first: right after a reboot this service
     * often starts a few seconds before Accessibility reconnects, which isn't worth an alert.
     */
    private suspend fun runDegradedAlert() {
        engine.status.filterNotNull()
            .map { it.source }
            .distinctUntilChanged()
            .collectLatest { source ->
                if (source == DetectionSource.ACCESSIBILITY) {
                    ProtectionNotifications.clearDegraded(this@TimerService)
                } else {
                    delay(DEGRADED_ALERT_DELAY_MS)
                    if (source == DetectionSource.NONE) Log.w(TAG, "No foreground detection available")
                    ProtectionNotifications.showDegraded(this@TimerService)
                }
            }
    }

    /** Limits apps installed after protection started (also those installed while it wasn't running). */
    private suspend fun runNewApps() {
        appsChanged.onStart { emit(Unit) }.collectLatest {
            val s = settings.settings.first()
            val known = settings.knownApps.first()
            val launchable = apps.launchableApps().mapTo(HashSet()) { it.packageName }
            val r = NewApps.check(known, launchable, s.limitNewApps, s.limitedApps, s.allowedDuringLock, neverLimited())
            if (r.known != known || r.toLimit.isNotEmpty()) settings.recordApps(r.known, r.toLimit)
            if (r.toLimit.isNotEmpty()) {
                Log.i(TAG, "limited new apps: ${r.toLimit}")
                ProtectionNotifications.showNewAppsLimited(this@TimerService, r.toLimit.map { apps.label(it) })
            }
        }
    }

    /** The phone, Settings, app stores and home screens: limiting them could cut the child off from calls. */
    private fun neverLimited(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val launchers = runCatching { packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName } }
            .getOrDefault(emptyList())
        val dialer = runCatching { getSystemService(TelecomManager::class.java).defaultDialerPackage }.getOrNull()
        return BlockPolicy.SYSTEM_SETTINGS_PACKAGES + BlockPolicy.PHONE_PACKAGES + BlockPolicy.APP_STORE_PACKAGES +
            launchers + listOfNotNull(dialer)
    }

    /**
     * A restart Nour Time didn't run in (safe mode: it can't be blocked, but it can be noticed) and
     * USB debugging, which lets a computer switch Nour Time off.
     */
    private suspend fun runProtectionAlerts() {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (boot >= 0 && alerts.recordBoot(boot, System.currentTimeMillis())) {
            Log.w(TAG, "a restart without Nour Time (boot $boot)")
            ProtectionNotifications.showProtectionAlert(this, ProtectionAlert.MISSED_RESTART)
        }
        var usbBefore = alerts.alerts.first().usbDebugging
        while (true) {
            val usb = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            if (usb != usbBefore) {
                alerts.setUsbDebugging(usb)
                if (usb) ProtectionNotifications.showProtectionAlert(this, ProtectionAlert.USB_DEBUGGING)
                usbBefore = usb
            }
            delay(USB_CHECK_MS)
        }
    }

    /** Keeps the stats database small: only recent days are shown. */
    private suspend fun runStatsCleanup() {
        while (true) {
            usage.pruneBefore(trustedClock.now().toLocalDate().minusDays(STATS_KEEP_DAYS))
            delay(CLEANUP_EVERY_MS)
        }
    }

    /**
     * Keeps a protection loop alive: a failure (e.g. a storage error) is logged and the loop restarts
     * after a pause, instead of crashing the process or silently ending protection.
     */
    private suspend fun resilient(name: String, block: suspend () -> Unit) {
        while (true) {
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$name failed; restarting", e)
                delay(RESTART_DELAY_MS)
            }
        }
    }

    companion object {
        private const val TAG = "TimerService"
        private const val TICK_MS = 1_000L
        private const val IDLE_TICK_MS = 30_000L
        private const val POLL_MS = 2_000L
        private const val FIRST_LOOKBACK_MS = 60 * 60_000L
        private const val POLL_LOOKBACK_MS = 15_000L
        private const val DEGRADED_ALERT_DELAY_MS = 15_000L
        private const val RESTART_DELAY_MS = 5_000L
        private const val STATS_KEEP_DAYS = 30L
        private const val CLEANUP_EVERY_MS = 12 * 60 * 60_000L
        private const val USB_CHECK_MS = 60_000L

        /** True while the service runs in this process (the watchdog checks it). */
        @Volatile
        var running: Boolean = false
            private set

        /** Safe to call from the foreground activity, the Accessibility service, boot and the watchdog. */
        fun start(context: Context): Boolean {
            val intent = Intent(context, TimerService::class.java)
            return ServiceStart.start(
                plain = { context.startService(intent) },
                foreground = { ContextCompat.startForegroundService(context, intent) },
            ).onFailure { Log.w(TAG, "Couldn't start timer service", it) }.isSuccess
        }
    }
}
