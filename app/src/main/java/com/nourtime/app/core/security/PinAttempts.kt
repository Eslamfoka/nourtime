package com.nourtime.app.core.security

/**
 * Persisted wrong-PIN state. The lockout end is in elapsedRealtime of boot [bootCount];
 * [lockoutDurationMs] lets a lockout be re-applied after a reboot resets elapsedRealtime.
 */
data class PinAttemptState(
    val failures: Int = 0,
    val lockoutEndElapsed: Long = 0,
    val lockoutDurationMs: Long = 0,
    val bootCount: Int = 0,
)

/**
 * Escalating delay after repeated wrong PINs. One count for every pad (the lock screen's and Nour
 * Time's own), so the child gets these free tries once, not once per pad.
 */
object PinAttemptPolicy : AttemptPolicy(
    freeAttempts = 4,
    delaysMs = longArrayOf(
        30_000L,        // 5th wrong PIN
        60_000L,        // 6th
        5 * 60_000L,    // 7th
        15 * 60_000L,   // 8th
        30 * 60_000L,   // 9th
        60 * 60_000L,   // 10th and after
    ),
)

/**
 * The security answer sets a new PIN ("Forgot PIN?"), and the child may know the family well enough
 * to guess it: three wrong answers, then a whole day for each further one (2026-10-13 audit, H1).
 */
object AnswerAttemptPolicy : AttemptPolicy(
    freeAttempts = 3,
    delaysMs = longArrayOf(24 * 60 * 60_000L),
)

/** A number of free wrong attempts, then a delay per further one (the last delay repeats). */
open class AttemptPolicy(private val freeAttempts: Int, private val delaysMs: LongArray) {

    fun lockoutFor(failures: Int): Long =
        if (failures <= freeAttempts) 0 else delaysMs[minOf(failures - freeAttempts - 1, delaysMs.lastIndex)]

    fun attemptsBeforeLockout(failures: Int): Int = maxOf(freeAttempts - failures, 0)

    /**
     * After a reboot the stored elapsed deadline is meaningless, so an active lockout restarts in full.
     * Rebooting therefore never shortens a lockout.
     */
    fun rebase(state: PinAttemptState, nowElapsed: Long, bootCount: Int): PinAttemptState =
        if (state.lockoutDurationMs > 0 && state.bootCount != bootCount) {
            state.copy(lockoutEndElapsed = nowElapsed + state.lockoutDurationMs, bootCount = bootCount)
        } else {
            state
        }

    fun remainingLockout(state: PinAttemptState, nowElapsed: Long): Long =
        if (state.lockoutDurationMs == 0L) 0 else maxOf(state.lockoutEndElapsed - nowElapsed, 0)

    /** Forgets an expired lockout (keeping the failure count) so a later reboot can't revive it. */
    fun clearExpired(state: PinAttemptState, nowElapsed: Long): PinAttemptState =
        if (state.lockoutDurationMs > 0 && remainingLockout(state, nowElapsed) == 0L) {
            state.copy(lockoutEndElapsed = 0, lockoutDurationMs = 0)
        } else {
            state
        }

    fun onFailure(state: PinAttemptState, nowElapsed: Long, bootCount: Int): PinAttemptState {
        val failures = state.failures + 1
        val lockout = lockoutFor(failures)
        return PinAttemptState(
            failures = failures,
            lockoutEndElapsed = if (lockout > 0) nowElapsed + lockout else 0,
            lockoutDurationMs = lockout,
            bootCount = bootCount,
        )
    }

    fun onSuccess(): PinAttemptState = PinAttemptState()

    /**
     * Two counts folded into one, keeping the stricter of each part: the higher failure count and the
     * longer lockout still running at [nowElapsed]. Both must already be [rebase]d to this boot.
     */
    fun stricter(a: PinAttemptState, b: PinAttemptState, nowElapsed: Long): PinAttemptState {
        val longer = if (remainingLockout(b, nowElapsed) > remainingLockout(a, nowElapsed)) b else a
        return longer.copy(failures = maxOf(a.failures, b.failures))
    }
}
