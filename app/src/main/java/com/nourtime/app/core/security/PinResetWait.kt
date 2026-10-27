package com.nourtime.app.core.security

/**
 * How much of the "Forgot PIN?" wait is left: [remainingMs], last counted at elapsedRealtime
 * [lastElapsed] of boot [bootCount].
 */
data class PinResetWaitState(val remainingMs: Long, val lastElapsed: Long, val bootCount: Int)

/**
 * The wait before a PIN set through the security answer works. It counts elapsedRealtime, not the
 * wall clock: a child who guessed the answer could otherwise reboot, move the date forward and have
 * their PIN at once. Like the timer ([com.nourtime.app.core.timer.TimeRules.advance]), the time across
 * a reboot isn't counted (it can't be measured), so switching the phone off never shortens the wait.
 */
object PinResetWait {
    const val WAIT_MS = 24 * 60 * 60_000L

    fun start(nowElapsed: Long, bootCount: Int) = PinResetWaitState(WAIT_MS, nowElapsed, bootCount)

    /**
     * Counts the real time since the last count. A reboot (a new boot count, or elapsed time going back
     * when the boot count can't be read) counts nothing; elapsed time restarts from there.
     */
    fun advance(state: PinResetWaitState, nowElapsed: Long, bootCount: Int): PinResetWaitState {
        val dt = if (bootCount != state.bootCount || nowElapsed < state.lastElapsed) 0 else nowElapsed - state.lastElapsed
        return PinResetWaitState(
            remainingMs = (state.remainingMs - dt).coerceIn(0, WAIT_MS),
            lastElapsed = nowElapsed,
            bootCount = bootCount,
        )
    }

    fun isOver(state: PinResetWaitState) = state.remainingMs == 0L
}
