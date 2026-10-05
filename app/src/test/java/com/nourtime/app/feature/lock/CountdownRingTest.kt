package com.nourtime.app.feature.lock

import org.junit.Assert.assertEquals
import org.junit.Test

/** The 7–9 "Time's up" ring (brief §12): the gold part is what's left until the refill. */
class CountdownRingTest {

    private val hour = 60 * 60_000L

    @Test
    fun `the ring shows the share of the lock still to go`() {
        assertEquals(0.25f, countdownFraction(ms = 90 * 60_000L, totalMs = 6 * hour), 0.0001f)
    }

    @Test
    fun `a fresh lock is a full ring and a finished one is empty`() {
        assertEquals(1f, countdownFraction(6 * hour, 6 * hour), 0f)
        assertEquals(0f, countdownFraction(0, 6 * hour), 0f)
    }

    @Test
    fun `an unknown total shows a full ring, and odd values stay in range`() {
        assertEquals(1f, countdownFraction(hour, null), 0f)
        assertEquals(1f, countdownFraction(hour, 0), 0f)
        assertEquals(1f, countdownFraction(7 * hour, 6 * hour), 0f)
    }
}
