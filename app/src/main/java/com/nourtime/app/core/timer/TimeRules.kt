package com.nourtime.app.core.timer

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

enum class TimerPhase { AVAILABLE, LOCKED }

/**
 * Persisted timer state (brief §2). All durations are measured with elapsedRealtime of boot
 * [bootCount], so changing the device clock can't add or remove time.
 */
data class TimerState(
    val phase: TimerPhase,
    /** Budget left while AVAILABLE. */
    val remainingMs: Long,
    /** Lock time left while LOCKED. */
    val lockRemainingMs: Long,
    /** Budget and lock length this state was computed with, to apply parent changes. */
    val budgetMs: Long,
    val lockMs: Long,
    val lastElapsed: Long,
    val bootCount: Int,
    /** Date of the last daily reset moment already handled, or null when daily reset is off. */
    val lastResetDay: LocalDate? = null,
    /** Real time elapsed since the last daily reset, so a clock change can't trigger an early one. */
    val sinceResetMs: Long = 0,
    /**
     * The part of [remainingMs] the parent gave as extra time and that isn't used yet. It counts as
     * the last part of the time left, and a lower budget never takes it away.
     */
    val bonusMs: Long = 0,
    /**
     * While AVAILABLE on a break the child earned during a lock ([TimeRules.reward]): the lock
     * time still left. It keeps counting down; when the break's minutes run out the lock resumes
     * with it, and when it reaches zero first the budget refills as usual. 0 = no break.
     */
    val lockPendingMs: Long = 0,
) {
    companion object {
        fun fresh(budgetMs: Long, lockMs: Long, nowElapsed: Long, bootCount: Int) = TimerState(
            phase = TimerPhase.AVAILABLE,
            remainingMs = budgetMs,
            lockRemainingMs = 0,
            budgetMs = budgetMs,
            lockMs = lockMs,
            lastElapsed = nowElapsed,
            bootCount = bootCount,
        )
    }
}

object TimeRules {
    /** A daily reset needs this much real time since the previous one. */
    const val MIN_DAILY_RESET_GAP_MS = 20 * 60 * 60_000L

    /**
     * Moves the state forward to [nowElapsed]. [wasInUse] says whether a limited app was in use
     * during the whole interval since the last update, so callers must advance on every change.
     * After a reboot elapsed time restarts, so the gap is not counted: powering off never shortens
     * a lock, and never uses up budget.
     */
    fun advance(state: TimerState, nowElapsed: Long, bootCount: Int, wasInUse: Boolean): TimerState {
        val dt = if (bootCount != state.bootCount || nowElapsed < state.lastElapsed) 0 else nowElapsed - state.lastElapsed
        val moved = state.copy(lastElapsed = nowElapsed, bootCount = bootCount, sinceResetMs = state.sinceResetMs + dt)
        return when (moved.phase) {
            TimerPhase.AVAILABLE -> advanceAvailable(moved, dt, wasInUse)
            TimerPhase.LOCKED -> progressLock(moved, dt)
        }
    }

    private fun advanceAvailable(state: TimerState, dt: Long, inUse: Boolean): TimerState {
        if (dt == 0L) return state
        val pending = state.lockPendingMs
        // On a break, only the part of the interval before the paused lock would end counts here.
        val span = if (pending > 0) minOf(dt, pending) else dt
        val used = if (inUse) minOf(span, state.remainingMs) else 0
        val left = state.copy(remainingMs = state.remainingMs - used).let { it.copy(bonusMs = minOf(it.bonusMs, it.remainingMs)) }
        // The break and the paused lock end together: the lock is over, so refill.
        if (pending > 0 && used == pending) return advanceAvailable(refill(left), dt - span, inUse)
        if (inUse && left.remainingMs == 0L) {
            val resumed = if (pending > 0) left.copy(lockPendingMs = pending - used) else left
            return progressLock(startLock(resumed), dt - used)
        }
        if (pending == 0L) return left
        return if (span == pending) advanceAvailable(refill(left), dt - span, inUse) else left.copy(lockPendingMs = pending - span)
    }

