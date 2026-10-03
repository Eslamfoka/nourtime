package com.nourtime.app.data.learning

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nourtime.app.core.learning.GameId
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LearningSettings
import com.nourtime.app.core.learning.LevelProgress
import com.nourtime.app.core.learning.VoicePack
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

    private var levels = 0

    /** Finishes [level]; by default one never finished before, since a replay earns only with more stars. */
    private suspend fun finish(stars: Int = 3, day: LocalDate = today, rewards: Boolean = true, level: String = "level-${levels++}") =
        repo.finishLevel(GameId.MATH, levelId = level, stars = stars, today = day, rewards = rewards)

    @Test
    fun `defaults are on, 5 minutes a level, 15 a day`() = runTest {
        assertEquals(LearningSettings(true, 5, 15), repo.settings.first())
    }

    @Test
    fun `the voice is chosen per language and kept`() = runTest {
        assertEquals(emptyMap<LearnLanguage, VoicePack>(), repo.state.first().voicePacks)
        repo.setVoicePack(VoicePack.ARABIC_EGYPTIAN)
        assertEquals(mapOf(LearnLanguage.ARABIC to VoicePack.ARABIC_EGYPTIAN), repo.state.first().voicePacks)
        repo.setVoicePack(VoicePack.ARABIC_FUSHA)
        assertEquals(VoicePack.ARABIC_FUSHA, repo.state.first().voicePacks[LearnLanguage.ARABIC])
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
        assertEquals(15, repo.takeBank())
        assertEquals(5, finish(day = today.plusDays(1)).earnedMinutes)
        assertEquals(5, repo.state.first().bankMinutes)
    }

    @Test
    fun `a replay earns only with more stars than before`() = runTest {
        assertEquals(5, finish(stars = 2, level = "add-5").earnedMinutes)
        val same = finish(stars = 2, level = "add-5")
        assertEquals(0, same.earnedMinutes)
        assertTrue(same.noBetterStars)
        assertEquals(5, finish(stars = 3, level = "add-5").earnedMinutes)
        assertEquals(0, finish(stars = 3, level = "add-5").earnedMinutes)
        assertEquals(10, repo.state.first().bankMinutes)
    }

    @Test
    fun `a full bank earns nothing until it's used`() = runTest {
        repeat(3) { finish() }
        val full = finish(day = today.plusDays(1))
        assertEquals(0, full.earnedMinutes)
        assertTrue(full.bankFull)
        assertEquals(15, repo.takeBank())
        assertEquals(5, finish(day = today.plusDays(1)).earnedMinutes)
    }

    @Test
    fun `one star or a preview earns nothing but still counts as progress`() = runTest {
        assertEquals(0, finish(stars = 1, level = "add-5").earnedMinutes)
        val preview = finish(rewards = false, level = "add-5")
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
        (1..10).map { i -> scope.async { finish(level = "par-$i") } }.awaitAll()
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
