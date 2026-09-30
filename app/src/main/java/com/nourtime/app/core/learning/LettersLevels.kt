package com.nourtime.app.core.learning

import kotlin.random.Random

/**
 * One Letters & Words level (from `letters/levels.json`): which tasks, how many choices, which part
 * of the alphabet, and which word categories (empty = all).
 */
data class LettersLevel(
    override val id: String,
    val tasks: List<Task>,
    val choices: Int = 3,
    val firstLettersOnly: Boolean = false,
    val categories: Set<String> = emptySet(),
    val questions: Int = LettersGame.QUESTIONS,
) : Level {
    /** The main flow: hear "A, Apple", pick the word, then the picture, for the same letter. */
    val pairs: Boolean get() = tasks == listOf(Task.LETTER_TO_WORD, Task.LETTER_TO_PICTURE)
}

/** Makes Letters & Words questions from a level and a language pack. */
object LettersGame {
    const val QUESTIONS = 6

    fun questions(spec: LettersLevel, content: LettersLanguagePack, random: Random, count: Int = spec.questions): List<Question> {
        val language = content.language
        val letters = content.letters.let { if (spec.firstLettersOnly) it.take(it.size / 2) else it }
        val words = content.words.filter { spec.categories.isEmpty() || it.category in spec.categories }
        // Every task needs something to ask about and enough things to choose from; without them
        // (a language with no pack yet) there's no level to play. Also keeps the cycling below finite.
        val needs = spec.tasks.map { task ->
            when (task) {
                Task.LETTER_TO_PICTURE, Task.LETTER_TO_WORD -> letters.size
                Task.WORD_TO_PICTURE, Task.PICTURE_TO_WORD -> words.size
                Task.NAME_TO_COLOR, Task.COLOR_TO_NAME -> content.colors.size
                Task.SOLVE, Task.COMPARE -> 0
            }
        }
        if (needs.any { it < spec.choices }) return emptyList()
        val out = mutableListOf<Question>()
        if (spec.pairs) {
            letters.shuffled(random).take(count / 2).forEach { entry ->
                out += letterToWord(entry, letters, spec.choices, content, random)
                out += letterToPicture(entry, letters, spec.choices, content, random)
            }
            return out
        }
        // Cycling: a small pack still fills a long level (repeats come in a new order).
        val usedLetters = generateSequence { letters.shuffled(random) }.flatten().iterator()
        val usedWords = generateSequence { words.shuffled(random) }.flatten().iterator()
        val usedColors = generateSequence { content.colors.shuffled(random) }.flatten().iterator()
        repeat(count) { i ->
            out += when (spec.tasks[i % spec.tasks.size]) {
                Task.LETTER_TO_PICTURE -> letterToPicture(usedLetters.next(), letters, spec.choices, content, random)
                Task.LETTER_TO_WORD -> letterToWord(usedLetters.next(), letters, spec.choices, content, random)
                Task.WORD_TO_PICTURE -> wordToPicture(usedWords.next(), words, spec.choices, language, random)
                Task.NAME_TO_COLOR -> nameToColor(usedColors.next(), content.colors, spec.choices, language, random)
                Task.COLOR_TO_NAME -> colorToName(usedColors.next(), content.colors, spec.choices, random)
                Task.PICTURE_TO_WORD -> pictureToWord(usedWords.next(), words, spec.choices, random)
                Task.SOLVE, Task.COMPARE -> error("not a letters task")
            }
        }
        return out
    }

    private fun letterPrompt(entry: LetterEntry) = Card.Text(entry.letter, speech = entry.name)

    private fun letterToPicture(entry: LetterEntry, pool: List<LetterEntry>, n: Int, content: LettersLanguagePack, random: Random) = Question(
        task = Task.LETTER_TO_PICTURE,
        prompt = listOf(letterPrompt(entry)),
        choices = (listOf(entry) + others(entry, pool, n - 1, random) { it.emoji }).map { Card.Picture(it.emoji, it.word, it.image) },
        answer = 0,
        say = Speech(content.speech(entry), content.language),
    ).shuffled(random)

    private fun letterToWord(entry: LetterEntry, pool: List<LetterEntry>, n: Int, content: LettersLanguagePack, random: Random) = Question(
        task = Task.LETTER_TO_WORD,
        prompt = listOf(letterPrompt(entry)),
        choices = (listOf(entry) + others(entry, pool, n - 1, random) { it.word }).map { Card.Text(it.word) },
        answer = 0,
        say = Speech(content.speech(entry), content.language),
    ).shuffled(random)

    private fun wordToPicture(entry: WordEntry, pool: List<WordEntry>, n: Int, lang: LearnLanguage, random: Random): Question {
        return Question(
            task = Task.WORD_TO_PICTURE,
            prompt = listOf(Card.Text(entry.word)),
            choices = (listOf(entry) + others(entry, pool, n - 1, random) { it.emoji }).map { Card.Picture(it.emoji, it.word, it.image) },
            answer = 0,
            say = Speech(entry.word, lang),
        ).shuffled(random)
    }

    private fun pictureToWord(entry: WordEntry, pool: List<WordEntry>, n: Int, random: Random): Question {
        // Distinct words and pictures: two choices must never both be right.
        val others = pool.filter { it.word != entry.word && it.emoji != entry.emoji }.distinctBy { it.word }.shuffled(random).take(n - 1)
        return Question(
            task = Task.PICTURE_TO_WORD,
            prompt = listOf(Card.Picture(entry.emoji, entry.word, entry.image)),
            choices = (listOf(entry) + others).map { Card.Text(it.word) },
            answer = 0,
        ).shuffled(random)
    }

    private fun nameToColor(entry: ColorEntry, pool: List<ColorEntry>, n: Int, lang: LearnLanguage, random: Random): Question {
        return Question(
            task = Task.NAME_TO_COLOR,
            prompt = listOf(Card.Text(entry.name)),
            choices = (listOf(entry) + others(entry, pool, n - 1, random) { it.argb }).map { Card.Swatch(it.argb, it.name) },
            answer = 0,
            say = Speech(entry.name, lang),
        ).shuffled(random)
    }

    private fun colorToName(entry: ColorEntry, pool: List<ColorEntry>, n: Int, random: Random): Question {
        return Question(
            task = Task.COLOR_TO_NAME,
            prompt = listOf(Card.Swatch(entry.argb, entry.name)),
            choices = (listOf(entry) + others(entry, pool, n - 1, random) { it.argb }).map { Card.Text(it.name) },
            answer = 0,
        ).shuffled(random)
    }

    /** [n] distractors from [pool] that differ from [answer] in what the child sees ([key]). */
    private fun <T, K> others(answer: T, pool: List<T>, n: Int, random: Random, key: (T) -> K): List<T> =
        pool.filter { key(it) != key(answer) }.distinctBy(key).shuffled(random).take(n)
}
