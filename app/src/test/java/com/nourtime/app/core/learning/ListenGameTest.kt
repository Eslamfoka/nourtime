package com.nourtime.app.core.learning

import com.nourtime.app.core.learning.content.ContentLoader
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.feature.learning.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Listen & Find (G1) and the game registry it was the first user of. */
class ListenGameTest {

    @Test
    fun `every listening question says its answer and has exactly one right choice`() {
        LearnLanguage.entries.forEach { lang ->
            val pack = TestContent.letters(lang)
            TestContent.listen.levels.forEach { level ->
                repeat(30) { seed ->
                    val qs = ListenGame.questions(level, pack, Random(seed))
                    assertEquals(level.id, level.questions, qs.size)
                    qs.forEach { q ->
                        val sound = q.prompt.single() as Card.Sound
                        assertEquals(sound.speech, q.say!!.text)
                        assertEquals(lang, q.say!!.language)
                        assertEquals(level.choices, q.choices.size)
                        val right = q.choices[q.answer]
                        when (q.task) {
                            Task.LISTEN_TO_PICTURE -> {
                                val emoji = pack.words.first { it.word == sound.speech }.emoji
                                assertEquals(emoji, (right as Card.Picture).emoji)
                                assertEquals(1, q.choices.count { (it as Card.Picture).emoji == emoji })
                            }
                            Task.LISTEN_TO_COLOR -> assertEquals(pack.colors.first { it.name == sound.speech }.argb, (right as Card.Swatch).argb)
                            Task.LISTEN_TO_LETTER -> assertEquals(pack.letters.first { it.name == sound.speech }.letter, (right as Card.Text).text)
                            Task.LISTEN_TO_NUMBER -> {
                                assertEquals(sound.number, (right as Card.Number).value)
                                assertTrue(sound.number!! in 0..level.maxNumber)
                            }
                            else -> error("unexpected ${q.task}")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `the pack starts with two pictures for the youngest and ends mixing every kind`() {
        val levels = TestContent.listen.levels
        assertTrue(levels.size >= 20)
        assertEquals(2, levels.first().choices)
        assertEquals(setOf(Task.LISTEN_TO_PICTURE, Task.LISTEN_TO_COLOR, Task.LISTEN_TO_LETTER, Task.LISTEN_TO_NUMBER), levels.last().tasks.toSet())
        assertEquals("hear-numbers-20", levels[TestContent.listen.startIndex(AgeGroup.AGES_10_12)].id)
    }

    @Test
    fun `listening tasks belong to the listen pack only`() {
        val problems = mutableListOf<String>()
        val files = mapOf(
            ContentLoader.LETTER_LEVELS to """{"schema": 1, "levels": [{"id": "a", "tasks": ["listen_to_picture"]}]}""",
            ContentLoader.LISTEN to """{"schema": 1, "levels": [{"id": "b", "tasks": ["word_to_picture"]}, {"id": "c", "tasks": ["listen_to_number"], "choices": 4, "maxNumber": 1}]}""",
        )
        val l = ContentLoader({ files[it] }, { problems += it })
        assertTrue(l.letterLevels().levels.isEmpty())
        assertTrue(l.listen().levels.isEmpty())
        assertEquals(3, problems.size)
    }

    @Test
    fun `without words in a language there is nothing to play`() {
        val empty = LettersLanguagePack(LearnLanguage.ARABIC, emptyList(), emptyList(), emptyList())
        assertTrue(ListenGame.questions(TestContent.listen.levels.first(), empty, Random(1)).isEmpty())
    }

    @Test
    fun `every game is registered exactly once`() {
        assertEquals(GameId.entries.toList(), GameRegistry.all.map { it.id }.sorted())
    }
}
