package com.nourtime.app.core.learning

import kotlin.random.Random

/** One Letters & Words level: which tasks, how many choices, and which letters it uses. */
data class LettersLevel(val tasks: List<Task>, val choices: Int = 3, val firstLettersOnly: Boolean = false)

object LettersLevels {
    const val QUESTIONS = 6

    val all: List<LettersLevel> = listOf(
        LettersLevel(listOf(Task.LETTER_TO_PICTURE), firstLettersOnly = true),
        LettersLevel(listOf(Task.LETTER_TO_PICTURE), choices = 4),
        // The owner's main flow: hear "A, Apple", pick the word, then the picture for the same letter.
        LettersLevel(listOf(Task.LETTER_TO_WORD, Task.LETTER_TO_PICTURE)),
        LettersLevel(listOf(Task.WORD_TO_PICTURE)),
        LettersLevel(listOf(Task.NAME_TO_COLOR)),
        LettersLevel(listOf(Task.COLOR_TO_NAME)),
        LettersLevel(listOf(Task.LETTER_TO_WORD, Task.WORD_TO_PICTURE, Task.NAME_TO_COLOR, Task.COLOR_TO_NAME), choices = 4),
    )

    fun questions(level: Int, language: LearnLanguage, random: Random, count: Int = QUESTIONS): List<Question> {
        val spec = all[level.coerceIn(all.indices)]
        val letters = LettersContent.letters(language).let { if (spec.firstLettersOnly) it.take(it.size / 2) else it }
        val out = mutableListOf<Question>()
        if (spec.tasks == listOf(Task.LETTER_TO_WORD, Task.LETTER_TO_PICTURE)) {
            letters.shuffled(random).take(count / 2).forEach { entry ->
                out += letterToWord(entry, letters, spec.choices, language, random)
                out += letterToPicture(entry, letters, spec.choices, language, random)
            }
            return out
        }
        val usedLetters = letters.shuffled(random).iterator()
        val usedWords = LettersContent.words(language).shuffled(random).iterator()
        val usedColors = generateSequence { LettersContent.colors(language).shuffled(random) }.flatten().iterator()
        repeat(count) { i ->
            out += when (spec.tasks[i % spec.tasks.size]) {
                Task.LETTER_TO_PICTURE -> letterToPicture(usedLetters.next(), letters, spec.choices, language, random)
                Task.LETTER_TO_WORD -> letterToWord(usedLetters.next(), letters, spec.choices, language, random)
                Task.WORD_TO_PICTURE -> wordToPicture(usedWords.next(), spec.choices, language, random)
                Task.NAME_TO_COLOR -> nameToColor(usedColors.next(), spec.choices, language, random)
                Task.COLOR_TO_NAME -> colorToName(usedColors.next(), spec.choices, language, random)
                Task.SOLVE, Task.COMPARE -> error("not a letters task")
            }
        }
        return out
    }

    private fun letterPrompt(entry: LetterEntry) = Card.Text(entry.letter, speech = entry.name)

    private fun letterToPicture(entry: LetterEntry, pool: List<LetterEntry>, n: Int, lang: LearnLanguage, random: Random) = Question(
        task = Task.LETTER_TO_PICTURE,
        prompt = listOf(letterPrompt(entry)),
        choices = (listOf(entry) + others(entry, pool, n - 1, random) { it.emoji }).map { Card.Picture(it.emoji, it.word) },
        answer = 0,
        say = Speech(LettersContent.speech(entry, lang), lang),
    ).shuffled(random)

    private fun letterToWord(entry: LetterEntry, pool: List<LetterEntry>, n: Int, lang: LearnLanguage, random: Random) = Question(
        task = Task.LETTER_TO_WORD,
        prompt = listOf(letterPrompt(entry)),
        choices = (listOf(entry) + others(entry, pool, n - 1, random) { it.word }).map { Card.Text(it.word) },
        answer = 0,
        say = Speech(LettersContent.speech(entry, lang), lang),
    ).shuffled(random)

    private fun wordToPicture(entry: WordEntry, n: Int, lang: LearnLanguage, random: Random): Question {
        val pool = LettersContent.words(lang)
        return Question(
            task = Task.WORD_TO_PICTURE,
            prompt = listOf(Card.Text(entry.word)),
            choices = (listOf(entry) + others(entry, pool, n - 1, random) { it.emoji }).map { Card.Picture(it.emoji, it.word) },
            answer = 0,
            say = Speech(entry.word, lang),
        ).shuffled(random)
    }

    private fun nameToColor(entry: ColorEntry, n: Int, lang: LearnLanguage, random: Random): Question {
        val pool = LettersContent.colors(lang)
        return Question(
            task = Task.NAME_TO_COLOR,
            prompt = listOf(Card.Text(entry.name)),
            choices = (listOf(entry) + others(entry, pool, n - 1, random) { it.argb }).map { Card.Swatch(it.argb, it.name) },
            answer = 0,
            say = Speech(entry.name, lang),
        ).shuffled(random)
    }

    private fun colorToName(entry: ColorEntry, n: Int, lang: LearnLanguage, random: Random): Question {
        val pool = LettersContent.colors(lang)
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
