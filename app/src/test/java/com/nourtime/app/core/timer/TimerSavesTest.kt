package com.nourtime.app.core.timer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The timer state is written when it matters, not every few seconds while nothing is counted. */
class TimerSavesTest {

    private val available = TimerState(
        phase = TimerPhase.AVAILABLE,
        remainingMs = 30 * 60_000L,
        lockRemainingMs = 0,
        budgetMs = 30 * 60_000L,
        lockMs = 60 * 60_000L,
        lastElapsed = 0,
        bootCount = 1,
    )

    private fun needed(
        previous: TimerState = available,
        next: TimerState = available,
        wasInUse: Boolean = false,
        inUse: Boolean = false,
        sinceSaveMs: Long = 0,
        first: Boolean = false,
    ) = TimerSaves.needed(first, previous, next, wasInUse, inUse, sinceSaveMs)

    @Test
    fun `nothing is written while no limited app is in use, however long it lasts`() {
        assertFalse(needed(sinceSaveMs = 60 * 60_000L))
    }

    @Test
    fun `nothing is written during a lock just because time passes`() {
        val locked = available.copy(phase = TimerPhase.LOCKED, remainingMs = 0, lockRemainingMs = 3_000_000)
        assertFalse(needed(previous = locked, next = locked.copy(lockRemainingMs = 2_000_000), sinceSaveMs = 60 * 60_000L))
    }

    @Test
    fun `while time is counted it is written at most every interval`() {
        assertFalse(needed(wasInUse = true, inUse = true, sinceSaveMs = TimerSaves.WHILE_COUNTING_EVERY_MS - 1))
        assertTrue(needed(wasInUse = true, inUse = true, sinceSaveMs = TimerSaves.WHILE_COUNTING_EVERY_MS))
    }

    @Test
    fun `starting or stopping a limited app is written at once`() {
        assertTrue(needed(wasInUse = false, inUse = true))
        assertTrue(needed(wasInUse = true, inUse = false))
    }

    @Test
    fun `a phase change is written at once`() {
        assertTrue(needed(next = available.copy(phase = TimerPhase.LOCKED)))
    }

    @Test
    fun `a daily reset is written at once`() {
        assertTrue(needed(next = available.copy(lastResetDay = LocalDate.of(2026, 10, 13))))
    }

    @Test
    fun `the first state after a start is written`() {
        assertTrue(needed(first = true))
    }
}
