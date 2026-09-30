package com.nourtime.app.core.learning

import com.nourtime.app.core.learning.content.ContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Word Builder (G6). */
class WordBuilderTest {

    @Test
    fun `every level has enough words in both languages and every word can be spelled`() {
        LearnLanguage.entries.forEach { lang ->
            val pack = TestContent.letters(lang)
            TestContent.words.levels.forEach { spec ->
                assertTrue("${lang.tag} ${spec.id}", WordBuilder.candidates(spec, pack).size >= spec.words)
                repeat(20) { seed ->
                    val tasks = WordBuilder.tasks(spec, pack, Random(seed))
                    assertEquals(spec.words, tasks.size)
                    var r = WordRound(tasks)
                    tasks.forEach { t ->
                        assertEquals(t.letters.size + spec.extraTiles, t.tiles.size)
                        assertTrue(t.word.word.length in spec.minLength..spec.maxLength)
                        // The extra tiles are never letters of the word, nor look-alikes of one (أ / ا).
                        val family = t.letters.map(WordBuilder::family).toSet()
                        t.tiles.filter { WordBuilder.family(it.letter) !in family }.let { assertEquals(spec.extraTiles, it.size) }
                        // Spell it with the right tiles in order.
                        val free = t.tiles.toMutableList()
                        t.letters.forEach { l ->
                            val tile = free.first { it.letter == l }
                            free.remove(tile)
                            r = r.place(tile).first
                        }
                    }
                    assertTrue(r.done)
                    assertEquals(0, r.mistakes)
                }
            }
        }
    }

    @Test
    fun `a wrong tile bounces and counts, the right one goes in, words follow each other`() {
        val cat = WordTask(WordEntry("cat", "Cat", "🐱", "animals"), listOf("C", "a", "t"), listOf(LetterTile(0, "C"), LetterTile(1, "a"), LetterTile(2, "t"), LetterTile(3, "x")))
        val dog = WordTask(WordEntry("dog", "Dog", "🐶", "animals"), listOf("D", "o", "g"), listOf(LetterTile(0, "D"), LetterTile(1, "o"), LetterTile(2, "g")))
        var r = WordRound(listOf(cat, dog))
        val (wrong, o) = r.place(cat.tiles[1])
        assertEquals(WordRound.Outcome.WRONG, o)
        assertEquals(1, wrong.mistakes)
        r = wrong.place(cat.tiles[0]).first
        assertEquals("C", r.built)
        assertEquals(WordRound.Outcome.IGNORED, r.place(cat.tiles[0]).second) // already placed
        r = r.place(cat.tiles[1]).first
        val (next, o2) = r.place(cat.tiles[2])
        assertEquals(WordRound.Outcome.WORD_DONE, o2)
        assertEquals(1, next.index)
        assertEquals("", next.built)
        assertEquals(WordRound.Outcome.IGNORED, next.place(cat.tiles[3]).second) // a tile of the old word
    }

    @Test
    fun `words with a space are never used and levels grow longer`() {
        val pack = TestContent.letters(LearnLanguage.ARABIC)
        val all = WordLevel("x", minLength = 2, maxLength = 10)
        assertTrue(WordBuilder.candidates(all, pack).none { ' ' in it.word })
        val levels = TestContent.words.levels
        assertTrue(levels.first().hint)
        assertTrue(levels.last().maxLength >= 8)
        assertTrue(levels.first().maxLength <= 3)
    }

    @Test
    fun `broken word levels are left out`() {
        val problems = mutableListOf<String>()
        val files = mapOf(
            ContentLoader.WORDS to """{"schema": 1, "levels": [
                {"id": "ok", "words": 3},
                {"id": "len", "minLength": 5, "maxLength": 3},
                {"id": "extra", "extraTiles": 12}
            ]}""",
        )
        val l = ContentLoader({ files[it] }, { problems += it })
        assertEquals(listOf("ok"), l.words().levels.map { it.id })
        assertEquals(2, problems.size)
    }
}
