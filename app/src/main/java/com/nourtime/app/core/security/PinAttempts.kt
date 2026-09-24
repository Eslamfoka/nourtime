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

/** Escalating delay after repeated wrong PINs. */
object PinAttemptPolicy {
    const val FREE_ATTEMPTS = 4

    private val delaysMs = longArrayOf(
        30_000L,        // 5th wrong PIN
        60_000L,        // 6th
        5 * 60_000L,    // 7th
        15 * 60_000L,   // 8th
        30 * 60_000L,   // 9th
        60 * 60_000L,   // 10th and after
    )

    fun lockoutFor(failures: Int): Long =
        if (failures <= FREE_ATTEMPTS) 0 else delaysMs[minOf(failures - FREE_ATTEMPTS - 1, delaysMs.lastIndex)]

    fun attemptsBeforeLockout(failures: Int): Int = maxOf(FREE_ATTEMPTS - failures, 0)

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
}
