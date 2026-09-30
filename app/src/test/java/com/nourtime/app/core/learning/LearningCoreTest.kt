package com.nourtime.app.core.learning

import com.nourtime.app.data.settings.AgeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LearningCoreTest {

    @Test
    fun `numerals switch between western and eastern digits`() {
        assertEquals("89", NumeralStyle.WESTERN.format(89))
        assertEquals("٨٩", NumeralStyle.EASTERN.format(89))
        assertEquals("١٢٣٤٥٦٧٨٩٠", NumeralStyle.EASTERN.format(1234567890))
    }

    // --- math ---

    private fun solve(q: Question): Int {
        val (a, op, b) = Triple((q.prompt[0] as Card.Number).value, (q.prompt[1] as Card.Symbol).text, (q.prompt[2] as Card.Number).value)
        return when (op) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            "÷" -> a / b
            else -> error(op)
        }
    }

    @Test
    fun `every math answer is right, in range and among distinct choices`() {
        MathLevels.all.indices.forEach { level ->
            repeat(40) { seed ->
                MathLevels.questions(level, Random(seed * 31 + level)).forEach { q ->
                    val spec = MathLevels.all[level]
                    assertEquals(spec.choices.takeIf { q.task == Task.SOLVE } ?: 3, q.choices.size)
                    if (q.task == Task.COMPARE) {
                        val a = (q.prompt[0] as Card.Number).value
                        val b = (q.prompt[2] as Card.Number).value
                        val expected = if (a < b) "<" else if (a == b) "=" else ">"
                        assertEquals(expected, (q.choices[q.answer] as Card.Symbol).text)
                        assertTrue(a in 0..spec.max && b in 0..spec.max)
                    } else {
                        assertEquals(solve(q), (q.choices[q.answer] as Card.Number).value)
                        q.choices.forEach { assertTrue((it as Card.Number).value >= 0) }
                        val op = (q.prompt[1] as Card.Symbol).text
                        if (op == "+" || op == "−") {
                            q.prompt.filterIsInstance<Card.Number>().forEach { assertTrue(it.value <= spec.max) }
                            assertTrue(solve(q) in 0..spec.max)
                        }
                        if (op == "÷") assertEquals(0, (q.prompt[0] as Card.Number).value % (q.prompt[2] as Card.Number).value)
                    }
                }
            }
        }
    }

    @Test
    fun `the first level is small additions with dots`() {
        MathLevels.questions(0, Random(7)).forEach {
            assertTrue(it.dots)
            assertEquals(Task.SOLVE, it.task)
            assertTrue(solve(it) <= 5)
        }
    }

    @Test
    fun `a level's questions don't repeat when there are enough of them`() {
        val prompts = MathLevels.questions(6, Random(3)).map { it.prompt }
        assertEquals(prompts.size, prompts.toSet().size)
    }

    @Test
    fun `the answer isn't always in the same place`() {
        val places = (0 until 50).map { MathLevels.questions(4, Random(it)).first().answer }.toSet()
        assertTrue(places.size > 2)
    }

    // --- letters ---

    @Test
    fun `every letters question has one right answer among distinct choices`() {
        LearnLanguage.entries.forEach { lang ->
            LettersLevels.all.indices.forEach { level ->
                repeat(30) { seed ->
                    val qs = LettersLevels.questions(level, lang, Random(seed))
                    assertEquals(LettersLevels.QUESTIONS, qs.size)
                    qs.forEach { q -> checkLetters(q, lang) }
                }
            }
        }
    }

    private fun checkLetters(q: Question, lang: LearnLanguage) {
        val right = q.choices[q.answer]
        when (q.task) {
            Task.LETTER_TO_PICTURE -> {
                val letter = (q.prompt[0] as Card.Text).text
                val entry = LettersContent.letters(lang).first { it.letter == letter }
                assertEquals(entry.emoji, (right as Card.Picture).emoji)
                assertEquals(1, q.choices.count { (it as Card.Picture).emoji == entry.emoji })
            }
            Task.LETTER_TO_WORD -> {
                val letter = (q.prompt[0] as Card.Text).text
                assertEquals(LettersContent.letters(lang).first { it.letter == letter }.word, (right as Card.Text).text)
                assertTrue(q.say!!.text.contains(right.text))
            }
            Task.WORD_TO_PICTURE -> {
                val word = (q.prompt[0] as Card.Text).text
                assertEquals(LettersContent.words(lang).first { it.word == word }.emoji, (right as Card.Picture).emoji)
            }
            Task.NAME_TO_COLOR -> {
                val name = (q.prompt[0] as Card.Text).text
                assertEquals(LettersContent.colors(lang).first { it.name == name }.argb, (right as Card.Swatch).argb)
            }
            Task.COLOR_TO_NAME -> {
                val argb = (q.prompt[0] as Card.Swatch).argb
                assertEquals(LettersContent.colors(lang).first { it.argb == argb }.name, (right as Card.Text).text)
            }
            else -> error("unexpected ${q.task}")
        }
    }

    @Test
    fun `the word-then-picture level asks both about the same letter`() {
        val qs = LettersLevels.questions(2, LearnLanguage.ARABIC, Random(1))
        qs.chunked(2).forEach { (word, picture) ->
            assertEquals(Task.LETTER_TO_WORD, word.task)
            assertEquals(Task.LETTER_TO_PICTURE, picture.task)
            assertEquals(word.prompt, picture.prompt)
        }
    }

    @Test
    fun `letters speak the letter's name and its word`() {
        val a = LettersContent.letters(LearnLanguage.ARABIC).first()
        assertEquals("ألف، أرنب", LettersContent.speech(a, LearnLanguage.ARABIC))
        assertEquals("A, Apple", LettersContent.speech(LettersContent.letters(LearnLanguage.ENGLISH).first(), LearnLanguage.ENGLISH))
    }

    @Test
    fun `content has no duplicate pictures within a list`() {
        LearnLanguage.entries.forEach { lang ->
            assertEquals(LettersContent.letters(lang).size, LettersContent.letters(lang).map { it.emoji }.toSet().size)
            assertEquals(LettersContent.words(lang).size, LettersContent.words(lang).map { it.emoji }.toSet().size)
        }
        assertEquals(28, LettersContent.letters(LearnLanguage.ARABIC).size)
    }

    // --- rounds ---

    private val q = Question(Task.SOLVE, listOf(Card.Number(1)), listOf(Card.Number(1), Card.Number(2), Card.Number(3)), answer = 1)

    @Test
    fun `a wrong pick is remembered and the child tries again`() {
        val (r1, o1) = Round(listOf(q, q)).pick(0)
        assertEquals(Round.Outcome.WRONG, o1)
        assertEquals(setOf(0), r1.wrong)
        assertEquals(Round.Outcome.IGNORED, r1.pick(0).second)
        val (r2, o2) = r1.pick(1)
        assertEquals(Round.Outcome.CORRECT, o2)
        assertEquals(1, r2.index)
        assertEquals(0, r2.firstTry)
        assertTrue(r2.wrong.isEmpty())
    }

    @Test
    fun `stars come from first-try answers and the tutorial doesn't count`() {
        var r = Round(List(6) { q }, tutorial = true)
        r = r.pick(0).first.pick(1).first // tutorial, missed: doesn't count
        repeat(5) { r = r.pick(1).first }
        assertTrue(r.done)
        assertEquals(5, r.scored)
        assertEquals(5, r.firstTry)
        assertEquals(3, r.stars)
    }

    @Test
    fun `star thresholds`() {
        assertEquals(3, Stars.of(9, 10))
        assertEquals(2, Stars.of(7, 10))
        assertEquals(2, Stars.of(4, 5))
        assertEquals(1, Stars.of(3, 5))
        assertEquals(1, Stars.of(0, 6))
    }

    // --- rewards and progress ---

    @Test
    fun `a won level earns minutes up to the daily maximum`() {
        val s = LearningSettings(minutesPerLevel = 5, dailyMaxMinutes = 12)
        assertEquals(5, RewardPolicy.earn(s, stars = 2, earnedToday = 0))
        assertEquals(2, RewardPolicy.earn(s, stars = 3, earnedToday = 10))
        assertEquals(0, RewardPolicy.earn(s, stars = 3, earnedToday = 12))
        assertEquals(0, RewardPolicy.earn(s, stars = 1, earnedToday = 0))
        assertEquals(0, RewardPolicy.earn(s.copy(enabled = false), stars = 3, earnedToday = 0))
        assertEquals(0, RewardPolicy.earn(s.copy(dailyMaxMinutes = 0), stars = 3, earnedToday = 0))
    }

    @Test
    fun `finishing a level opens the next and keeps the best stars`() {
        val p = LevelProgress().finished(0, 2, levelCount = 3).finished(0, 1, levelCount = 3)
        assertEquals(1, p.unlocked)
        assertEquals(2, p.stars[0])
        assertEquals(2, p.finished(2, 3, levelCount = 3).unlocked)
        assertEquals(2, p.finished(1, 3, 3).finished(2, 3, 3).unlocked)
    }

    @Test
    fun `progress survives encoding and ignores junk`() {
        val p = LevelProgress(4, mapOf(0 to 3, 3 to 1))
        assertEquals(p, LevelProgress.decode(p.encode()))
        assertEquals(LevelProgress(2), LevelProgress.decode("2|"))
        assertNull(LevelProgress.decode("x|0:3"))
        assertEquals(LevelProgress(1, mapOf(0 to 3)), LevelProgress.decode("1|0:9,zz,1:"))
    }

    @Test
    fun `older children start further in`() {
        assertEquals(0, LevelProgress.start(GameId.MATH, AgeGroup.AGES_3_6).unlocked)
        assertEquals(4, LevelProgress.start(GameId.MATH, AgeGroup.AGES_10_12).unlocked)
        assertFalse(LevelProgress.start(GameId.LETTERS, AgeGroup.AGES_7_9).unlocked > 0)
    }
}
