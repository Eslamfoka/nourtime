package com.nourtime.app.core.learning

import kotlin.random.Random

enum class MathOp(val symbol: String) { ADD("+"), SUB("−"), MUL("×"), DIV("÷"), COMPARE("?") }

/**
 * One Smart Math level. [max] bounds every number in +, − and comparisons; [factor] bounds the
 * factors of × and the divisor and quotient of ÷ (with [minFactor] as the smallest first factor).
 */
data class MathLevel(
    val ops: List<MathOp>,
    val max: Int = 10,
    val minFactor: Int = 1,
    val factor: Int = 10,
    val dots: Boolean = false,
    val choices: Int = 4,
)

object MathLevels {
    const val QUESTIONS = 6

    val all: List<MathLevel> = listOf(
        MathLevel(listOf(MathOp.ADD), max = 5, dots = true, choices = 3),
        MathLevel(listOf(MathOp.ADD), max = 10, choices = 3),
        MathLevel(listOf(MathOp.SUB), max = 10, choices = 3),
        MathLevel(listOf(MathOp.COMPARE), max = 20),
        MathLevel(listOf(MathOp.ADD), max = 20),
        MathLevel(listOf(MathOp.SUB), max = 20),
        MathLevel(listOf(MathOp.ADD, MathOp.SUB), max = 100),
        MathLevel(listOf(MathOp.MUL), minFactor = 2, factor = 5),
        MathLevel(listOf(MathOp.MUL), minFactor = 2, factor = 10),
        MathLevel(listOf(MathOp.DIV), factor = 10),
        MathLevel(listOf(MathOp.COMPARE), max = 100),
        MathLevel(listOf(MathOp.ADD, MathOp.SUB, MathOp.MUL, MathOp.DIV, MathOp.COMPARE), max = 100, minFactor = 2, factor = 10),
    )

    /** The questions of level [level] (0-based); the same seed gives the same questions. */
    fun questions(level: Int, random: Random, count: Int = QUESTIONS): List<Question> {
        val spec = all[level.coerceIn(all.indices)]
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
