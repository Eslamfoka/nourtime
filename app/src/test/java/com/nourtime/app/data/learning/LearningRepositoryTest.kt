package com.nourtime.app.data.learning

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nourtime.app.core.learning.GameId
import com.nourtime.app.core.learning.LearningSettings
import com.nourtime.app.core.learning.LevelProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class LearningRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy { PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("l.preferences_pb") } }
    private val repo by lazy { LearningRepository(store) }
    private val today = LocalDate.of(2026, 9, 30)

    @After
    fun tearDown() = scope.cancel()

    private suspend fun finish(stars: Int = 3, day: LocalDate = today, rewards: Boolean = true) =
        repo.finishLevel(GameId.MATH, levelId = "add-5", stars = stars, today = day, rewards = rewards)

    @Test
    fun `defaults are on, 5 minutes a level, 15 a day`() = runTest {
        assertEquals(LearningSettings(true, 5, 15), repo.settings.first())
    }

    @Test
    fun `won levels fill the bank up to the daily maximum`() = runTest {
        assertEquals(5, finish().earnedMinutes)
        assertEquals(5, finish().earnedMinutes)
        val third = finish()
        assertEquals(5, third.earnedMinutes)
        assertTrue(third.dailyMaxReached)
        assertEquals(0, finish().earnedMinutes)
        assertEquals(15, repo.state.first().bankMinutes)
    }

    @Test
    fun `a new day allows more`() = runTest {
        repeat(4) { finish() }
        assertEquals(5, finish(day = today.plusDays(1)).earnedMinutes)
        assertEquals(20, repo.state.first().bankMinutes)
    }

    @Test
    fun `one star or a preview earns nothing but still counts as progress`() = runTest {
        assertEquals(0, finish(stars = 1).earnedMinutes)
        val preview = finish(rewards = false)
        assertEquals(0, preview.earnedMinutes)
        assertFalse(preview.dailyMaxReached)
        assertEquals(3, repo.state.first().progress[GameId.MATH]?.starsOf("add-5"))
        assertEquals(0, repo.state.first().bankMinutes)
    }

    @Test
    fun `taking the bank empties it`() = runTest {
        finish()
        assertEquals(5, repo.takeBank())
        assertEquals(0, repo.takeBank())
    }

    @Test
    fun `parallel finishes can't pass the daily maximum`() = runTest {
        (1..10).map { scope.async { finish() } }.awaitAll()
        assertEquals(15, repo.state.first().bankMinutes)
    }

    @Test
    fun `stars are kept per level id`() = runTest {
        val p = repo.finishLevel(GameId.MATH, levelId = "add-20", stars = 2, today = today, rewards = true).progress
        assertEquals(2, p.starsOf("add-20"))
        assertEquals(2, repo.state.first().progress[GameId.MATH]?.starsOf("add-20"))
        assertEquals(LevelProgress(), repo.progressOf(LearningState(), GameId.CONNECT))
    }
}
