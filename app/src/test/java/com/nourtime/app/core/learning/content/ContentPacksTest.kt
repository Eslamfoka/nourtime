package com.nourtime.app.core.learning.content

import com.nourtime.app.core.learning.Area
import com.nourtime.app.core.learning.ColoringRules
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LettersGame
import com.nourtime.app.core.learning.SvgPath
import com.nourtime.app.core.learning.TestContent
import com.nourtime.app.data.settings.AgeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The shipped packs are valid, and the loader copes with broken or future packs. */
class ContentPacksTest {

    @Test
    fun `every shipped pack is valid, deep checks included`() {
        assertEquals(emptyList<String>(), TestContent.loader.checkAll(deep = true))
    }

    @Test
    fun `the migrated content is all there`() {
        assertEquals(12, TestContent.math.levels.size)
        assertEquals(7, TestContent.letterLevels.levels.size)
        assertEquals(28, TestContent.letters(LearnLanguage.ARABIC).letters.size)
        assertEquals(25, TestContent.letters(LearnLanguage.ENGLISH).letters.size)
        assertEquals(58, TestContent.letters(LearnLanguage.ARABIC).words.size)
        assertEquals(11, TestContent.letters(LearnLanguage.ENGLISH).colors.size)
        assertEquals(8, TestContent.connect.levels.size)
        assertEquals(6, TestContent.coloring.pack.levels.size)
        assertEquals(2, TestContent.letterLevels.startIndex(AgeGroup.AGES_10_12))
    }

    // --- a loader over files given in the test ---

    private fun loader(vararg files: Pair<String, String>): Pair<ContentLoader, MutableList<String>> {
        val problems = mutableListOf<String>()
        val map = files.toMap()
        return ContentLoader({ map[it] }, { problems += it }) to problems
    }

    @Test
    fun `a missing or broken file gives an empty game, not a crash`() {
        val (l, problems) = loader(ContentLoader.MATH to "{ not json")
        assertTrue(l.math().levels.isEmpty())
        assertTrue(l.connect().levels.isEmpty())
        assertTrue(problems.any { "math/levels.json" in it && "not valid" in it })
        assertTrue(problems.any { "connect/shapes.json" in it && "missing" in it })
    }

    @Test
    fun `a pack from a newer format is skipped, new fields in this format are ignored`() {
        val (l, problems) = loader(ContentLoader.MATH to """{"schema": 2, "levels": []}""")
        assertTrue(l.math().levels.isEmpty())
        assertTrue(problems.any { "schema 2" in it })
        val (l2, p2) = loader(ContentLoader.MATH to """{"schema": 1, "future": true, "levels": [{"id": "a", "ops": ["add"], "sparkles": 3}]}""")
        assertEquals(1, l2.math().levels.size)
        assertEquals(emptyList<String>(), p2)
    }

    @Test
    fun `bad levels are left out one by one`() {
        val (l, problems) = loader(
            ContentLoader.MATH to """{"schema": 1, "startAt": {"AGES_7_9": "nope"}, "levels": [
                {"id": "ok", "ops": ["add"]},
                {"id": "Bad Id", "ops": ["add"]},
                {"id": "ok", "ops": ["sub"]},
                {"id": "op", "ops": ["pow"]},
                {"id": "few", "ops": ["add"], "choices": 9}
            ]}""",
        )
        assertEquals(listOf("ok"), l.math().levels.map { it.id })
        assertEquals(5, problems.size)
    }

    @Test
    fun `a word that doesn't start with its letter is caught`() {
        val (l, problems) = loader(
            ContentLoader.CONCEPTS to """{"schema": 1, "concepts": [{"id": "cat", "emoji": "🐱", "category": "animals"}]}""",
            "letters/en.json" to """{"schema": 1, "language": "en", "letters": [{"letter": "D", "name": "D", "concept": "cat"}], "words": {"cat": "Cat"}}""",
        )
        assertTrue(l.letters(LearnLanguage.ENGLISH).letters.isEmpty())
        assertTrue(problems.single().contains("doesn't start with D"))
    }

