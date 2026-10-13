package com.nourtime.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

/** The security answer resets the PIN, so a child gets very few guesses: 3, then one a day. */
class AnswerAttemptPolicyTest {

    private val day = 24 * 60 * 60_000L

    private fun failTimes(n: Int, now: Long = 1_000, boot: Int = 1): PinAttemptState =
        (1..n).fold(PinAttemptState()) { s, _ -> AnswerAttemptPolicy.onFailure(s, now, boot) }

    @Test
    fun `three wrong answers are free`() {
        assertEquals(0L, AnswerAttemptPolicy.remainingLockout(failTimes(3), 1_000))
    }

    @Test
    fun `every wrong answer after the third locks answers for a day`() {
        assertEquals(day, AnswerAttemptPolicy.lockoutFor(4))
        assertEquals(day, AnswerAttemptPolicy.lockoutFor(5))
        assertEquals(day, AnswerAttemptPolicy.lockoutFor(50))
        assertEquals(day, AnswerAttemptPolicy.remainingLockout(failTimes(4), 1_000))
    }

    @Test
    fun `a reboot restarts the day in full`() {
        val locked = failTimes(4, now = 1_000, boot = 1)
        val afterReboot = AnswerAttemptPolicy.rebase(locked, nowElapsed = 500, bootCount = 2)
        assertEquals(day, AnswerAttemptPolicy.remainingLockout(afterReboot, 500))
    }

    @Test
    fun `the PIN keeps its own, gentler schedule`() {
        assertEquals(0L, PinAttemptPolicy.lockoutFor(4))
        assertEquals(30_000L, PinAttemptPolicy.lockoutFor(5))
    }
}
