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
            TimerPhase.AVAILABLE -> {
                if (!wasInUse || dt == 0L) return moved
                val used = minOf(dt, moved.remainingMs)
                val left = moved.copy(remainingMs = moved.remainingMs - used).let { it.copy(bonusMs = minOf(it.bonusMs, it.remainingMs)) }
                if (left.remainingMs > 0) left else progressLock(startLock(left), dt - used)
            }
            TimerPhase.LOCKED -> progressLock(moved, dt)
        }
    }

    /** Applies a changed budget or lock length from the parent settings. */
    fun applySettings(state: TimerState, budgetMs: Long, lockMs: Long): TimerState {
        var s = state
        if (budgetMs != s.budgetMs) {
            s = if (s.phase == TimerPhase.AVAILABLE) {
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
        TimerCommand.LockNow -> if (state.phase == TimerPhase.AVAILABLE) startLock(state) else state
        TimerCommand.EndLock -> if (state.phase == TimerPhase.LOCKED) refill(state) else state
    }

    private fun startLock(state: TimerState) =
        state.copy(phase = TimerPhase.LOCKED, remainingMs = 0, lockRemainingMs = state.lockMs, bonusMs = 0)

    private fun progressLock(state: TimerState, dt: Long): TimerState =
        if (dt >= state.lockRemainingMs) refill(state) else state.copy(lockRemainingMs = state.lockRemainingMs - dt)

    private fun refill(state: TimerState) =
        state.copy(phase = TimerPhase.AVAILABLE, remainingMs = state.budgetMs, lockRemainingMs = 0, bonusMs = 0)
}
