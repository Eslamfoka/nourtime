package com.nourtime.app.core.learning

import kotlin.random.Random

/** What a "What Comes Next?" pattern is made of. [key] is the name used in content files. */
enum class PatternKind(val key: String) { COLORS("colors"), SHAPES("shapes"), PICTURES("pictures"), NUMBERS("numbers") }

/**
 * One "What Comes Next?" level (from `patterns/levels.json`).
 * - Colors, shapes and pictures repeat a unit: [rules] like `ab`, `aab`, `abc` (each letter one item).
 * - Numbers follow `step` (add one of [steps] each time; negative steps count down) or `double`,
 *   with every number, the answer included, in 0..[max].
 * [shown] items are shown before the "?".
 */
data class PatternLevel(
    override val id: String,
    val kind: PatternKind,
    val rules: List<String>,
    val shown: Int = 4,
    val steps: List<Int> = listOf(1),
    val max: Int = 20,
    val categories: Set<String> = emptySet(),
    val choices: Int = 3,
    val questions: Int = PatternGame.QUESTIONS,
) : Level

object PatternGame {
    const val QUESTIONS = 6

    /** Repeating units: letters a–d, each a different item. */
    val UNITS = setOf("ab", "aab", "abb", "abc", "aabb", "abbc", "abcd")

    /** Number rules. */
    val NUMBER_RULES = setOf("step", "double")

    /** Plain shapes (drawn as text, so they take the text color and never depend on emoji). */
    val SHAPES = listOf("●", "■", "▲", "★", "♥", "◆")

    fun questions(spec: PatternLevel, content: LettersLanguagePack, random: Random, count: Int = spec.questions): List<Question> {
        val pool: List<Card> = when (spec.kind) {
            // White would vanish on the white card.
            PatternKind.COLORS -> content.colors.filter { it.argb != WHITE }.map { Card.Swatch(it.argb, it.name) }
            // Silent: a TTS voice would read the symbol's Unicode name.
            PatternKind.SHAPES -> SHAPES.map { Card.Text(it, speech = "") }
            PatternKind.PICTURES -> content.words.filter { spec.categories.isEmpty() || it.category in spec.categories }
                .distinctBy { it.emoji }.map { Card.Picture(it.emoji, it.word, it.image) }
            PatternKind.NUMBERS -> emptyList()
        }
        if (spec.kind != PatternKind.NUMBERS && pool.size < maxOf(spec.choices, spec.rules.maxOf { it.toSet().size })) return emptyList()
        return List(count) {
            val rule = spec.rules.random(random)
            if (spec.kind == PatternKind.NUMBERS) numbers(spec, rule, random) else repeating(spec, rule, pool, random)
        }
    }

    private fun repeating(spec: PatternLevel, unit: String, pool: List<Card>, random: Random): Question {
        val letters = unit.toSet().toList()
        val items = pool.shuffled(random).take(letters.size)
        val byLetter = letters.zip(items).toMap()
        val sequence = List(spec.shown + 1) { byLetter.getValue(unit[it % unit.length]) }
        val answer = sequence.last()
        // Wrong choices: the pattern's other items first (the real test), then others.
        val others = (items.filter { it != answer }.shuffled(random) + pool.filter { it !in items }.shuffled(random)).take(spec.choices - 1)
        return Question(
            task = Task.PATTERN,
            prompt = sequence.dropLast(1) + Card.Symbol("?"),
            choices = listOf(answer) + others,
            answer = 0,
        ).shuffled(random)
    }

    private fun numbers(spec: PatternLevel, rule: String, random: Random): Question {
        val n = spec.shown + 1
        val sequence: List<Int> = if (rule == "double") {
            // The loader makes sure max >= 2^shown, so there's always a start.
            val start = random.nextInt(1, (spec.max shr spec.shown) + 1)
            List(n) { start shl it }
        } else {
            val step = spec.steps.random(random)
            val span = kotlin.math.abs(step) * spec.shown
            val start = if (step > 0) random.nextInt(0, spec.max - span + 1) else random.nextInt(span, spec.max + 1)
            List(n) { start + step * it }
        }
        val answer = sequence.last()
        return Question(
            task = Task.PATTERN,
            prompt = sequence.dropLast(1).map(Card::Number) + Card.Symbol("?"),
            choices = MathGame.numberChoices(answer, spec.choices, random).map(Card::Number),
            answer = 0,
        ).shuffled(random)
    }

    private const val WHITE = 0xFFFFFFFFL
}
