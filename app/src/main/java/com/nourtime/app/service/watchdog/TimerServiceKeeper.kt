package com.nourtime.app.service.watchdog

import com.nourtime.app.data.mode.AppMode

/**
 * Restarts the timer service from Accessibility events. Blocking runs only inside the timer service,
 * so if it alone was stopped (a phone maker's cleaner, "Stop" in the active-apps list) while
 * Accessibility stayed bound, nothing would cover limited apps until the 15-minute watchdog.
 * Same rule as [Watchdog]; checked at most once per [INTERVAL_MS] however many events arrive.
 */
class TimerServiceKeeper(
    private val nowMs: () -> Long,
    private val mode: () -> AppMode?,
    private val setupComplete: () -> Boolean,
    private val timerRunning: () -> Boolean,
    private val start: () -> Unit,
) {
    private var lastCheckMs: Long? = null

    /** Returns whether it started the timer service. */
    @Synchronized
    fun onEvent(): Boolean {
        val now = nowMs()
        val last = lastCheckMs
        if (last != null && now - last < INTERVAL_MS) return false
        lastCheckMs = now
        if (Watchdog.action(mode(), setupComplete(), timerRunning()) != WatchdogAction.START) return false
        start()
        return true
    }

    companion object {
        const val INTERVAL_MS = 10_000L
    }
}
