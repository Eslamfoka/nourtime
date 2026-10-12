package com.nourtime.app.service.watchdog

import com.nourtime.app.data.mode.AppMode
import org.junit.Assert.assertEquals
import org.junit.Test

// Seen on the emulator (2026-10-12): Android killed Nour Time during a slow start at boot ("failed to
// complete startup") and nothing started it again, so protection stayed off until the app was opened.
class WatchdogTest {

    @Test
    fun `a set-up child's phone without the timer gets it started`() {
        assertEquals(WatchdogAction.START, Watchdog.action(AppMode.CHILD, setupComplete = true, timerRunning = false))
    }

    @Test
    fun `nothing to do while the timer runs`() {
        assertEquals(WatchdogAction.NONE, Watchdog.action(AppMode.CHILD, setupComplete = true, timerRunning = true))
    }

    @Test
    fun `nothing before setup is finished`() {
        assertEquals(WatchdogAction.NONE, Watchdog.action(AppMode.CHILD, setupComplete = false, timerRunning = false))
    }

    @Test
    fun `a parent's phone has no timer to start`() {
        assertEquals(WatchdogAction.NONE, Watchdog.action(AppMode.PARENT, setupComplete = false, timerRunning = false))
        assertEquals(WatchdogAction.NONE, Watchdog.action(null, setupComplete = false, timerRunning = false))
    }
}
