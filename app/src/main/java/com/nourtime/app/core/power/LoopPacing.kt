package com.nourtime.app.core.power

/**
 * How often the protection loops run (2026-10-28 audit, battery). While the screen is on, blocking and
 * counting react within a second, as before. While it's off nothing can be seen or used, so the loops
 * slow down; whatever matters (the screen coming on, a window, a setting, the timer, media starting)
 * still wakes the block check at once, since it also runs on every change of its inputs.
 */
object LoopPacing {
    const val ACTIVE_MS = 1_000L
    /** The timer's existing idle tick, now shared by the block check. */
    const val IDLE_MS = 30_000L
    /**
     * The screen re-read while the screen is off: the third way to notice it coming on, after the
     * screen broadcast and the display listener.
     */
    const val SCREEN_OFF_SYNC_MS = 5_000L

    /** The timer: counts only on a usable screen; otherwise lock periods and day changes are checked slowly. */
    fun timerTickMs(screenUsable: Boolean): Long = if (screenUsable) ACTIVE_MS else IDLE_MS

    /** The block check: every second while the screen is on, also on the lock screen (the countdown shows there). */
    fun blockTickMs(screenInteractive: Boolean): Long = if (screenInteractive) ACTIVE_MS else IDLE_MS

    /**
     * The screen re-read behind the broadcasts: every second while the screen is on (a missed unlock,
     * seen on Honor, is exactly what it catches), slower while it's off.
     */
    fun screenSyncMs(screenInteractive: Boolean): Long = if (screenInteractive) ACTIVE_MS else SCREEN_OFF_SYNC_MS

    /** Usage stats are polled only while they're the detection and there's a usable screen to look at. */
    fun usagePollNeeded(accessibilityConnected: Boolean, screenUsable: Boolean): Boolean =
        !accessibilityConnected && screenUsable
}
