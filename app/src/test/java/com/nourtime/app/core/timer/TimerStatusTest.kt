package com.nourtime.app.core.timer

import com.nourtime.app.core.detection.DetectionSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Home shows: a learning break is apps open while a lock waits underneath. */
class TimerStatusTest {

    private fun status(phase: TimerPhase, lockPendingMs: Long) =
        TimerStatus(phase, 5 * 60_000L, 60 * 60_000L, 0, emptySet(), DetectionSource.ACCESSIBILITY, lockPendingMs = lockPendingMs)

    @Test
    fun `available with a lock waiting is a break`() {
        assertTrue(status(TimerPhase.AVAILABLE, lockPendingMs = 60_000).onBreak)
    }

    @Test
    fun `plain available or locked is not a break`() {
        assertFalse(status(TimerPhase.AVAILABLE, lockPendingMs = 0).onBreak)
        assertFalse(status(TimerPhase.LOCKED, lockPendingMs = 0).onBreak)
    }
}
