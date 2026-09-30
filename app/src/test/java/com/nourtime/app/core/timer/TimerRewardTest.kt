package com.nourtime.app.core.timer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Minutes the child earned in the Learning Hub: a break inside the lock, not a fresh lock afterwards. */
class TimerRewardTest {

    private val min = 60_000L
    private val hour = 60 * min

    private fun available(remaining: Long) =
        TimerState.fresh(budgetMs = 60 * min, lockMs = 6 * hour, nowElapsed = 0, bootCount = 1).copy(remainingMs = remaining)

    private fun locked(lockLeft: Long) =
        TimerState.fresh(budgetMs = 60 * min, lockMs = 6 * hour, nowElapsed = 0, bootCount = 1)
            .copy(phase = TimerPhase.LOCKED, remainingMs = 0, lockRemainingMs = lockLeft)

    private fun TimerState.after(ms: Long, inUse: Boolean) = TimeRules.advance(this, lastElapsed + ms, bootCount, inUse)

    @Test
    fun `a reward during a lock opens the apps for exactly the reward`() {
        val s = TimeRules.reward(locked(lockLeft = 3 * hour), 5)
        assertEquals(TimerPhase.AVAILABLE, s.phase)
        assertEquals(5 * min, s.remainingMs)
        assertEquals(3 * hour, s.lockPendingMs)
    }

    @Test
    fun `when the reward is used up the lock continues with what is left`() {
        val s = TimeRules.reward(locked(lockLeft = 3 * hour), 5).after(5 * min, inUse = true)
        assertEquals(TimerPhase.LOCKED, s.phase)
        assertEquals(3 * hour - 5 * min, s.lockRemainingMs)
        assertEquals(0L, s.lockPendingMs)
    }

    @Test
    fun `the lock clock keeps running while the reward is not used`() {
        val s = TimeRules.reward(locked(lockLeft = 3 * hour), 5)
            .after(hour, inUse = false)
            .after(5 * min, inUse = true)
        assertEquals(TimerPhase.LOCKED, s.phase)
        assertEquals(2 * hour - 5 * min, s.lockRemainingMs)
    }

    @Test
    fun `using more than the reward in one step locks for the rest`() {
        val s = TimeRules.reward(locked(lockLeft = 3 * hour), 5).after(15 * min, inUse = true)
        assertEquals(TimerPhase.LOCKED, s.phase)
        assertEquals(3 * hour - 15 * min, s.lockRemainingMs)
    }

    @Test
    fun `if the lock would have ended during the break the budget refills`() {
        val s = TimeRules.reward(locked(lockLeft = 2 * min), 10).after(3 * min, inUse = true)
        assertEquals(TimerPhase.AVAILABLE, s.phase)
        assertEquals(0L, s.lockPendingMs)
        // Two minutes of the break, then one minute of the refilled budget.
        assertEquals(59 * min, s.remainingMs)
    }

    @Test
    fun `lock ending while the reward is unused refills`() {
        val s = TimeRules.reward(locked(lockLeft = 30 * min), 10).after(31 * min, inUse = false)
        assertEquals(TimerPhase.AVAILABLE, s.phase)
        assertEquals(60 * min, s.remainingMs)
        assertEquals(0L, s.lockPendingMs)
    }

    @Test
    fun `a reward while available adds to what is left`() {
        val s = TimeRules.reward(available(remaining = 10 * min), 5)
        assertEquals(15 * min, s.remainingMs)
        assertEquals(0L, s.lockPendingMs)
    }

    @Test
    fun `lock now during a break starts a full lock`() {
        val s = TimeRules.apply(TimeRules.reward(locked(lockLeft = hour), 5), TimerCommand.LockNow)
        assertEquals(TimerPhase.LOCKED, s.phase)
        assertEquals(6 * hour, s.lockRemainingMs)
        assertEquals(0L, s.lockPendingMs)
    }

    @Test
    fun `end lock during a break refills`() {
        val s = TimeRules.apply(TimeRules.reward(locked(lockLeft = hour), 5), TimerCommand.EndLock)
        assertEquals(TimerPhase.AVAILABLE, s.phase)
        assertEquals(60 * min, s.remainingMs)
        assertEquals(0L, s.lockPendingMs)
    }

    @Test
    fun `a budget change during a break doesn't change the earned minutes`() {
        val s = TimeRules.applySettings(TimeRules.reward(locked(lockLeft = hour), 5), budgetMs = 120 * min, lockMs = 6 * hour)
        assertEquals(5 * min, s.remainingMs)
        assertEquals(hour, s.lockPendingMs)
    }

    @Test
    fun `a shorter lock during a break shortens what is left of it`() {
        val s = TimeRules.applySettings(TimeRules.reward(locked(lockLeft = 3 * hour), 5), budgetMs = 60 * min, lockMs = 4 * hour)
        assertEquals(hour, s.lockPendingMs)
        val over = TimeRules.applySettings(TimeRules.reward(locked(lockLeft = hour), 5), budgetMs = 60 * min, lockMs = 2 * hour)
        assertEquals(TimerPhase.AVAILABLE, over.phase)
        assertEquals(60 * min, over.remainingMs)
        assertEquals(0L, over.lockPendingMs)
    }

    @Test
    fun `reward and lock ending at the same moment refill instead of locking again`() {
        val s = TimeRules.reward(locked(lockLeft = 5 * min), 5).after(5 * min, inUse = true)
        assertEquals(TimerPhase.AVAILABLE, s.phase)
        assertEquals(60 * min, s.remainingMs)
    }
}
