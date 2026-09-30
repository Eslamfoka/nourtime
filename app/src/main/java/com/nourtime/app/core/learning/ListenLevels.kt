package com.nourtime.app.core.learning

import kotlin.random.Random

/**
 * One Listen & Find level (from `listen/levels.json`): the child hears something and taps it.
 * [tasks] take turns; [categories] narrow the pictures (empty = all); [maxNumber] bounds the numbers
 * of [Task.LISTEN_TO_NUMBER] (from 0); [firstLettersOnly] uses the first half of the alphabet.
 */
data class ListenLevel(
    override val id: String,
    val tasks: List<Task>,
    val choices: Int = 3,
    val categories: Set<String> = emptySet(),
    val firstLettersOnly: Boolean = false,
    val maxNumber: Int = 10,
    val questions: Int = ListenGame.QUESTIONS,
) : Level

/**
 * Makes Listen & Find questions from a level and a language pack (the same words, colors and letters
 * as Letters & Words). Every question is read aloud when it appears ([Question.say]) and again when
 * the speaker is tapped.
 */
object ListenGame {
    const val QUESTIONS = 6

    fun questions(spec: ListenLevel, content: LettersLanguagePack, random: Random, count: Int = spec.questions): List<Question> {
        val language = content.language
        val letters = content.letters.let { if (spec.firstLettersOnly) it.take(it.size / 2) else it }
        val words = content.words.filter { spec.categories.isEmpty() || it.category in spec.categories }
            .distinctBy { it.emoji }
        val enough = spec.tasks.all { task ->
            when (task) {
                Task.LISTEN_TO_PICTURE -> words.size >= spec.choices
                Task.LISTEN_TO_COLOR -> content.colors.size >= spec.choices
                Task.LISTEN_TO_LETTER -> letters.size >= spec.choices
                Task.LISTEN_TO_NUMBER -> spec.maxNumber + 1 >= spec.choices
                else -> false
            }
        }
        // A language with no pack yet: no level to play (the game stays on its level screen).
        if (!enough) return emptyList()
        val usedWords = generateSequence { words.shuffled(random) }.flatten().iterator()
        val usedColors = generateSequence { content.colors.shuffled(random) }.flatten().iterator()
        val usedLetters = generateSequence { letters.shuffled(random) }.flatten().iterator()
        val usedNumbers = generateSequence { (0..spec.maxNumber).shuffled(random) }.flatten().iterator()
        return List(count) { i ->
            when (spec.tasks[i % spec.tasks.size]) {
                Task.LISTEN_TO_PICTURE -> {
                    val w = usedWords.next()
                    val others = words.filter { it.emoji != w.emoji }.shuffled(random).take(spec.choices - 1)
                    question(Task.LISTEN_TO_PICTURE, Card.Sound(w.word, w.word), w.word, language, (listOf(w) + others).map { Card.Picture(it.emoji, it.word, it.image) }, random)
                }
                Task.LISTEN_TO_COLOR -> {
                    val c = usedColors.next()
                    val others = content.colors.filter { it.argb != c.argb }.shuffled(random).take(spec.choices - 1)
                    question(Task.LISTEN_TO_COLOR, Card.Sound(c.name, c.name), c.name, language, (listOf(c) + others).map { Card.Swatch(it.argb, it.name) }, random)
                }
                Task.LISTEN_TO_LETTER -> {
                    val l = usedLetters.next()
                    val others = letters.filter { it.letter != l.letter }.shuffled(random).take(spec.choices - 1)
                    question(Task.LISTEN_TO_LETTER, Card.Sound(l.name, l.name), l.name, language, (listOf(l) + others).map { Card.Text(it.letter, speech = it.name) }, random)
                }
                Task.LISTEN_TO_NUMBER -> {
                    val n = usedNumbers.next()
                    val choices = MathGame.numberChoices(n, spec.choices, random).map(Card::Number)
                    question(Task.LISTEN_TO_NUMBER, Card.Sound(n.toString(), n.toString(), number = n), n.toString(), language, choices, random)
                }
                else -> error("not a listening task")
            }
        }
    }

    private fun question(task: Task, prompt: Card.Sound, say: String, language: LearnLanguage, choices: List<Card>, random: Random) = Question(
        task = task,
        prompt = listOf(prompt),
        choices = choices,
        answer = 0,
        say = Speech(say, language),
    ).shuffled(random)
}
