package com.nourtime.app.data.schedule

import com.nourtime.app.data.db.NourDatabase
import com.nourtime.app.data.db.PeriodKind
import com.nourtime.app.data.db.SchedulePeriod
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(db: NourDatabase) {
    private val dao = db.schedule()

    val periods: Flow<List<SchedulePeriod>> = dao.observeAll()

    suspend fun add(kind: PeriodKind, startMinute: Int, endMinute: Int) {
        dao.insert(SchedulePeriod(kind = kind, startMinute = startMinute.mod(DAY), endMinute = endMinute.mod(DAY)))
    }

    suspend fun update(period: SchedulePeriod) = dao.update(period.copy(startMinute = period.startMinute.mod(DAY), endMinute = period.endMinute.mod(DAY)))

    suspend fun delete(id: Long) = dao.delete(id)

    /** A typical school day the parent can start from and adjust. */
    suspend fun addSuggestedDay() = dao.insertAll(SUGGESTED_DAY)

    companion object {
        const val DAY = 24 * 60

        val SUGGESTED_DAY = listOf(
            SchedulePeriod(kind = PeriodKind.MEAL, startMinute = 7 * 60, endMinute = 8 * 60),
            SchedulePeriod(kind = PeriodKind.MEAL, startMinute = 14 * 60, endMinute = 15 * 60),
            SchedulePeriod(kind = PeriodKind.PLAY, startMinute = 15 * 60, endMinute = 17 * 60),
            SchedulePeriod(kind = PeriodKind.STUDY, startMinute = 17 * 60, endMinute = 19 * 60),
            SchedulePeriod(kind = PeriodKind.MEAL, startMinute = 19 * 60, endMinute = 20 * 60),
            SchedulePeriod(kind = PeriodKind.SLEEP, startMinute = 20 * 60 + 30, endMinute = 7 * 60),
        )
    }
}

/** True when [minute] of the day is inside the period (windows may cross midnight). */
fun SchedulePeriod.contains(minute: Int): Boolean = when {
    startMinute == endMinute -> false
    startMinute < endMinute -> minute in startMinute until endMinute
    else -> minute >= startMinute || minute < endMinute
}

/** The period in effect at [minute]; when periods overlap, the one that started most recently wins. */
fun List<SchedulePeriod>.periodAt(minute: Int): SchedulePeriod? =
    filter { it.contains(minute) }.minByOrNull { (minute - it.startMinute).mod(ScheduleRepository.DAY) }

/** Length of the period in minutes. */
val SchedulePeriod.lengthMinutes: Int get() = (endMinute - startMinute).mod(ScheduleRepository.DAY)
