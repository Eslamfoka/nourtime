package com.nourtime.app.feature.learning

import com.nourtime.app.core.learning.GameId
import com.nourtime.app.core.learning.GamePack
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.MathLevel
import com.nourtime.app.core.learning.MathOp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// A missing content pack used to crash the game (`packs.math!!`) instead of leaving it out.
class GameRegistryTest {

    private fun start(packs: HubContent) = GameStart(
        packs = packs,
        letters = { error("no level, so no words are read") },
        wordsLanguage = LearnLanguage.ENGLISH,
        random = Random(1),
    )

    private val math = GamePack(listOf(MathLevel("m1", listOf(MathOp.ADD))))

    @Test
    fun `no game starts without its pack, none crashes`() = runTest {
        for (spec in GameRegistry.all) {
            assertNull(spec.id.name, spec.start(start(HubContent(loaded = true)), 0, false))
        }
    }

    @Test
    fun `a level past the end of its pack doesn't start`() = runTest {
        assertNull(GameRegistry.of(GameId.MATH).start(start(HubContent(math = math, loaded = true)), 1, false))
    }

    @Test
    fun `a level in its pack starts`() = runTest {
        val screen = GameRegistry.of(GameId.MATH).start(start(HubContent(math = math, loaded = true)), 0, false)
        assertTrue(screen is HubScreen.Playing)
    }

    @Test
    fun `while the packs load every game is shown`() {
        assertEquals(GameRegistry.all, GameRegistry.shown(HubContent(), LearnLanguage.ENGLISH))
        assertTrue(GameRegistry.hidden(HubContent(), LearnLanguage.ENGLISH).isEmpty())
    }

    @Test
    fun `once loaded, games with a missing or empty pack are hidden`() {
        val content = HubContent(math = math, clock = GamePack(emptyList()), loaded = true)
        assertEquals(listOf(GameId.MATH), GameRegistry.shown(content, LearnLanguage.ENGLISH).map { it.id })
        assertTrue(GameId.CLOCK in GameRegistry.hidden(content, LearnLanguage.ENGLISH).map { it.id })
    }
}
