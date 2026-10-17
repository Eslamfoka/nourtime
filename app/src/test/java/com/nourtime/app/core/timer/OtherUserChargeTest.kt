package com.nourtime.app.core.timer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Time in a guest or second user, where Nour Time can't limit apps (2026-10-17 audit, H2). */
class OtherUserChargeTest {

    private val min = 60_000L
    private val hour = 60 * min
    private val start = TimerState.fresh(budgetMs = 60 * min, lockMs = 6 * hour, nowElapsed = 0, bootCount = 1)

    @Test
    fun `time away comes off the time left`() {
        val s = TimeRules.chargeOtherUser(start, 20 * min)
        assertEquals(TimerPhase.AVAILABLE, s.phase)
        assertEquals(40 * min, s.remainingMs)
    }

    @Test
    fun `more time away than is left starts a full lock`() {
        val s = TimeRules.chargeOtherUser(start, 2 * hour)
        assertEquals(TimerPhase.LOCKED, s.phase)
        assertEquals(6 * hour, s.lockRemainingMs)
        assertEquals(0L, s.remainingMs)
    }

    @Test
    fun `the parent's extra time is used up last, like any use`() {
        val withBonus = TimeRules.apply(start, TimerCommand.Bonus(15)) // 75 min left, 15 of them extra
        val s = TimeRules.chargeOtherUser(withBonus, 65 * min)
        assertEquals(10 * min, s.remainingMs)
        assertEquals(10 * min, s.bonusMs)
    }

    @Test
    fun `a lock period doesn't run down while away`() {
        val locked = TimeRules.apply(start, TimerCommand.LockNow)
        // The engine's ticks counted the lock down by an hour while the phone was in the guest user.
        val ranDown = TimeRules.advance(locked, hour, 1, wasInUse = false)
        assertEquals(5 * hour, ranDown.lockRemainingMs)
        val s = TimeRules.chargeOtherUser(ranDown, hour, lockLeftAtStartMs = 6 * hour)
        assertEquals(TimerPhase.LOCKED, s.phase)
        assertEquals(6 * hour, s.lockRemainingMs)
    }

    @Test
    fun `a lock that ran out while away starts again with what was left of it`() {
        // 10 minutes of lock left, then an hour in the guest user: the lock ended and the budget refilled.
        val locked = TimeRules.apply(start, TimerCommand.LockNow).copy(lockRemainingMs = 10 * min)
        val refilled = TimeRules.advance(locked, hour, 1, wasInUse = false)
        assertEquals(TimerPhase.AVAILABLE, refilled.phase)
        val s = TimeRules.chargeOtherUser(refilled, hour, lockLeftAtStartMs = 10 * min)
        assertEquals(TimerPhase.LOCKED, s.phase)
        assertEquals(10 * min, s.lockRemainingMs)
    }

    @Test
    fun `a lock started meanwhile stays as it is`() {
        val locked = TimeRules.apply(start, TimerCommand.LockNow)
        assertEquals(6 * hour, TimeRules.chargeOtherUser(locked, 3 * hour, lockLeftAtStartMs = 0).lockRemainingMs)
    }

    @Test
    fun `no time away changes nothing`() {
        assertEquals(start, TimeRules.chargeOtherUser(start, 0))
    }
}