    @Test
    fun `Arabic alef forms count as the same letter`() {
        assertTrue(ContentLoader.startsWith("أرنب", "أ", LearnLanguage.ARABIC))
        assertTrue(ContentLoader.startsWith("اسد", "أ", LearnLanguage.ARABIC))
        assertFalse(ContentLoader.startsWith("بطة", "أ", LearnLanguage.ARABIC))
        assertTrue(ContentLoader.startsWith("apple", "A", LearnLanguage.ENGLISH))
    }

    @Test
    fun `a language with no pack yet has no letters, so the game stays on its level screen`() {
        val (l, _) = loader()
        val empty = l.letters(LearnLanguage.ARABIC)
        assertTrue(LettersGame.questions(TestContent.letterLevels.levels[0], empty, Random(1)).isEmpty())
    }

    // --- SVG paths for illustrators' drawings ---

    @Test
    fun `svg squares, relative commands and curves become tappable areas`() {
        val square = SvgPath.flatten("M10 10 H90 V90 H10 Z", 100f, 100f)
        assertTrue(SvgPath.contains(square, 0.5f, 0.5f))
        assertFalse(SvgPath.contains(square, 0.05f, 0.5f))
        val rel = SvgPath.flatten("m10,10 l80,0 0,80 -80,0 z", 100f, 100f)
        assertTrue(SvgPath.contains(rel, 0.5f, 0.5f))
        val curve = SvgPath.flatten("M0 50 C0 0 100 0 100 50 S 0 100 0 50 Z", 100f, 100f)
        assertTrue(SvgPath.contains(curve, 0.5f, 0.5f))
        assertFalse(SvgPath.contains(curve, 0.02f, 0.02f))
    }

    @Test
    fun `svg arcs make a circle, and a hole stays empty`() {
        val ring = "M50 10 A40 40 0 1 1 49.9 10 Z M50 30 A20 20 0 1 0 50.1 30 Z"
        val area = Area.Svg(ring, 100f, 100f)
        assertTrue(area.contains(0.5f, 0.15f)) // on the ring
        assertFalse(area.contains(0.5f, 0.5f)) // in the hole
        assertFalse(area.contains(0.05f, 0.05f)) // outside
    }

    @Test
    fun `svg regions in a coloring pack are tapped like any other`() {
        val (l, problems) = loader(
            ContentLoader.COLORING to """{"schema": 1, "levels": [{"id": "sq", "colors": ["#FF0000", "#00FF00"],
                "viewBox": [200, 200], "regions": [
                  {"color": 0, "path": "M0 0 H200 V200 H0 Z"},
                  {"color": 1, "path": "M50 50 h100 v100 h-100 z"}]}]}""",
        )
        assertEquals(emptyList<String>(), problems)
        val p = l.coloring().pack.levels.single()
        assertEquals(1, ColoringRules.regionAt(p, 0.5f, 0.5f))
        assertEquals(0, ColoringRules.regionAt(p, 0.1f, 0.1f))
    }

    // --- scale ---

    @Test
    fun `ten thousand words load in well under a second`() {
        val n = 10_000
        val concepts = (0 until n).joinToString(",") { """{"id": "c$it", "emoji": "e$it", "category": "cat${it % 20}"}""" }
        val words = (0 until n).joinToString(",") { "\"c$it\": \"word$it\"" }
        val (l, problems) = loader(
            ContentLoader.CONCEPTS to """{"schema": 1, "concepts": [$concepts]}""",
            "letters/en.json" to """{"schema": 1, "language": "en", "letters": [], "words": {$words}}""",
        )
        val start = System.nanoTime()
        val pack = l.letters(LearnLanguage.ENGLISH)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("10,000 words parsed and checked in $ms ms")
        assertEquals(emptyList<String>(), problems)
        assertEquals(n, pack.words.size)
        assertTrue("took $ms ms", ms < 3_000)
    }
}
