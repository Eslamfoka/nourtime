package com.nourtime.app.core.learning

import com.nourtime.app.core.learning.content.ContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Memory Match (G5). */
class MemoryGameTest {

    /** What a card "means", so two cards of different pairs never mean the same. */
    private fun meaning(c: Card): String = when (c) {
        is Card.Picture -> "pic:" + c.emoji
        is Card.Text -> "text:" + c.text
        is Card.Number -> "num:" + c.value
        is Card.Dots -> "dots:" + c.count
        is Card.Swatch -> "color:" + c.argb
        else -> error("unexpected $c")
    }

    @Test
    fun `every level deals its pairs, each pair belonging together and no card ambiguous`() {
        LearnLanguage.entries.forEach { lang ->
            val pack = TestContent.letters(lang)
            TestContent.memory.levels.forEach { spec ->
                repeat(30) { seed ->
                    val cards = MemoryGame.deal(spec, pack, Random(seed))
                    assertEquals("${lang.tag} ${spec.id}", spec.pairs * 2, cards.size)
                    cards.groupBy { it.pair }.values.forEach { pair ->
                        assertEquals(2, pair.size)
                        val (x, y) = pair.map { it.face }
                        fun belong(a: Card, b: Card) = when {
                            a is Card.Picture && b is Card.Picture -> a.emoji == b.emoji
                            a is Card.Picture && b is Card.Text -> pack.words.any { it.emoji == a.emoji && it.word == b.text }
                            a is Card.Text && b is Card.Picture -> pack.letters.any { it.letter == a.text && it.emoji == b.emoji }
                            a is Card.Number && b is Card.Dots -> a.value == b.count && a.value in 1..spec.maxNumber
                            a is Card.Swatch && b is Card.Text -> a.name == b.text
                            else -> false
                        }
                        // Dealt in any order.
                        val ok = belong(x, y) || belong(y, x)
                        assertTrue("${spec.id}: $x / $y", ok)
                    }
                    // A card can only match its own partner.
                    val meanings = cards.groupBy { it.pair }.values.flatMap { p -> p.map { meaning(it.face) }.distinct() }
                    assertEquals(spec.id, meanings.size, meanings.toSet().size)
                }
            }
        }
    }

    private val cards = listOf(
        MemoryCard(0, Card.Number(1)), MemoryCard(1, Card.Number(2)),
        MemoryCard(0, Card.Dots(1)), MemoryCard(1, Card.Dots(2)),
    )

    @Test
    fun `turning two matching cards keeps them, a mismatch waits and turns back`() {
        var r = MemoryRound(cards)
        r = r.turn(0).first
        val (miss, o) = r.turn(1)
        assertEquals(MemoryRound.Outcome.MISMATCH, o)
        assertTrue(miss.waiting)
        assertEquals(MemoryRound.Outcome.IGNORED, miss.turn(2).second) // wait for the pair to turn back
        r = miss.hide()
        assertEquals(1, r.moves)
        r = r.turn(0).first
        val (hit, o2) = r.turn(2)
        assertEquals(MemoryRound.Outcome.MATCH, o2)
        assertEquals(MemoryRound.Outcome.IGNORED, hit.turn(0).second) // matched cards stay
        val (done, o3) = hit.turn(1).first.turn(3)
        assertEquals(MemoryRound.Outcome.DONE, o3)
        assertTrue(done.done)
        assertEquals(3, done.moves)
    }

    @Test
    fun `stars come from moves per pair`() {
        assertEquals(3, MemoryRound(cards, moves = 2).stars)
        assertEquals(3, MemoryRound(cards, moves = 3).stars)
        assertEquals(2, MemoryRound(cards, moves = 5).stars)
        assertEquals(1, MemoryRound(cards, moves = 6).stars)
    }

    @Test
    fun `the table grows from two pairs to eight in rows of up to four`() {
        val levels = TestContent.memory.levels
        assertTrue(levels.size >= 20)
        assertEquals(2, levels.first().pairs)
        assertEquals(8, levels.last().pairs)
        assertEquals(2, MemoryGame.columns(4))
        assertEquals(3, MemoryGame.columns(6))
        assertEquals(4, MemoryGame.columns(16))
    }

    @Test
    fun `broken memory levels are left out`() {
        val problems = mutableListOf<String>()
        val files = mapOf(
            ContentLoader.MEMORY to """{"schema": 1, "levels": [
                {"id": "ok", "pairs": 3, "kinds": ["same_picture"]},
                {"id": "big", "pairs": 12, "kinds": ["same_picture"]},
                {"id": "kind", "pairs": 3, "kinds": ["smells"]},
                {"id": "dots", "pairs": 6, "kinds": ["number_dots"], "maxNumber": 3}
            ]}""",
        )
        val l = ContentLoader({ files[it] }, { problems += it })
        assertEquals(listOf("ok"), l.memory().levels.map { it.id })
        assertEquals(3, problems.size)
    }
}
