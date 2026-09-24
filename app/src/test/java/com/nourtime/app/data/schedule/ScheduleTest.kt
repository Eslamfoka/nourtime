package com.nourtime.app.data.schedule

import com.nourtime.app.data.db.PeriodKind
import com.nourtime.app.data.db.SchedulePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {

    private fun p(kind: PeriodKind, start: Int, end: Int) = SchedulePeriod(kind = kind, startMinute = start, endMinute = end)

    @Test
    fun `contains handles same-day and overnight periods`() {
        val study = p(PeriodKind.STUDY, 17 * 60, 19 * 60)
        assertTrue(study.contains(17 * 60))
        assertFalse(study.contains(19 * 60))
        val sleep = p(PeriodKind.SLEEP, 21 * 60, 7 * 60)
        assertTrue(sleep.contains(23 * 60))
        assertTrue(sleep.contains(3 * 60))
        assertFalse(sleep.contains(12 * 60))
        assertFalse(p(PeriodKind.PLAY, 60, 60).contains(60))
    }

    @Test
    fun `period at a time, most recently started wins on overlap`() {
        val day = ScheduleRepository.SUGGESTED_DAY
        assertEquals(PeriodKind.STUDY, day.periodAt(18 * 60)?.kind)
        assertEquals(PeriodKind.SLEEP, day.periodAt(2 * 60)?.kind)
        assertNull(day.periodAt(10 * 60))
        val overlap = listOf(p(PeriodKind.PLAY, 15 * 60, 18 * 60), p(PeriodKind.MEAL, 16 * 60, 17 * 60))
        assertEquals(PeriodKind.MEAL, overlap.periodAt(16 * 60 + 30)?.kind)
    }

    @Test
    fun `length wraps midnight`() {
        assertEquals(10 * 60, p(PeriodKind.SLEEP, 21 * 60, 7 * 60).lengthMinutes)
        assertEquals(120, p(PeriodKind.STUDY, 17 * 60, 19 * 60).lengthMinutes)
    }
}
