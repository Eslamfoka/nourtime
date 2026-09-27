package com.nourtime.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Phase 4b: the last 7 days of use, on the child's Home and the parent's phone. */
class WeekReportTest {

    private val today = LocalDate.of(2026, 9, 27)
    private val min = 60_000L

    private fun use(daysAgo: Long, app: String, minutes: Long) = UsageEntry(today.minusDays(daysAgo), app, minutes * min)

    @Test
    fun `seven days ending today, oldest first, with empty days as zero`() {
        val r = WeekReport.of(listOf(use(0, "yt", 30), use(2, "yt", 10), use(2, "game", 20)), today)
        assertEquals((6L downTo 0L).map { today.minusDays(it) }, r.days.map { it.date })
        assertEquals(listOf(0L, 0L, 0L, 0L, 30 * min, 0L, 30 * min), r.days.map { it.totalMs })
    }

    @Test
    fun `total, daily average and the most used app`() {
        val r = WeekReport.of(listOf(use(0, "yt", 30), use(1, "game", 50), use(3, "yt", 40)), today)
        assertEquals(120 * min, r.totalMs)
        assertEquals(120 * min / 7, r.averagePerDayMs)
        assertEquals("yt", r.topApp)
        assertEquals(70 * min, r.topAppMs)
    }

    @Test
    fun `days older than the week are not counted in it`() {
        val r = WeekReport.of(listOf(use(7, "yt", 99), use(0, "yt", 1)), today)
        assertEquals(1 * min, r.totalMs)
    }

    @Test
    fun `the change against the week before`() {
        val r = WeekReport.of(listOf(use(0, "yt", 60), use(8, "yt", 120)), today)
        assertEquals(-50, r.changePercent)
    }

    @Test
    fun `no change is shown without data from the week before`() {
        assertNull(WeekReport.of(listOf(use(0, "yt", 60)), today).changePercent)
    }

    @Test
    fun `an empty week has no top app`() {
        val r = WeekReport.of(emptyList(), today)
        assertEquals(0L, r.totalMs)
        assertNull(r.topApp)
    }
}
