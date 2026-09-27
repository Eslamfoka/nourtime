package com.nourtime.app.data.usage

import java.time.LocalDate
import kotlin.math.roundToInt

/** Minutes of one limited app on one day. */
data class UsageEntry(val date: LocalDate, val packageName: String, val ms: Long)

data class DayTotal(val date: LocalDate, val totalMs: Long)

/**
 * The last 7 days of use, ending today (Phase 4b), for the child's Home and the parent's phone.
 * [changePercent] compares with the 7 days before; null when those have no data.
 */
data class WeekReport(
    val days: List<DayTotal>,
    val totalMs: Long,
    val averagePerDayMs: Long,
    val topApp: String?,
    val topAppMs: Long,
    val changePercent: Int?,
) {
    companion object {
        const val DAYS = 7L

        /** From [first] of the previous week through today: what a caller needs to load. */
        fun firstDayNeeded(today: LocalDate): LocalDate = today.minusDays(2 * DAYS - 1)

        fun of(entries: List<UsageEntry>, today: LocalDate): WeekReport {
            val weekStart = today.minusDays(DAYS - 1)
            val week = entries.filter { it.date in weekStart..today }
            val previous = entries.filter { it.date in weekStart.minusDays(DAYS)..weekStart.minusDays(1) }
            val byDay = week.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.ms } }
            val byApp = week.groupBy { it.packageName }.mapValues { (_, list) -> list.sumOf { it.ms } }
            val total = week.sumOf { it.ms }
            val previousTotal = previous.sumOf { it.ms }
            val top = byApp.maxByOrNull { it.value }?.takeIf { it.value > 0 }
            return WeekReport(
                days = (0 until DAYS).map { weekStart.plusDays(it) }.map { DayTotal(it, byDay[it] ?: 0) },
                totalMs = total,
                averagePerDayMs = total / DAYS,
                topApp = top?.key,
                topAppMs = top?.value ?: 0,
                changePercent = if (previousTotal > 0) ((total - previousTotal) * 100.0 / previousTotal).roundToInt() else null,
            )
        }
    }
}
