package com.nourtime.app.service.detection

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.nourtime.app.core.detection.AppWindow
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.detection.UsageStatsSource
import com.nourtime.app.data.onboarding.OnboardingRepository
import com.nourtime.app.service.timer.TimerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detects which apps are on screen, including picture-in-picture and split screen.
 *
 * Privacy (Play Accessibility policy): from each window this reads only the window type and the
 * package name that owns it. It never reads text, view content or anything the child types.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class NourAccessibilityService : AccessibilityService() {

    @Inject lateinit var tracker: ForegroundAppTracker
    @Inject lateinit var onboarding: OnboardingRepository
    @Inject lateinit var usageStats: UsageStatsSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Window lookups are IPC calls that, for Nour Time's own windows, must be answered by this
     * process's main thread. Doing them on the main thread deadlocks until timeout, so they run on
     * one background worker, and bursts of events collapse into a single refresh.
     */
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onServiceConnected() {
        super.onServiceConnected()
        tracker.onAccessibilityConnected()
        scope.launch(Dispatchers.Default.limitedParallelism(1)) {
            for (request in refreshRequests) publishWindows()
        }
        refreshRequests.trySend(Unit)
        scope.launch {
            if (onboarding.isComplete.first()) TimerService.start(this@NourAccessibilityService)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> refreshRequests.trySend(Unit)
        }
    }

    private fun publishWindows() {
        val list = runCatching { windows }.getOrNull() ?: return
        tracker.onWindows(list.map { it.toAppWindow() })
    }

    /** Reads the owning package of the window's root node, and nothing else from it. */
    private fun AccessibilityWindowInfo.toAppWindow(): AppWindow {
        val root = runCatching { root }.getOrNull()
        val pkg = root?.packageName?.toString()
        @Suppress("DEPRECATION")
        if (root != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) root.recycle()
        return AppWindow(
            packageName = pkg,
            isApplication = type == AccessibilityWindowInfo.TYPE_APPLICATION,
            isActive = isActive || isFocused,
        )
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        tracker.onAccessibilityDisconnected(usageStats.available)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tracker.onAccessibilityDisconnected(usageStats.available)
        scope.cancel()
        super.onDestroy()
    }
}
