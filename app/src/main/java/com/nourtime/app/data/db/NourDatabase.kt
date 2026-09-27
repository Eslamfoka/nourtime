package com.nourtime.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Kinds of daily periods; each has its own "Time's up" template (brief §4). */
enum class PeriodKind { STUDY, PLAY, MEAL, SLEEP }

/** A period of the day, in minutes of the day; may cross midnight. */
@Entity(tableName = "schedule_periods")
data class SchedulePeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: PeriodKind,
    val startMinute: Int,
    val endMinute: Int,
)

/** Active use of one limited app on one day (brief §6 stats). */
@Entity(tableName = "daily_usage", primaryKeys = ["epochDay", "packageName"])
data class DailyUsage(
    val epochDay: Long,
    val packageName: String,
    val usedMs: Long,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_periods ORDER BY startMinute")
    fun observeAll(): Flow<List<SchedulePeriod>>

    @Insert
    suspend fun insert(period: SchedulePeriod): Long

    @Insert
    suspend fun insertAll(periods: List<SchedulePeriod>)

    @Update
    suspend fun update(period: SchedulePeriod)

    @Query("DELETE FROM schedule_periods WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM daily_usage WHERE epochDay = :epochDay ORDER BY usedMs DESC")
    fun observeDay(epochDay: Long): Flow<List<DailyUsage>>

    @Query("SELECT * FROM daily_usage WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<DailyUsage>>

    @Query("INSERT OR IGNORE INTO daily_usage (epochDay, packageName, usedMs) VALUES (:epochDay, :packageName, 0)")
    suspend fun ensureRow(epochDay: Long, packageName: String)

    @Query("UPDATE daily_usage SET usedMs = usedMs + :ms WHERE epochDay = :epochDay AND packageName = :packageName")
    suspend fun addTo(epochDay: Long, packageName: String, ms: Long)

    @Query("DELETE FROM daily_usage WHERE epochDay < :beforeEpochDay")
    suspend fun deleteBefore(beforeEpochDay: Long)
}

@Database(entities = [SchedulePeriod::class, DailyUsage::class], version = 1, exportSchema = false)
abstract class NourDatabase : RoomDatabase() {
    abstract fun schedule(): ScheduleDao
    abstract fun usage(): UsageDao
}
