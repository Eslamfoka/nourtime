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

    @Test
    fun `eastern numerals write math right to left with mirrored relation signs`() {
        assertTrue(MathWriting.rightToLeft(NumeralStyle.EASTERN))
        assertFalse(MathWriting.rightToLeft(NumeralStyle.WESTERN))
        // "3 < 5" right to left: ٥ on the left, and the sign still opens toward it.
        assertEquals(">", MathWriting.glyph("<", rightToLeft = true))
        assertEquals("<", MathWriting.glyph(">", rightToLeft = true))
        assertEquals("؟", MathWriting.glyph("?", rightToLeft = true))
        assertEquals("=", MathWriting.glyph("=", rightToLeft = true))
        assertEquals("−", MathWriting.glyph("−", rightToLeft = true))
        assertEquals("<", MathWriting.glyph("<", rightToLeft = false))
    }

    // --- math ---

    /** The answer of "a op b = ?" or of "a op ? = c" (a missing number). */
    private fun solve(q: Question): Int {
        val a = (q.prompt[0] as Card.Number).value
        val op = (q.prompt[1] as Card.Symbol).text
        if (q.prompt[2] == Card.Symbol("?")) {
            val c = (q.prompt[4] as Card.Number).value
            return when (op) {
                "+" -> c - a
                "−" -> a - c
                "×" -> c / a
                else -> error(op)
            }
        }
        val b = (q.prompt[2] as Card.Number).value
        return when (op) {
            "+" -> a + b
            "−" -> a - b
            "×" -> a * b
            "÷" -> a / b
            else -> error(op)
        }
    }

    private fun mathLevel(id: String) = TestContent.math.levels.first { it.id == id }

    @Test
    fun `every math answer is right, in range and among distinct choices`() {
        TestContent.math.levels.forEachIndexed { level, spec ->
            repeat(40) { seed ->
                MathGame.questions(spec, Random(seed * 31 + level)).forEach { q ->
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
                            // The number added or taken away is never below the level's minimum.
                            val added = if (q.prompt[2] == Card.Symbol("?")) solve(q) else (q.prompt[2] as Card.Number).value
                            assertTrue("${spec.id}: $added < ${spec.min}", added >= spec.min)
                        }
                        if (op == "×" && q.prompt[2] == Card.Symbol("?")) assertEquals(0, (q.prompt[4] as Card.Number).value % (q.prompt[0] as Card.Number).value)
                        if (op == "÷") assertEquals(0, (q.prompt[0] as Card.Number).value % (q.prompt[2] as Card.Number).value)
                    }
                }
            }
        }
    }

    @Test
    fun `the first level is small additions with dots`() {
        MathGame.questions(TestContent.math.levels[0], Random(7)).forEach {
            assertTrue(it.dots)
            assertEquals(Task.SOLVE, it.task)
            assertTrue(solve(it) <= 5)
        }
    }

    @Test
    fun `a level's questions don't repeat when there are enough of them`() {
        val prompts = MathGame.questions(mathLevel("add-sub-100"), Random(3)).map { it.prompt }
        assertEquals(prompts.size, prompts.toSet().size)
    }

    @Test
    fun `a tiny level repeats questions but never the same one twice in a row`() {
        // add-3 has only three sums (1+1, 1+2, 2+1) for six questions; seen on the emulator as 2+1 three times running.
        repeat(50) { seed ->
            val prompts = MathGame.questions(mathLevel("add-3"), Random(seed)).map { it.prompt }
            assertEquals(MathGame.QUESTIONS, prompts.size)
            prompts.zipWithNext().forEach { (a, b) -> assertTrue("seed $seed: $a twice", a != b) }
        }
    }

    @Test
    fun `missing-number levels hide the second number and ask for it`() {
        MathGame.questions(mathLevel("missing-sub-20"), Random(5)).forEach { q ->
            assertEquals(Card.Symbol("?"), q.prompt[2])
            assertEquals(Card.Symbol("−"), q.prompt[1])
            val a = (q.prompt[0] as Card.Number).value
            val c = (q.prompt[4] as Card.Number).value
            assertEquals(a - c, (q.choices[q.answer] as Card.Number).value)
        }
    }

    @Test
    fun `levels get harder, the math pack starts tiny and ends with every operation`() {
        val levels = TestContent.math.levels
        assertTrue(levels.size >= 40)
        assertEquals(3, levels.first().max)
        assertTrue(levels.first().dots)
        assertEquals(MathOp.entries.toSet(), levels.last().ops.toSet())
        // The biggest number in play never shrinks by much from one level to the next.
        val reach = levels.map { l -> maxOf(if (l.ops.any { it.additive }) l.max else 0, if (l.ops.any { it.multiplicative }) l.factor * 10 else 0) }
        assertTrue(reach.last() >= 1000)
    }

    @Test
    fun `long equations get a smaller font so they fit on one line`() {
        val short = listOf(Card.Number(2), Card.Symbol("+"), Card.Number(3), Card.Symbol("="), Card.Symbol("?"))
        val long = listOf(Card.Number(999), Card.Symbol("+"), Card.Number(999), Card.Symbol("="), Card.Symbol("?"))
        val longest = listOf(Card.Number(1000), Card.Symbol("−"), Card.Symbol("?"), Card.Symbol("="), Card.Number(1000))
        assertEquals(52, MathWriting.promptSize(short))
        assertTrue(MathWriting.promptSize(long) < 52)
        assertTrue(MathWriting.promptSize(longest) <= MathWriting.promptSize(long))
    }

    @Test
    fun `the answer isn't always in the same place`() {
        val places = (0 until 50).map { MathGame.questions(mathLevel("add-20"), Random(it)).first().answer }.toSet()
        assertTrue(places.size > 2)
    }

    // --- letters ---

    @Test
    fun `every letters question has one right answer among distinct choices`() {
        LearnLanguage.entries.forEach { lang ->
            TestContent.letterLevels.levels.forEach { level ->
                repeat(30) { seed ->
                    val qs = LettersGame.questions(level, TestContent.letters(lang), Random(seed))
                    assertEquals(level.questions, qs.size)
                    qs.forEach { q -> checkLetters(q, TestContent.letters(lang)) }
                }
            }
        }
    }

    private fun checkLetters(q: Question, lang: LettersLanguagePack) {
        val right = q.choices[q.answer]
        when (q.task) {
            Task.LETTER_TO_PICTURE -> {
                val letter = (q.prompt[0] as Card.Text).text
                val entry = lang.letters.first { it.letter == letter }
                assertEquals(entry.emoji, (right as Card.Picture).emoji)
                assertEquals(1, q.choices.count { (it as Card.Picture).emoji == entry.emoji })
            }
            Task.LETTER_TO_WORD -> {
                val letter = (q.prompt[0] as Card.Text).text
                assertEquals(lang.letters.first { it.letter == letter }.word, (right as Card.Text).text)
                assertTrue(q.say!!.text.contains(right.text))
            }
            Task.WORD_TO_PICTURE -> {
                val word = (q.prompt[0] as Card.Text).text
                assertEquals(lang.words.first { it.word == word }.emoji, (right as Card.Picture).emoji)
            }
            Task.NAME_TO_COLOR -> {
                val name = (q.prompt[0] as Card.Text).text
                assertEquals(lang.colors.first { it.name == name }.argb, (right as Card.Swatch).argb)
            }
            Task.COLOR_TO_NAME -> {
                val argb = (q.prompt[0] as Card.Swatch).argb
                assertEquals(lang.colors.first { it.argb == argb }.name, (right as Card.Text).text)
            }
            Task.PICTURE_TO_WORD -> {
                val picture = q.prompt[0] as Card.Picture
                assertEquals(picture.word, (right as Card.Text).text)
                // No other choice names the same picture, and the word isn't read aloud.
                q.choices.filter { it != right }.forEach { c -> assertTrue(lang.words.first { it.word == (c as Card.Text).text }.emoji != picture.emoji) }
                assertNull(q.say)
            }
            else -> error("unexpected ${q.task}")
        }
    }

    @Test
    fun `every word category has enough words for its levels, in both languages`() {
        LearnLanguage.entries.forEach { lang ->
            val pack = TestContent.letters(lang)
            TestContent.letterLevels.levels.filter { it.categories.isNotEmpty() }.forEach { level ->
                val words = pack.words.count { it.category in level.categories }
                assertTrue("${lang.tag} ${level.id}: $words words", words >= level.choices * 2)
            }
        }
    }

    @Test
    fun `the word-then-picture level asks both about the same letter`() {
        val level = TestContent.letterLevels.levels.first { it.pairs }
        val qs = LettersGame.questions(level, TestContent.letters(LearnLanguage.ARABIC), Random(1))
        qs.chunked(2).forEach { (word, picture) ->
            assertEquals(Task.LETTER_TO_WORD, word.task)
            assertEquals(Task.LETTER_TO_PICTURE, picture.task)
            assertEquals(word.prompt, picture.prompt)
        }
    }

    @Test
    fun `letters speak the letter's name and its word`() {
        val ar = TestContent.letters(LearnLanguage.ARABIC)
        assertEquals("ألف، أرنب", ar.speech(ar.letters.first()))
        val en = TestContent.letters(LearnLanguage.ENGLISH)
        assertEquals("A, Apple", en.speech(en.letters.first()))
    }

    @Test
    fun `content has no duplicate pictures within a list`() {
        LearnLanguage.entries.forEach { lang ->
            val pack = TestContent.letters(lang)
            assertEquals(pack.letters.size, pack.letters.map { it.emoji }.toSet().size)
            assertEquals(pack.words.size, pack.words.map { it.emoji }.toSet().size)
        }
        assertEquals(28, TestContent.letters(LearnLanguage.ARABIC).letters.size)
        assertEquals(26, TestContent.letters(LearnLanguage.ENGLISH).letters.size)
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
    fun `a replay earns only with more stars than before`() {
        val s = LearningSettings(minutesPerLevel = 5, dailyMaxMinutes = 15)
        assertEquals(0, RewardPolicy.earn(s, stars = 2, earnedToday = 0, previousStars = 2))
        assertEquals(0, RewardPolicy.earn(s, stars = 3, earnedToday = 0, previousStars = 3))
        assertEquals(5, RewardPolicy.earn(s, stars = 3, earnedToday = 0, previousStars = 2))
        // One star earned nothing, so two stars later is the first win.
        assertEquals(5, RewardPolicy.earn(s, stars = 2, earnedToday = 0, previousStars = 1))
    }

    @Test
    fun `the bank never holds more than the daily maximum`() {
        val s = LearningSettings(minutesPerLevel = 5, dailyMaxMinutes = 15)
        assertEquals(5, RewardPolicy.earn(s, stars = 3, earnedToday = 0, bank = 10))
        assertEquals(3, RewardPolicy.earn(s, stars = 3, earnedToday = 0, bank = 12))
        assertEquals(0, RewardPolicy.earn(s, stars = 3, earnedToday = 0, bank = 15))
        // The parent lowered the maximum below what's already banked: nothing more.
        assertEquals(0, RewardPolicy.earn(s.copy(dailyMaxMinutes = 10), stars = 3, earnedToday = 0, bank = 15))
    }

    private data class L(override val id: String) : Level

    private val three = GamePack(listOf(L("a"), L("b"), L("c")))

    @Test
    fun `finishing a level opens the next and keeps the best stars`() {
        val p = LevelProgress().finished("a", 2).finished("a", 1)
        assertEquals(2, p.starsOf("a"))
        assertTrue(p.playable(three, 1, null))
        assertFalse(p.playable(three, 2, null))
        assertEquals(1, p.current(three, null))
        assertEquals(2, p.finished("b", 3).current(three, null))
    }

    @Test
    fun `a level added later in the middle keeps everyone's stars`() {
        val p = LevelProgress().finished("a", 3).finished("b", 2)
        val grown = GamePack(listOf(L("a"), L("a2"), L("b"), L("c")))
        assertEquals(2, p.starsOf("b"))
        assertTrue(p.playable(grown, 1, null)) // the new level after a finished one
        assertTrue(p.playable(grown, 2, null)) // b was finished: it stays open
        assertTrue(p.playable(grown, 3, null)) // c is still open after b
        assertEquals(3, p.current(grown, null))
    }

    @Test
    fun `progress survives encoding and ignores junk`() {
        val p = LevelProgress(mapOf("add-5" to 3, "mul-2-10" to 1))
        assertEquals(p, LevelProgress.decode(p.encode()))
        assertEquals(LevelProgress(), LevelProgress.decode(null))
        assertEquals(LevelProgress(mapOf("ok" to 3)), LevelProgress.decode("ok:9,Bad Id:2,zz,x:"))
    }

    @Test
    fun `older children start further in`() {
        val math = TestContent.math
        assertEquals(0, LevelProgress().current(math, AgeGroup.AGES_3_6))
        assertEquals("add-20", math.levels[LevelProgress().current(math, AgeGroup.AGES_10_12)].id)
        val letters = TestContent.letterLevels
        assertEquals(0, LevelProgress().current(letters, AgeGroup.AGES_3_6))
        assertEquals("letter-picture-all", letters.levels[LevelProgress().current(letters, AgeGroup.AGES_7_9)].id)
    }
}
