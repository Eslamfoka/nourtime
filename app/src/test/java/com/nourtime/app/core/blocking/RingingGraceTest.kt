package com.nourtime.app.core.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A ringing phone uncovers the screen so the call can be answered, but only for a real ring's length:
 * the ring is read from the audio mode, which repeated calls or an app can keep setting (2026-10-13 audit).
 */
class RingingGraceTest {

    private val grace = RingingGrace()
    private val second = 1_000L

    @Test
    fun `nothing is uncovered while nothing rings`() {
        assertFalse(grace.exempt(ringing = false, nowElapsed = 0))
    }

    @Test
    fun `a ring is uncovered for up to a minute`() {
        assertTrue(grace.exempt(ringing = true, nowElapsed = 0))
        assertTrue(grace.exempt(ringing = true, nowElapsed = 59 * second))
        assertFalse(grace.exempt(ringing = true, nowElapsed = 60 * second))
    }

    @Test
    fun `rings share three minutes per half hour, however they are cut up`() {
        var t = 0L
        // Six 30-second rings: the first six minutes of ringing would be 3 minutes of uncovered screen.
        repeat(6) {
            assertTrue(grace.exempt(ringing = true, nowElapsed = t))
            t += 29 * second
            assertTrue(grace.exempt(ringing = true, nowElapsed = t))
            t += 1 * second
            grace.exempt(ringing = false, nowElapsed = t)
            t += 10 * second
        }
        assertFalse("budget used up", grace.exempt(ringing = true, nowElapsed = t))
        grace.exempt(ringing = false, nowElapsed = t + second)
        // Half an hour after those rings, a new ring is uncovered again.
        t += 30 * 60 * second
        assertTrue(grace.exempt(ringing = true, nowElapsed = t))
    }

    @Test
    fun `a ring switched off and on again still counts toward the budget`() {
        var t = 0L
        // Three 50-second pieces: 150 s used.
        repeat(3) {
            grace.exempt(ringing = true, nowElapsed = t)
            t += 50 * second
            grace.exempt(ringing = false, nowElapsed = t)
            t += second
        }
        // The fourth piece gets only the 30 s left of the 3 minutes, not a fresh minute.
        assertTrue(grace.exempt(ringing = true, nowElapsed = t))
        assertTrue(grace.exempt(ringing = true, nowElapsed = t + 29 * second))
        assertFalse(grace.exempt(ringing = true, nowElapsed = t + 30 * second))
    }
}
