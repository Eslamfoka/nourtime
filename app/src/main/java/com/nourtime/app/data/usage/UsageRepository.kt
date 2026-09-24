package com.nourtime.app.data.usage

import androidx.room.withTransaction
import com.nourtime.app.data.db.DailyUsage
import com.nourtime.app.data.db.NourDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Active use per limited app per day, kept on the device for the stats screen (brief §6). */
@Singleton
class UsageRepository @Inject constructor(private val db: NourDatabase) {
    private val dao = db.usage()

    fun observeDay(day: LocalDate): Flow<List<DailyUsage>> = dao.observeDay(day.toEpochDay())

    suspend fun add(day: LocalDate, packageName: String, ms: Long) {
        if (ms <= 0) return
        db.withTransaction {
            dao.ensureRow(day.toEpochDay(), packageName)
            dao.addTo(day.toEpochDay(), packageName, ms)
        }
    }

    /** Only recent history is useful; keep the database small. */
    suspend fun pruneBefore(day: LocalDate) = dao.deleteBefore(day.toEpochDay())
}
