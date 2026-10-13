package com.nourtime.app.core.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.WallClock
import com.nourtime.app.data.settings.ParentSettings
import com.nourtime.app.data.usage.UsageSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** The engine around [TimeRules]: what it counts, what it charges to which app, and when it writes. */
class TimeEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val youtube = "com.google.android.youtube"
    private val settings = ParentSettings(budgetMinutes = 30, lockPeriodHours = 1, limitedApps = setOf(youtube))
    private val today = LocalDate.of(2026, 10, 13)

    private var elapsed = 1_000_000L
    private val clock = object : DeviceClock {
        override fun elapsedRealtime() = elapsed
        override fun bootCount() = 1
    }
    private val wall = WallClock { ZonedDateTime.of(today.atTime(16, 0), ZoneOffset.UTC) }
    private val charged = mutableListOf<Triple<LocalDate, String, Long>>()
    private val usage = UsageSink { day, pkg, ms -> charged += Triple(day, pkg, ms) }

    /** Counts the writes that reach the file. */
    private class CountingStore(private val inner: DataStore<Preferences>) : DataStore<Preferences> {
        var writes = 0
        override val data: Flow<Preferences> get() = inner.data
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writes++
            return inner.updateData(transform)
        }
    }

    private lateinit var file: File

    private fun TestScope.newStore(): CountingStore {
        if (!::file.isInitialized) file = tmp.newFile("timer.preferences_pb")
        return CountingStore(PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher(testScheduler))) { file })
    }

    private fun engine(store: DataStore<Preferences>) = TimeEngine(store, clock, wall, usage)

    /** Ticks once a second, like the timer service while the screen is on. */
    private suspend fun TimeEngine.run(seconds: Int, inUse: Set<String>) {
        repeat(seconds) {
            elapsed += 1_000
            update(inUse, DetectionSource.ACCESSIBILITY, settings)
        }
    }

    @Test
    fun `time is counted only while a limited app is in use`() = runTest {
        val engine = engine(newStore())
        engine.update(emptySet(), DetectionSource.ACCESSIBILITY, settings)
        engine.run(60, emptySet())
        assertEquals(30 * 60_000L, engine.status.value!!.remainingMs)
        engine.update(setOf(youtube), DetectionSource.ACCESSIBILITY, settings)
        engine.run(90, setOf(youtube))
        assertEquals(30 * 60_000L - 90_000, engine.status.value!!.remainingMs)
    }

    @Test
    fun `nothing is written while nothing is counted, and every 15 seconds while it is`() = runTest {
        val store = newStore()
        val engine = engine(store)
        engine.update(emptySet(), DetectionSource.ACCESSIBILITY, settings)
        val afterStart = store.writes
        engine.run(5 * 60, emptySet())
        assertEquals("idle for 5 minutes", afterStart, store.writes)

        engine.update(setOf(youtube), DetectionSource.ACCESSIBILITY, settings) // starting to count: written
        val counting = store.writes
        engine.run(60, setOf(youtube))
        assertEquals("one minute counted", counting + 4, store.writes)
    }

    @Test
    fun `used time is charged to the app and written to the stats when saved`() = runTest {
        val engine = engine(newStore())
        engine.update(setOf(youtube), DetectionSource.ACCESSIBILITY, settings)
        engine.run(30, setOf(youtube))
        engine.update(emptySet(), DetectionSource.ACCESSIBILITY, settings)
        // Time after the app closed isn't charged; flush doesn't advance the clock.
        elapsed += 10_000
        engine.flush()
        assertEquals(30_000L, charged.filter { it.second == youtube }.sumOf { it.third })
        assertTrue(charged.all { it.first == today })
    }

    @Test
    fun `the budget running out starts the lock and it is written at once`() = runTest {
        val store = newStore()
        val engine = engine(store)
        val short = settings.copy(budgetMinutes = 5)
        engine.update(setOf(youtube), DetectionSource.ACCESSIBILITY, short)
        repeat(5 * 60 - 1) {
            elapsed += 1_000
            engine.update(setOf(youtube), DetectionSource.ACCESSIBILITY, short)
        }
        val before = store.writes
        elapsed += 1_000
        engine.update(setOf(youtube), DetectionSource.ACCESSIBILITY, short)
        assertEquals(TimerPhase.LOCKED, engine.status.value!!.phase)
        assertEquals(before + 1, store.writes)
    }

    @Test
    fun `after a restart the saved state comes back, losing at most the unsaved seconds`() = runTest {
        val first = engine(newStore())
        first.update(setOf(youtube), DetectionSource.ACCESSIBILITY, settings)
        first.run(100, setOf(youtube))
        // The process dies without a flush: the last periodic save was at most 15 s ago.
        val restarted = engine(newStoreSameFile())
        restarted.update(emptySet(), DetectionSource.ACCESSIBILITY, settings)
        val remaining = restarted.status.value!!.remainingMs
        assertTrue("remaining $remaining", remaining in (30 * 60_000L - 100_000)..(30 * 60_000L - 100_000 + TimerSaves.WHILE_COUNTING_EVERY_MS))
    }

    @Test
    fun `a command from the parent is written at once`() = runTest {
        val store = newStore()
        val engine = engine(store)
        engine.update(emptySet(), DetectionSource.ACCESSIBILITY, settings)
        val before = store.writes
        engine.apply(TimerCommand.LockNow)
        assertEquals(before + 1, store.writes)
        assertEquals(TimerPhase.LOCKED, engine.status.value!!.phase)
    }

    // A second DataStore on the same file needs the first one's scope gone; reading the file directly
    // into a fresh store keeps the test simple.
    private fun TestScope.newStoreSameFile(): CountingStore {
        val copy = tmp.newFile("timer-copy.preferences_pb")
        file.copyTo(copy, overwrite = true)
        return CountingStore(PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher(testScheduler))) { copy })
    }
}
