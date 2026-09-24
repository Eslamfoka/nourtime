package com.nourtime.app.core.timer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimeRulesTest {

    private val min = 60_000L
    private val hour = 60 * min
    private val start = TimerState.fresh(budgetMs = 60 * min, lockMs = 6 * hour, nowElapsed = 0, bootCount = 1)

    private fun TimerState.run(ms: Long, inUse: Boolean = true, boot: Int = bootCount) =
        TimeRules.advance(this, lastElapsed + ms, boot, inUse)

    @Test
    fun `only active use consumes the budget`() {
        val s = start.run(10 * min, inUse = true).run(30 * min, inUse = false)
        assertEquals(50 * min, s.remainingMs)
        assertEquals(TimerPhase.AVAILABLE, s.phase)
    }

    @Test
    fun `switching between limited apps keeps one shared countdown`() {
        // The engine only sees "a limited app is in use"; consecutive intervals just add up.
        val s = start.run(20 * min).run(15 * min).run(5 * min)
        assertEquals(20 * min, s.remainingMs)
    }

    @Test
    fun `budget hitting zero starts the lock immediately`() {
        val s = start.run(60 * min)
        assertEquals(TimerPhase.LOCKED, s.phase)
        assertEquals(6 * hour, s.lockRemainingMs)
    }

    @Test
    fun `overshoot past zero already counts toward the lock`() {
        val s = start.run(65 * min)
        assertEquals(6 * hour - 5 * min, s.lockRemainingMs)
    }

    @Test
    fun `lock counts down whether or not apps are used, then refills fully`() {
        val locked = start.run(60 * min)
        assertEquals(hour, locked.run(5 * hour, inUse = false).lockRemainingMs)
        val refilled = locked.run(6 * hour, inUse = false)
        assertEquals(TimerPhase.AVAILABLE, refilled.phase)
        assertEquals(60 * min, refilled.remainingMs)
    }

    @Test
    fun `reboot doesn't count the gap - no free refill, no lost budget`() {
        val locked = start.run(60 * min).run(hour, inUse = false)
        // After reboot elapsedRealtime restarts near zero on a new boot count.
        val afterReboot = TimeRules.advance(locked, nowElapsed = 30_000, bootCount = 2, wasInUse = false)
        assertEquals(5 * hour, afterReboot.lockRemainingMs)
        assertEquals(2, afterReboot.bootCount)
        assertEquals(4 * hour, afterReboot.run(hour, inUse = false).lockRemainingMs)
    }

    @Test
    fun `elapsed going backwards on the same boot is ignored`() {
        val s = start.run(10 * min)
        assertEquals(s.remainingMs, TimeRules.advance(s, s.lastElapsed - 5 * min, 1, true).remainingMs)
    }

    @Test
    fun `raising the budget adds the difference, lowering can lock`() {
        val used = start.run(40 * min) // 20 left
        assertEquals(50 * min, TimeRules.applySettings(used, 90 * min, 6 * hour).remainingMs)
        val lowered = TimeRules.applySettings(used, 30 * min, 6 * hour)
        assertEquals(TimerPhase.LOCKED, lowered.phase)
    }

    @Test
    fun `changing the lock length adjusts a running lock`() {
        val locked = start.run(60 * min).run(hour, inUse = false) // 5h left
        assertEquals(7 * hour, TimeRules.applySettings(locked, 60 * min, 8 * hour).lockRemainingMs)
        val shortened = TimeRules.applySettings(locked, 60 * min, 1 * hour)
        assertEquals(TimerPhase.AVAILABLE, shortened.phase)
        assertEquals(60 * min, shortened.remainingMs)
    }

    @Test
    fun `refill uses the latest budget`() {
        val locked = TimeRules.applySettings(start.run(60 * min), 45 * min, 6 * hour)
        assertEquals(45 * min, locked.run(6 * hour, inUse = false).remainingMs)
    }

    private val day1 = LocalDate.of(2026, 9, 24)
    private val sevenAm = LocalTime.of(7, 0)

    @Test
    fun `daily reset baselines when switched on, then refills the next day`() {
        val on = TimeRules.applyDailyReset(start.run(50 * min), day1, LocalTime.of(20, 0), sevenAm)
        assertEquals(10 * min, on.remainingMs) // no refill on switch-on
        val night = on.run(11 * hour, inUse = false)
        val nextMorning = TimeRules.applyDailyReset(night, day1.plusDays(1), LocalTime.of(7, 5), sevenAm)
        assertEquals(60 * min, nextMorning.remainingMs)
        assertEquals(day1.plusDays(1), nextMorning.lastResetDay)
    }

    @Test
    fun `daily reset also ends a running lock`() {
        val longLock = TimeRules.applySettings(start, 60 * min, 24 * hour)
        val on = TimeRules.applyDailyReset(longLock, day1, LocalTime.of(8, 0), sevenAm)
        val locked = on.run(60 * min).run(22 * hour, inUse = false)
        assertEquals(TimerPhase.LOCKED, locked.phase)
        val reset = TimeRules.applyDailyReset(locked, day1.plusDays(1), LocalTime.of(7, 1), sevenAm)
        assertEquals(TimerPhase.AVAILABLE, reset.phase)
        assertEquals(60 * min, reset.remainingMs)
    }

    @Test
    fun `moving the clock forward can't force a daily reset`() {
        val on = TimeRules.applyDailyReset(start.run(50 * min), day1, LocalTime.of(20, 0), sevenAm)
        // Child sets the date to tomorrow 07:05 only 10 minutes later.
        val cheat = TimeRules.applyDailyReset(on.run(10 * min, inUse = false), day1.plusDays(1), LocalTime.of(7, 5), sevenAm)
        assertEquals(10 * min, cheat.remainingMs)
    }

    @Test
    fun `turning daily reset off clears the baseline`() {
        val on = TimeRules.applyDailyReset(start, day1, LocalTime.of(20, 0), sevenAm)
        assertEquals(null, TimeRules.applyDailyReset(on, day1, LocalTime.of(20, 0), null).lastResetDay)
    }
}
