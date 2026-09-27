package com.nourtime.app.data.settings

import com.nourtime.app.core.blocking.isActive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Different limits on weekend days (Phase 4a). Off by default; when a parent turns it on, the
 * weekend values start as a copy of the normal ones.
 */
data class WeekendRules(
    val enabled: Boolean = false,
    val days: Set<DayOfWeek> = defaultDays("ar"),
    val budgetMinutes: Int = TimeLimits.DEFAULT_BUDGET_MINUTES,
    val lockPeriodHours: Int = TimeLimits.DEFAULT_LOCK_HOURS,
    val bedtime: Bedtime = Bedtime(),
) {
    companion object {
        /** Friday and Saturday in most Arabic-speaking countries, Saturday and Sunday elsewhere. */
        fun defaultDays(language: String): Set<DayOfWeek> =
            if (language == "ar") setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY) else setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

data class DayLimits(val budgetMinutes: Int, val lockPeriodHours: Int)

/** Which limits apply on a given day and night. */
object DayRules {

    private const val NOON_HOUR = 12

    fun isWeekend(s: ParentSettings, date: LocalDate): Boolean = s.weekend.enabled && date.dayOfWeek in s.weekend.days

    /** The budget and lock length follow the calendar day. */
    fun limitsOn(s: ParentSettings, date: LocalDate): DayLimits =
        if (isWeekend(s, date)) DayLimits(s.weekend.budgetMinutes, s.weekend.lockPeriodHours)
        else DayLimits(s.budgetMinutes, s.lockPeriodHours)

    /**
     * The bedtime in force at [now], or null. Each night runs from noon to noon, and the night before
     * a weekend day uses the weekend bedtime: Thursday night is relaxed when Friday is off, Saturday
     * night is strict when Sunday is a school day.
     */
    fun activeBedtime(s: ParentSettings, now: LocalDateTime): Bedtime? {
        val night = if (now.hour >= NOON_HOUR) now.toLocalDate() else now.toLocalDate().minusDays(1)
        val bedtime = if (isWeekend(s, night.plusDays(1))) s.weekend.bedtime else s.bedtime
        return bedtime.takeIf { it.isActive(now.toLocalTime()) }
    }
}
