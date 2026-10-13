package com.nourtime.app.core.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lock-screen state is re-read on every tick, so a missed broadcast can't stop blocking. */
class ForegroundAppTrackerScreenTest {

    private var screen = ScreenState(interactive = true, keyguardLocked = true)
    private var ignoredReads = 0
    private val tracker = ForegroundAppTracker(
        readScreen = { screen },
        readIgnored = {
            ignoredReads++
            setOf("com.android.systemui")
        },
    )

    @Test
    fun `an unlock without a broadcast is picked up by the next sync`() {
        assertFalse(tracker.state.value.screen.usable)
        screen = ScreenState(interactive = true, keyguardLocked = false)
        assertTrue(tracker.syncScreen())
        assertTrue(tracker.state.value.screen.usable)
    }

    @Test
    fun `a screen turned off without a broadcast is picked up too`() {
        screen = ScreenState(interactive = true, keyguardLocked = false)
        tracker.syncScreen()
        screen = ScreenState(interactive = false, keyguardLocked = true)
        assertTrue(tracker.syncScreen())
        assertFalse(tracker.state.value.screen.usable)
    }

    @Test
    fun `nothing changes and nothing is recomputed while the screen stays the same`() {
        val reads = ignoredReads
        val before = tracker.state.value
        assertFalse(tracker.syncScreen())
        assertFalse(tracker.syncScreen())
        assertEquals(before, tracker.state.value)
        assertEquals(reads, ignoredReads)
    }

    @Test
    fun `a broadcast refresh still updates the screen and the keyboards`() {
        val reads = ignoredReads
        screen = ScreenState(interactive = true, keyguardLocked = false)
        tracker.refreshScreen()
        assertTrue(tracker.state.value.screen.usable)
        assertEquals(reads + 1, ignoredReads)
    }
}
