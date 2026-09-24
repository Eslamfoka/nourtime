package com.nourtime.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinAttemptPolicyTest {

    private fun failTimes(n: Int, now: Long = 1_000, boot: Int = 1): PinAttemptState =
        (1..n).fold(PinAttemptState()) { s, _ -> PinAttemptPolicy.onFailure(s, now, boot) }

    @Test
    fun `first four wrong pins have no delay`() {
        val state = failTimes(4)
        assertEquals(4, state.failures)
        assertEquals(0, PinAttemptPolicy.remainingLockout(state, 1_000))
        assertEquals(0, PinAttemptPolicy.attemptsBeforeLockout(4))
        assertEquals(3, PinAttemptPolicy.attemptsBeforeLockout(1))
    }

    @Test
    fun `delay escalates from the fifth wrong pin and caps at one hour`() {
        assertEquals(30_000L, PinAttemptPolicy.lockoutFor(5))
        assertEquals(60_000L, PinAttemptPolicy.lockoutFor(6))
        assertEquals(5 * 60_000L, PinAttemptPolicy.lockoutFor(7))
        assertEquals(60 * 60_000L, PinAttemptPolicy.lockoutFor(10))
        assertEquals(60 * 60_000L, PinAttemptPolicy.lockoutFor(50))
    }

    @Test
    fun `lockout counts down with elapsed time`() {
        val state = failTimes(5, now = 1_000)
        assertEquals(30_000L, PinAttemptPolicy.remainingLockout(state, 1_000))
        assertEquals(10_000L, PinAttemptPolicy.remainingLockout(state, 21_000))
        assertEquals(0L, PinAttemptPolicy.remainingLockout(state, 40_000))
    }

    @Test
    fun `reboot restarts an active lockout instead of clearing it`() {
        val state = failTimes(5, now = 500_000, boot = 1)
        // After reboot elapsedRealtime starts near zero again.
        val rebased = PinAttemptPolicy.rebase(state, nowElapsed = 2_000, bootCount = 2)
        assertEquals(30_000L, PinAttemptPolicy.remainingLockout(rebased, 2_000))
        assertEquals(2, rebased.bootCount)
    }

    @Test
    fun `rebase is a no-op on the same boot`() {
        val state = failTimes(5, now = 1_000, boot = 1)
        assertEquals(state, PinAttemptPolicy.rebase(state, 20_000, 1))
    }

    @Test
    fun `expired lockout is cleared but failures are kept`() {
        val state = failTimes(5, now = 1_000)
        val cleared = PinAttemptPolicy.clearExpired(state, 100_000)
        assertEquals(5, cleared.failures)
        assertEquals(0L, cleared.lockoutDurationMs)
        // A reboot afterwards doesn't bring the lockout back.
        val rebooted = PinAttemptPolicy.rebase(cleared, 1_000, 2)
        assertEquals(0L, PinAttemptPolicy.remainingLockout(rebooted, 1_000))
    }

    @Test
    fun `failure after an expired lockout escalates`() {
        val cleared = PinAttemptPolicy.clearExpired(failTimes(5, now = 1_000), 100_000)
        val next = PinAttemptPolicy.onFailure(cleared, 100_000, 1)
        assertEquals(60_000L, PinAttemptPolicy.remainingLockout(next, 100_000))
    }

    @Test
    fun `success resets everything`() {
        assertEquals(PinAttemptState(), PinAttemptPolicy.onSuccess())
    }
}
