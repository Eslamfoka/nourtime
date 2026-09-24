package com.nourtime.app.service.timer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.detection.UsageStatsSource
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.data.settings.ParentSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
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
    @Inject lateinit var engine: TimeEngine
    @Inject lateinit var settings: ParentSettingsRepository
    @Inject lateinit var usageStats: UsageStatsSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = tracker.refreshScreen()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ProtectionNotifications.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            ProtectionNotifications.STATUS_ID,
            ProtectionNotifications.status(this, engine.status.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        tracker.refreshScreen()
        if (!tracker.accessibilityConnected) tracker.onAccessibilityDisconnected(usageStats.available)

        scope.launch { runTimer() }
        scope.launch { runUsageStatsFallback() }
        scope.launch { runNotifications() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
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
                Triple(s.phase, (if (s.phase == TimerPhase.LOCKED) s.lockRemainingMs else s.remainingMs) / 60_000, s.protectionDegraded)
            }
            .distinctUntilChanged()
            .collect { (_, _, degraded) ->
                val status = engine.status.value
                runCatching {
                    NotificationManagerCompat.from(this@TimerService)
                        .notify(ProtectionNotifications.STATUS_ID, ProtectionNotifications.status(this@TimerService, status))
                }
                if (degraded) ProtectionNotifications.showDegraded(this@TimerService)
                else ProtectionNotifications.clearDegraded(this@TimerService)
                if (status?.source == DetectionSource.NONE) Log.w(TAG, "No foreground detection available")
            }
    }

    companion object {
        private const val TAG = "TimerService"
        private const val TICK_MS = 1_000L
        private const val IDLE_TICK_MS = 30_000L
        private const val POLL_MS = 2_000L
        private const val FIRST_LOOKBACK_MS = 60 * 60_000L
        private const val POLL_LOOKBACK_MS = 15_000L

        /** Safe to call from the foreground activity, the Accessibility service and boot. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java)) }
                .onFailure { Log.w(TAG, "Couldn't start timer service", it) }
        }
    }
}
