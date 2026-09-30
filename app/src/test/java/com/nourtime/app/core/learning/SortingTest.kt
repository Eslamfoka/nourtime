package com.nourtime.app.core.learning

import com.nourtime.app.core.learning.content.ContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Sorting (G7). */
class SortingTest {

    @Test
    fun `every level deals in both languages and every thing belongs to its group`() {
        LearnLanguage.entries.forEach { lang ->
            val pack = TestContent.letters(lang)
            TestContent.sorting.levels.forEach { spec ->
                repeat(20) { seed ->
                    val dealt = SortGame.deal(spec, pack, Random(seed))
                    assertNotNull("${lang.tag} ${spec.id}", dealt)
                    val round = dealt!!
                    assertEquals(spec.items, round.items.size)
                    round.bins.forEach { assertTrue("${spec.id} label", it.label.isNotBlank()) }
                    round.items.forEach { item ->
                        val bin = spec.bins[item.bin]
                        when (val c = item.card) {
                            is Card.Picture -> assertEquals(bin.category, pack.words.first { it.emoji == c.emoji }.category)
                            is Card.Number -> {
                                assertEquals(bin.parity == "even", c.value % 2 == 0)
                                assertTrue(c.value in 1..spec.max)
                            }
                            else -> error("unexpected $c")
                        }
                    }
                    // Nothing twice, and every group gets something.
                    assertEquals(round.items.size, round.items.map { it.card }.toSet().size)
                    assertEquals(spec.bins.indices.toSet(), round.items.map { it.bin }.toSet())
                    // Sorted without a mistake, it ends.
                    var r = round
                    round.items.forEach { r = r.drop(it.bin).first }
                    assertTrue(r.done)
                    assertEquals(3, r.stars)
                }
            }
        }
    }

    @Test
    fun `a wrong group is a mistake and the thing stays`() {
        val r = SortRound(listOf(SortBin("", "Even", "even"), SortBin("", "Odd", "odd")), listOf(SortItem(Card.Number(3), 1), SortItem(Card.Number(4), 0)))
        val (wrong, o) = r.drop(0)
        assertEquals(SortRound.Outcome.WRONG, o)
        assertEquals(0, wrong.index)
        assertEquals(1, wrong.mistakes)
        val (right, o2) = wrong.drop(1)
        assertEquals(SortRound.Outcome.RIGHT, o2)
        assertEquals(1, right.count(1))
        assertEquals(SortRound.Outcome.DONE, right.drop(0).second)
    }

    @Test
    fun `broken sorting levels are left out`() {
        val problems = mutableListOf<String>()
        val files = mapOf(
            ContentLoader.SORTING to """{"schema": 1, "levels": [
                {"id": "ok", "bins": [{"parity": "even", "labels": {"en": "Even"}}, {"parity": "odd", "labels": {"en": "Odd"}}]},
                {"id": "one", "bins": [{"parity": "even", "labels": {"en": "Even"}}]},
                {"id": "both", "bins": [{"parity": "even", "category": "food", "labels": {"en": "x"}}, {"parity": "odd", "labels": {"en": "Odd"}}]},
                {"id": "nolabel", "bins": [{"category": "food"}, {"category": "animals", "labels": {"en": "A"}}]}
            ]}""",
        )
        val l = ContentLoader({ files[it] }, { problems += it })
        assertEquals(listOf("ok"), l.sorting().levels.map { it.id })
        assertEquals(3, problems.size)
    }
}
