package com.nourtime.app.service.detection

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.nourtime.app.core.coroutines.doublingPause
import com.nourtime.app.core.coroutines.keepRunning
import com.nourtime.app.core.detection.AppWindow
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.detection.ForegroundRules
import com.nourtime.app.core.detection.UsageStatsSource
import com.nourtime.app.data.mode.AppMode
import com.nourtime.app.data.mode.AppModeRepository
import com.nourtime.app.data.onboarding.OnboardingRepository
import com.nourtime.app.data.settings.ParentSettingsRepository
import com.nourtime.app.service.blocking.LockOverlay
import com.nourtime.app.service.media.MediaWatcher
import com.nourtime.app.service.timer.TimerService
import com.nourtime.app.service.watchdog.TimerServiceKeeper
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    @Inject lateinit var overlay: LockOverlay
    @Inject lateinit var modes: AppModeRepository
    @Inject lateinit var settings: ParentSettingsRepository
    @Inject lateinit var media: MediaWatcher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Window lookups are IPC calls that, for Nour Time's own windows, must be answered by this
     * process's main thread. Doing them on the main thread deadlocks until timeout, so they run on
     * one background worker, and bursts of events collapse into a single refresh.
     */
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    /** Only these apps' media notifications are looked at (for their session handle, nothing else). */
    @Volatile private var limitedApps: Set<String> = emptySet()

    // Cached so that events never wait for the settings store; until read, nothing is restarted here.
    @Volatile private var mode: AppMode? = null
    @Volatile private var setupComplete = false

    private val keeper = TimerServiceKeeper(
        nowMs = SystemClock::elapsedRealtime,
        mode = { mode },
        setupComplete = { setupComplete },
        timerRunning = { TimerService.running },
        start = {
            Log.w(TAG, "timer service wasn't running; starting it")
            TimerService.start(this)
        },
    )

    /** One refresh at a time, also while an unbound connection's loop is still finishing its last one. */
    private val windowWorker = Dispatchers.Default.limitedParallelism(1)

    /**
     * Everything one connection runs. Cancelled when the system unbinds and before a new connection
     * starts its own, so readers never pile up; the refresh requests and cached values carry over.
     */
    private var connection: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "connected ${System.identityHashCode(this)}")
        tracker.onAccessibilityConnected()
        overlay.attach(this)
        connection?.cancel()
        connection = scope.launch {
            launch(windowWorker) {
                refreshLoop(refreshRequests, ::publishWindows) { e -> Log.e(TAG, "window refresh failed", e) }
            }
            // Each reader restarts after a settings-store error instead of keeping its last value.
            reader("timer start") {
                if (onboarding.isComplete.first()) TimerService.start(this@NourAccessibilityService)
            }
            reader("mode") { modes.mode.collect { mode = it } }
            reader("limited apps") { settings.settings.collect { limitedApps = it.limitedApps } }
            reader("setup complete") { onboarding.isComplete.collect { setupComplete = it } }
        }
        refreshRequests.trySend(Unit)
    }

    private fun CoroutineScope.reader(name: String, block: suspend () -> Unit) = launch {
        keepRunning(name, { doublingPause(it, READER_RETRY_FIRST_MS, READER_RETRY_MAX_MS) }, { msg, e -> Log.e(TAG, msg, e) }, block = block)
    }

    /** Owner package of each window, from event metadata: used when a window's root can't be read. */
    private val eventPackages = ConcurrentHashMap<Int, String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                val pkg = event.packageName?.toString()
                if (pkg != null && event.windowId != AppWindow.NO_WINDOW_ID) eventPackages[event.windowId] = pkg
                refreshRequests.trySend(Unit)
                keeper.onEvent()
            }
            // A limited app's media notification: only its media session handle is taken, so its
            // background audio can be paused during a lock and counted (MediaWatcher). Every other
            // app's notification is ignored here, unread.
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg !in limitedApps) return
                (event.parcelableData as? Notification)?.let { media.onMediaNotification(pkg, it) }
            }
        }
    }

    private var lastLogged: String? = null

    private fun publishWindows() {
        val read = runCatching { windows }.getOrNull()?.map { it.toAppWindow() }
        if (read != null) eventPackages.keys.retainAll(read.mapTo(HashSet()) { it.windowId })
        val appWindows = ForegroundRules.resolveUnknown(read, eventPackages) {
            Log.w(TAG, if (read == null) "window list unavailable, using usage stats" else "active window owner unknown, using usage stats")
            if (usageStats.available) usageStats.latestForeground(tracker.ignoredPackages, FALLBACK_LOOKBACK_MS) else null
        }
        val summary = appWindows.joinToString { "${it.windowId}:${it.packageName}${if (it.isActive) "*" else ""}" }
        if (summary != lastLogged) {
            Log.d(TAG, "windows [$summary]")
            lastLogged = summary
        }
        tracker.onWindows(appWindows)
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
            windowId = id,
        )
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "unbind ${System.identityHashCode(this)}")
        connection?.cancel()
        connection = null
        overlay.detach(this)
        tracker.onAccessibilityDisconnected(usageStats.available)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "destroy ${System.identityHashCode(this)}")
        overlay.detach(this)
        tracker.onAccessibilityDisconnected(usageStats.available)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "NourA11y"

        /** Long enough to find the app that was opened before its window became unreadable. */
        const val FALLBACK_LOOKBACK_MS = 60 * 60_000L

        /** A failed settings reader starts again after 1 s, then 2, 4… up to 30 s. */
        const val READER_RETRY_FIRST_MS = 1_000L
        const val READER_RETRY_MAX_MS = 30_000L
    }
}

/**
 * One window refresh per request. A refresh that throws (a system call or the tracker failing) is
 * logged and skipped: the next event refreshes again, instead of the loop ending, which would have
 * left detection frozen until the service reconnected (2026-10-17 audit).
 */
internal suspend fun refreshLoop(requests: ReceiveChannel<Unit>, publish: () -> Unit, onError: (Exception) -> Unit) {
    for (request in requests) {
        try {
            publish()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
    }
}
