package com.nourtime.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Phase 4a: different limits on weekend days. */
class DayRulesTest {

    // 2026-10-01 is a Thursday; Friday and Saturday are the weekend here.
    private val thursday = LocalDate.of(2026, 10, 1)
    private val friday = thursday.plusDays(1)
    private val saturday = thursday.plusDays(2)
    private val sunday = thursday.plusDays(3)

    private val schoolBedtime = Bedtime(enabled = true, startMinute = 21 * 60, endMinute = 7 * 60)
    private val weekendBedtime = Bedtime(enabled = true, startMinute = 23 * 60, endMinute = 9 * 60)

    private val settings = ParentSettings(
        budgetMinutes = 60,
        lockPeriodHours = 6,
        bedtime = schoolBedtime,
        weekend = WeekendRules(
            enabled = true,
            days = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
            budgetMinutes = 120,
            lockPeriodHours = 4,
            bedtime = weekendBedtime,
        ),
    )

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(date, LocalTime.of(hour, minute))

    @Test
    fun `weekend days use the weekend budget and lock length`() {
        assertEquals(DayLimits(120, 4), DayRules.limitsOn(settings, friday))
        assertEquals(DayLimits(120, 4), DayRules.limitsOn(settings, saturday))
        assertEquals(DayLimits(60, 6), DayRules.limitsOn(settings, sunday))
        assertEquals(DayLimits(60, 6), DayRules.limitsOn(settings, thursday))
    }

    @Test
    fun `turned off, every day uses the normal limits`() {
        val off = settings.copy(weekend = settings.weekend.copy(enabled = false))
        assertEquals(DayLimits(60, 6), DayRules.limitsOn(off, friday))
        assertEquals(schoolBedtime, DayRules.activeBedtime(off, at(friday, 22)))
    }

    @Test
    fun `the night before a weekend day uses the weekend bedtime`() {
        // Thursday night (no school on Friday): 22:00 is still allowed, 23:30 is bedtime.
        assertNull(DayRules.activeBedtime(settings, at(thursday, 22)))
        assertEquals(weekendBedtime, DayRules.activeBedtime(settings, at(thursday, 23, 30)))
        // Friday morning after that night: bedtime lasts until 9:00.
        assertEquals(weekendBedtime, DayRules.activeBedtime(settings, at(friday, 8)))
        assertNull(DayRules.activeBedtime(settings, at(friday, 9)))
    }

    @Test
    fun `the night before a school day uses the normal bedtime`() {
        // Saturday night (school on Sunday): bedtime from 21:00 until 7:00.
        assertEquals(schoolBedtime, DayRules.activeBedtime(settings, at(saturday, 21, 30)))
        assertEquals(schoolBedtime, DayRules.activeBedtime(settings, at(sunday, 6, 59)))
        assertNull(DayRules.activeBedtime(settings, at(sunday, 7)))
    }

    @Test
    fun `a weekend bedtime can be off while the school one is on`() {
        val noWeekendBedtime = settings.copy(weekend = settings.weekend.copy(bedtime = weekendBedtime.copy(enabled = false)))
        assertNull(DayRules.activeBedtime(noWeekendBedtime, at(friday, 23, 30)))
        assertEquals(schoolBedtime, DayRules.activeBedtime(noWeekendBedtime, at(saturday, 21, 30)))
    }

    @Test
    fun `the default weekend follows the language`() {
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), WeekendRules.defaultDays("ar"))
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), WeekendRules.defaultDays("en"))
    }
}
