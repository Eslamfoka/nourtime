package com.nourtime.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinResetWaitTest {

    private val hour = 60 * 60_000L

    @Test
    fun `the wait counts elapsed time and is over after a day`() {
        val start = PinResetWait.start(nowElapsed = 1_000, bootCount = 1)
        val later = PinResetWait.advance(start, 1_000 + 23 * hour, 1)
        assertEquals(hour, later.remainingMs)
        assertFalse(PinResetWait.isOver(later))
        // Counting rarely gives the same result as counting often.
        assertTrue(PinResetWait.isOver(PinResetWait.advance(start, 1_000 + 24 * hour, 1)))
        assertTrue(PinResetWait.isOver(PinResetWait.advance(later, 1_000 + 24 * hour, 1)))
    }

    @Test
    fun `a reboot keeps what is left and counts nothing for the time off`() {
        val counted = PinResetWait.advance(PinResetWait.start(1_000, 1), 1_000 + 10 * hour, 1)
        val rebooted = PinResetWait.advance(counted, nowElapsed = 50 * hour, bootCount = 2)
        assertEquals(14 * hour, rebooted.remainingMs)
        assertEquals(2, rebooted.bootCount)
        // From there it counts again.
        assertEquals(10 * hour, PinResetWait.advance(rebooted, 54 * hour, 2).remainingMs)
    }

    @Test
    fun `without a boot count, elapsed time going back counts as a reboot`() {
        val counted = PinResetWait.advance(PinResetWait.start(5 * hour, 0), 9 * hour, 0)
        val rebooted = PinResetWait.advance(counted, 1_000, 0)
        assertEquals(20 * hour, rebooted.remainingMs)
    }

    @Test
    fun `the wait never goes below zero`() {
        val over = PinResetWait.advance(PinResetWait.start(0, 1), 100 * hour, 1)
        assertEquals(0L, over.remainingMs)
    }
}
