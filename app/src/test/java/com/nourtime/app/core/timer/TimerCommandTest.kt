package com.nourtime.app.core.timer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Remote commands from the parent's phone (Phase 2): bonus time, lock now, end lock. */
class TimerCommandTest {

    private val min = 60_000L
    private val hour = 60 * min

    private fun available(remaining: Long) =
        TimerState.fresh(budgetMs = 60 * min, lockMs = 6 * hour, nowElapsed = 0, bootCount = 1).copy(remainingMs = remaining)

    private fun locked(lockLeft: Long) =
        TimerState.fresh(budgetMs = 60 * min, lockMs = 6 * hour, nowElapsed = 0, bootCount = 1)
            .copy(phase = TimerPhase.LOCKED, remainingMs = 0, lockRemainingMs = lockLeft)

    @Test
    fun `a later budget change keeps extra time above the budget`() {
        // Deferred review item: 20 min left + a 60 min bonus, then the parent trims the budget by 15 min.
        val withBonus = TimeRules.apply(available(remaining = 20 * min), TimerCommand.Bonus(60))
        assertEquals(65 * min, TimeRules.applySettings(withBonus, budgetMs = 45 * min, lockMs = 6 * hour).remainingMs)
        assertEquals(110 * min, TimeRules.applySettings(withBonus, budgetMs = 90 * min, lockMs = 6 * hour).remainingMs)
    }

    @Test
    fun `bonus while available adds to what is left`() {
        assertEquals(40 * min, TimeRules.apply(available(remaining = 10 * min), TimerCommand.Bonus(30)).remainingMs)
    }

    @Test
    fun `bonus during a lock ends it and gives exactly the bonus`() {
        val s = TimeRules.apply(locked(lockLeft = 3 * hour), TimerCommand.Bonus(15))
        assertEquals(TimerPhase.AVAILABLE, s.phase)
        assertEquals(15 * min, s.remainingMs)
        assertEquals(0L, s.lockRemainingMs)
    }

    @Test
    fun `when the bonus is used up a full new lock period starts`() {
        val s = TimeRules.advance(TimeRules.apply(locked(lockLeft = hour), TimerCommand.Bonus(15)), 15 * min, 1, wasInUse = true)
        assertEquals(TimerPhase.LOCKED, s.phase)
        assertEquals(s.lockMs, s.lockRemainingMs)
    }

    @Test
    fun `lock now starts a full lock`() {
        val s = TimeRules.apply(available(remaining = 20 * min), TimerCommand.LockNow)
        assertEquals(TimerPhase.LOCKED, s.phase)
        assertEquals(s.lockMs, s.lockRemainingMs)
        assertEquals(0L, s.remainingMs)
    }

    @Test
    fun `lock now while locked changes nothing`() {
        val l = locked(lockLeft = hour)
        assertEquals(l, TimeRules.apply(l, TimerCommand.LockNow))
    }

    @Test
    fun `end lock refills the budget`() {
        val s = TimeRules.apply(locked(lockLeft = hour), TimerCommand.EndLock)
        assertEquals(TimerPhase.AVAILABLE, s.phase)
        assertEquals(s.budgetMs, s.remainingMs)
        assertEquals(0L, s.lockRemainingMs)
    }

    @Test
    fun `end lock while available changes nothing`() {
        val a = available(remaining = min)
        assertEquals(a, TimeRules.apply(a, TimerCommand.EndLock))
    }

    @Test
    fun `bonus is capped at 24 hours left`() {
        assertEquals(24 * hour, TimeRules.apply(available(remaining = 23 * hour + 50 * min), TimerCommand.Bonus(240)).remainingMs)
    }
}
