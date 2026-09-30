package com.nourtime.app.core.learning

import com.nourtime.app.core.learning.content.ContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** What Comes Next? (G2). */
class PatternGameTest {

    /** True when [cards] repeat [unit] with one distinct card per letter. */
    private fun fits(unit: String, cards: List<Card>): Boolean {
        val map = mutableMapOf<Char, Card>()
        cards.forEachIndexed { i, c ->
            val letter = unit[i % unit.length]
            if (map.getOrPut(letter) { c } != c) return false
        }
        return map.values.toSet().size == map.size
    }

    /** True when [numbers] follow one of the level's number rules. */
    private fun follows(spec: PatternLevel, numbers: List<Int>): Boolean {
        if (numbers.any { it !in 0..spec.max }) return false
        val diffs = numbers.zipWithNext { a, b -> b - a }.toSet()
        val step = "step" in spec.rules && diffs.size == 1 && diffs.single() in spec.steps
        val double = "double" in spec.rules && numbers.zipWithNext().all { (a, b) -> b == a * 2 }
        return step || double
    }

    @Test
    fun `every pattern has exactly one choice that continues it`() {
        LearnLanguage.entries.forEach { lang ->
            val pack = TestContent.letters(lang)
            TestContent.patterns.levels.forEach { spec ->
                repeat(40) { seed ->
                    val qs = PatternGame.questions(spec, pack, Random(seed))
                    assertEquals(spec.id, spec.questions, qs.size)
                    qs.forEach { q ->
                        assertEquals(Task.PATTERN, q.task)
                        assertEquals(spec.shown + 1, q.prompt.size)
                        assertEquals(Card.Symbol("?"), q.prompt.last())
                        assertEquals(spec.choices, q.choices.size)
                        val shown = q.prompt.dropLast(1)
                        q.choices.forEachIndexed { i, choice ->
                            val ok = if (spec.kind == PatternKind.NUMBERS) {
                                follows(spec, (shown + choice).map { (it as Card.Number).value })
                            } else {
                                spec.rules.any { fits(it, shown + choice) }
                            }
                            assertEquals("${spec.id} seed $seed choice $i: ${shown + choice}", i == q.answer, ok)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `the pack starts with two-item patterns and two choices, and ends with harder numbers`() {
        val levels = TestContent.patterns.levels
        assertTrue(levels.size >= 20)
        assertEquals(listOf("ab"), levels.first().rules)
        assertEquals(2, levels.first().choices)
        assertEquals(PatternKind.NUMBERS, levels.last().kind)
        assertTrue(levels.last().max >= 200)
        assertEquals(PatternKind.entries.toSet(), levels.map { it.kind }.toSet())
    }

    @Test
    fun `shapes are silent and white never appears as a color`() {
        val pack = TestContent.letters(LearnLanguage.ENGLISH)
        val colors = TestContent.patterns.levels.first { it.kind == PatternKind.COLORS }
        repeat(20) { seed ->
            PatternGame.questions(colors, pack, Random(seed)).forEach { q ->
                (q.prompt + q.choices).filterIsInstance<Card.Swatch>().forEach { assertTrue(it.argb != 0xFFFFFFFFL) }
            }
        }
        val shapes = TestContent.patterns.levels.first { it.kind == PatternKind.SHAPES }
        PatternGame.questions(shapes, pack, Random(1)).forEach { q ->
            q.choices.filterIsInstance<Card.Text>().forEach { assertEquals("", it.speech) }
        }
    }

    @Test
    fun `broken pattern levels are left out`() {
        val problems = mutableListOf<String>()
        val files = mapOf(
            ContentLoader.PATTERNS to """{"schema": 1, "levels": [
                {"id": "ok", "kind": "colors", "rules": ["ab"]},
                {"id": "kind", "kind": "sounds", "rules": ["ab"]},
                {"id": "rule", "kind": "numbers", "rules": ["ab"]},
                {"id": "zero", "kind": "numbers", "rules": ["step"], "steps": [0]},
                {"id": "small", "kind": "numbers", "rules": ["step"], "steps": [10], "max": 20},
                {"id": "dbl", "kind": "numbers", "rules": ["double"], "shown": 5, "max": 20}
            ]}""",
        )
        val l = ContentLoader({ files[it] }, { problems += it })
        assertEquals(listOf("ok"), l.patterns().levels.map { it.id })
        assertEquals(5, problems.size)
        assertFalse(problems.any { "ok" in it.split(" ")[1] })
    }
}
