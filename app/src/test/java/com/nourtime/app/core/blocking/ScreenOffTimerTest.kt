package com.nourtime.app.core.blocking

import com.nourtime.app.data.settings.LockType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOffTimerTest {

    private val timer = ScreenOffTimer(delayMs = 8_000)

    private fun tick(
        now: Long,
        lockPeriod: Boolean = true,
        type: LockType = LockType.WHOLE_DEVICE,
        blocking: Boolean = true,
        screenOn: Boolean = true,
    ) = timer.update(now, lockPeriod, type, blocking, screenOn)

    @Test
    fun `turns the screen off once, a few seconds after the lock period starts`() {
        assertFalse(tick(0, lockPeriod = false, blocking = false))
        assertFalse(tick(1_000)) // lock period starts
        assertFalse(tick(8_000))
        assertTrue(tick(9_000))
        assertFalse(tick(10_000))
        assertFalse(tick(60_000))
    }

    @Test
    fun `selected-apps mode never turns the screen off`() {
        tick(0, lockPeriod = false, type = LockType.SELECTED_APPS, blocking = false)
        tick(1_000, type = LockType.SELECTED_APPS, blocking = false)
        assertFalse(tick(20_000, type = LockType.SELECTED_APPS, blocking = false))
    }

    @Test
    fun `not when protection starts in the middle of a lock period`() {
        assertFalse(tick(0))
        assertFalse(tick(20_000))
    }

    @Test
    fun `skipped when the parent unlocked the phone before the delay`() {
        tick(0, lockPeriod = false, blocking = false)
        tick(1_000)
        assertFalse(tick(5_000, blocking = false)) // device pass
        assertFalse(tick(9_000, blocking = false))
        assertFalse(tick(20_000))
    }

    @Test
    fun `a moment with nothing covered at the start doesn't cancel it`() {
        tick(0, lockPeriod = false, blocking = false)
        assertFalse(tick(1_000, blocking = false)) // Nour Time still open as the period starts
        assertFalse(tick(2_000)) // child is on the launcher, covered
        assertTrue(tick(9_000))
    }

    @Test
    fun `cancelled when the screen is already off`() {
        tick(0, lockPeriod = false, blocking = false)
        tick(1_000)
        assertFalse(tick(5_000, screenOn = false))
        assertFalse(tick(20_000))
    }

    @Test
    fun `fires again for the next lock period`() {
        tick(0, lockPeriod = false, blocking = false)
        tick(1_000)
        assertTrue(tick(9_000))
        tick(100_000, lockPeriod = false, blocking = false) // refilled
        tick(200_000) // next lock period, e.g. bedtime
        assertTrue(tick(208_000))
    }
}