    /** Applies a changed budget or lock length from the parent settings. */
    fun applySettings(state: TimerState, budgetMs: Long, lockMs: Long): TimerState {
        var s = state
        if (budgetMs != s.budgetMs) {
            s = if (s.phase == TimerPhase.AVAILABLE && s.lockPendingMs == 0L) {
                // Moves the time left by the change, but never below the unused extra time the parent gave.
                val moved = (s.remainingMs + budgetMs - s.budgetMs).coerceIn(0, MAX_REMAINING_MS)
                s.copy(remainingMs = maxOf(moved, s.bonusMs), budgetMs = budgetMs)
            } else {
                s.copy(budgetMs = budgetMs)
            }
        }
        if (lockMs != s.lockMs) {
            s = if (s.phase == TimerPhase.LOCKED) {
                progressLock(s.copy(lockRemainingMs = (s.lockRemainingMs + lockMs - s.lockMs).coerceIn(0, lockMs), lockMs = lockMs), 0)
            } else if (s.lockPendingMs > 0) {
                val pending = (s.lockPendingMs + lockMs - s.lockMs).coerceIn(0, lockMs)
                if (pending == 0L) refill(s.copy(lockMs = lockMs)) else s.copy(lockPendingMs = pending, lockMs = lockMs)
            } else {
                s.copy(lockMs = lockMs)
            }
        }
        if (s.phase == TimerPhase.AVAILABLE && s.remainingMs == 0L) s = startLock(s)
        return s
    }

    /**
     * Optional daily refill at [resetAt] (wall clock). Fires at most once per calendar day and only
     * after [MIN_DAILY_RESET_GAP_MS] of real time, so moving the clock forward doesn't help.
     */
    fun applyDailyReset(state: TimerState, today: LocalDate, now: LocalTime, resetAt: LocalTime?): TimerState {
        if (resetAt == null) return if (state.lastResetDay == null) state else state.copy(lastResetDay = null)
        val dueDay = if (now >= resetAt) today else today.minusDays(1)
        val last = state.lastResetDay
            // Just switched on by the parent: count from the most recent reset moment, without refilling.
            ?: return state.copy(
                lastResetDay = dueDay,
                sinceResetMs = Duration.between(dueDay.atTime(resetAt), today.atTime(now)).toMillis(),
            )
        if (dueDay <= last || state.sinceResetMs < MIN_DAILY_RESET_GAP_MS) return state
        return refill(state).copy(lastResetDay = dueDay, sinceResetMs = 0)
    }

    /** Longest remaining budget a bonus can create, so repeated bonuses can't grow without bound. */
    const val MAX_REMAINING_MS = 24 * 60 * 60_000L

    /** Applies a command from the parent's phone. Commands that don't fit the current phase change nothing. */
    fun apply(state: TimerState, command: TimerCommand): TimerState = when (command) {
        is TimerCommand.Bonus -> {
            val bonusMs = command.minutes * 60_000L
            if (state.phase == TimerPhase.LOCKED) {
                val remaining = bonusMs.coerceAtMost(MAX_REMAINING_MS)
                state.copy(phase = TimerPhase.AVAILABLE, remainingMs = remaining, lockRemainingMs = 0, bonusMs = remaining)
            } else {
                val remaining = (state.remainingMs + bonusMs).coerceAtMost(MAX_REMAINING_MS)
                state.copy(remainingMs = remaining, bonusMs = minOf(state.bonusMs + bonusMs, remaining))
            }
        }
        TimerCommand.LockNow -> if (state.phase == TimerPhase.AVAILABLE) startLock(state.copy(lockPendingMs = 0)) else state
        TimerCommand.EndLock -> if (state.phase == TimerPhase.LOCKED || state.lockPendingMs > 0) refill(state) else state
    }

    /**
     * Minutes the child earned in the Learning Hub. During a lock period they open the apps for these
     * minutes while the lock keeps counting down (a break, see [TimerState.lockPendingMs]); unlike a
     * parent's bonus, no fresh lock period follows. Outside a lock they add to the time left.
     */
    fun reward(state: TimerState, minutes: Int): TimerState {
        if (state.phase != TimerPhase.LOCKED) return apply(state, TimerCommand.Bonus(minutes))
        val remaining = (minutes * 60_000L).coerceAtMost(MAX_REMAINING_MS)
        return state.copy(phase = TimerPhase.AVAILABLE, remainingMs = remaining, lockRemainingMs = 0, bonusMs = remaining, lockPendingMs = state.lockRemainingMs)
    }

    /** Starts a lock period, or resumes the one a break paused. */
    private fun startLock(state: TimerState) = state.copy(
        phase = TimerPhase.LOCKED,
        remainingMs = 0,
        lockRemainingMs = if (state.lockPendingMs > 0) state.lockPendingMs else state.lockMs,
        bonusMs = 0,
        lockPendingMs = 0,
    )

    private fun progressLock(state: TimerState, dt: Long): TimerState =
        if (dt >= state.lockRemainingMs) refill(state) else state.copy(lockRemainingMs = state.lockRemainingMs - dt)

    private fun refill(state: TimerState) =
        state.copy(phase = TimerPhase.AVAILABLE, remainingMs = state.budgetMs, lockRemainingMs = 0, bonusMs = 0, lockPendingMs = 0)
}
