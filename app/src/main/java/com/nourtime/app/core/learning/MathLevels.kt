package com.nourtime.app.core.learning

import kotlin.random.Random

/** [key] is the name used in content files. */
enum class MathOp(val symbol: String, val key: String) { ADD("+", "add"), SUB("−", "sub"), MUL("×", "mul"), DIV("÷", "div"), COMPARE("?", "compare") }

/**
 * One Smart Math level (from `math/levels.json`). [max] bounds every number in +, − and comparisons;
 * [factor] bounds the factors of × and the divisor and quotient of ÷ (with [minFactor] as the
 * smallest first factor).
 */
data class MathLevel(
    override val id: String,
    val ops: List<MathOp>,
    val max: Int = 10,
    val minFactor: Int = 1,
    val factor: Int = 10,
    val dots: Boolean = false,
    val choices: Int = 4,
    val questions: Int = MathGame.QUESTIONS,
) : Level

/** Makes Smart Math questions. Numbers are generated, so one level spec gives endless questions. */
object MathGame {
    const val QUESTIONS = 6

    /** The questions of [spec]; the same seed gives the same questions. */
    fun questions(spec: MathLevel, random: Random, count: Int = spec.questions): List<Question> {
        val seen = mutableSetOf<List<Card>>()
        val out = mutableListOf<Question>()
        var tries = 0
        while (out.size < count) {
            val q = question(spec, random)
            // Tiny levels have few distinct questions; allow repeats rather than loop forever.
            if (seen.add(q.prompt) || ++tries > 50) out += q
        }
        return out
    }

    fun question(spec: MathLevel, random: Random): Question = when (val op = spec.ops.random(random)) {
        MathOp.COMPARE -> compare(spec, random)
        else -> {
            val (a, b, answer) = operands(op, spec, random)
            Question(
                task = Task.SOLVE,
                prompt = listOf(Card.Number(a), Card.Symbol(op.symbol), Card.Number(b), Card.Symbol("="), Card.Symbol("?")),
                choices = numberChoices(answer, spec.choices, random).map(Card::Number),
                answer = 0,
                dots = spec.dots,
            ).shuffled(random)
        }
    }

    private fun operands(op: MathOp, spec: MathLevel, random: Random): Triple<Int, Int, Int> = when (op) {
        MathOp.ADD -> {
            val a = random.nextInt(1, spec.max)
            val b = random.nextInt(1, spec.max - a + 1)
            Triple(a, b, a + b)
        }
        MathOp.SUB -> {
            val a = random.nextInt(2, spec.max + 1)
            val b = random.nextInt(1, a)
            Triple(a, b, a - b)
        }
        MathOp.MUL -> {
            val a = random.nextInt(spec.minFactor, spec.factor + 1)
            val b = random.nextInt(1, 11)
            Triple(a, b, a * b)
        }
        MathOp.DIV -> {
            val divisor = random.nextInt(2, spec.factor + 1)
            val quotient = random.nextInt(1, spec.factor + 1)
            Triple(divisor * quotient, divisor, quotient)
        }
        MathOp.COMPARE -> error("not an operation")
    }

    private fun compare(spec: MathLevel, random: Random): Question {
        val a = random.nextInt(0, spec.max + 1)
        // Equal about one time in five, so "=" is a real option.
        val b = if (random.nextInt(5) == 0) a else generateSequence { random.nextInt(0, spec.max + 1) }.first { it != a }
        val symbols = listOf("<", "=", ">")
        val answer = when {
            a < b -> 0
            a == b -> 1
            else -> 2
        }
        return Question(
            task = Task.COMPARE,
            prompt = listOf(Card.Number(a), Card.Symbol("?"), Card.Number(b)),
            choices = symbols.map(Card::Symbol),
            answer = answer,
        )
    }

    /** The answer first, then distinct nearby wrong answers (never negative). */
    fun numberChoices(answer: Int, count: Int, random: Random): List<Int> {
        val spread = maxOf(3, count + 1)
        val out = linkedSetOf(answer)
        while (out.size < count) {
            val d = random.nextInt(1, spread + 1) * if (random.nextBoolean()) 1 else -1
            val candidate = answer + d
            if (candidate >= 0) out += candidate
        }
        return out.toList()
    }
}

/** The same question with its choices in random order. */
fun Question.shuffled(random: Random): Question {
    val order = choices.indices.shuffled(random)
    return copy(choices = order.map { choices[it] }, answer = order.indexOf(answer))
}
