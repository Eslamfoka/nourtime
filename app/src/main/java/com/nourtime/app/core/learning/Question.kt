package com.nourtime.app.core.learning

/**
 * The Learning Hub's mini-games. The name is part of the saved progress key: never rename one.
 * Adding a game: an entry here plus its entry in the hub's game registry (`GameRegistry`).
 */
enum class GameId { MATH, LETTERS, CONNECT, COLORING, LISTEN, PATTERNS, CLOCK, TRACING, MEMORY }

/** Language of a game's content and voice, independent of the app language. */
enum class LearnLanguage(val tag: String) { ARABIC("ar"), ENGLISH("en") }

/** 123 or ١٢٣. */
enum class NumeralStyle {
    WESTERN, EASTERN;

    fun format(n: Int): String {
        val western = n.toString()
        if (this == WESTERN) return western
        return buildString { western.forEach { append(if (it in '0'..'9') '٠' + (it - '0') else it) } }
    }
}

/**
 * How an equation is written. With Eastern numerals (١٢٣) it follows Arabic schoolbooks: right to
 * left, the first number on the right. A relation sign always opens toward the bigger number, so in
 * right-to-left order "less than" is drawn as ">" (the same mirroring Unicode applies to < and >).
 */
object MathWriting {
    fun rightToLeft(numerals: NumeralStyle): Boolean = numerals == NumeralStyle.EASTERN

    /**
     * Font size (sp) for an equation's cards, so a long one ("999 + 999 = ?") still fits on one line
     * of a narrow phone.
     */
    fun promptSize(prompt: List<Card>): Int {
        val chars = prompt.sumOf { if (it is Card.Number) it.value.toString().length else 1 }
        return when {
            chars <= 7 -> 52
            chars <= 9 -> 44
            chars <= 11 -> 36
            else -> 30
        }
    }

    /** The glyph to draw for a symbol card; the question itself keeps the logical symbol. */
    fun glyph(symbol: String, rightToLeft: Boolean): String = if (!rightToLeft) symbol else when (symbol) {
        "<" -> ">"
        ">" -> "<"
        "?" -> "؟"
        else -> symbol
    }
}

/** One thing on a question card: in the prompt or as a choice. */
sealed interface Card {
    data class Number(val value: Int) : Card

    /** An operator or relation: + − × ÷ = < > ?, in logical form; [MathWriting] decides the glyph. */
    data class Symbol(val text: String) : Card

    /** A letter or a word, spoken as [speech] (a letter's name) or as itself. */
    data class Text(val text: String, val speech: String = text) : Card

    /** A picture: the illustration [image] (asset id) when there is one, otherwise the [emoji]. */
    data class Picture(val emoji: String, val word: String, val image: String? = null) : Card

    data class Swatch(val argb: Long, val name: String) : Card

    /**
     * Something to listen to: a big speaker button that says [speech]. [text] is shown only when the
     * phone has no voice for the language (then the child reads instead); [number] is shown in the
     * child's numerals instead of [text] when it's set.
     */
    data class Sound(val speech: String, val text: String, val number: Int? = null) : Card

    /** An analog clock showing [hour] (1–12) and [minute]. */
    data class Clock(val hour: Int, val minute: Int) : Card

    /** A written time "h:mm" in the child's numerals. */
    data class Time(val hour: Int, val minute: Int) : Card

    /** [count] dots to count (Memory Match: a number and its dots). */
    data class Dots(val count: Int) : Card
}

/** What the child is asked to do; the UI shows it as a short instruction. */
enum class Task {
    SOLVE, COMPARE, LETTER_TO_PICTURE, LETTER_TO_WORD, WORD_TO_PICTURE, NAME_TO_COLOR, COLOR_TO_NAME,

    /** See a picture, pick its written word (reading; nothing is read aloud). */
    PICTURE_TO_WORD,

    /** Listen & Find: hear a word, a color, a letter's name or a number and tap it. */
    LISTEN_TO_PICTURE, LISTEN_TO_COLOR, LISTEN_TO_LETTER, LISTEN_TO_NUMBER,

    /** What Comes Next?: continue a sequence. */
    PATTERN,

    /** Tell the Time: read a clock and pick the time, or read a time and pick the clock. */
    CLOCK_TO_TIME, TIME_TO_CLOCK,
    ;

    val listening: Boolean get() = this == LISTEN_TO_PICTURE || this == LISTEN_TO_COLOR || this == LISTEN_TO_LETTER || this == LISTEN_TO_NUMBER
}

data class Speech(val text: String, val language: LearnLanguage)

/**
 * A multiple-choice question. [say] is read aloud when the question appears (young children) and
 * when the child taps the prompt. [dots] asks the UI to draw each number in the prompt as dots too.
 */
data class Question(
    val task: Task,
    val prompt: List<Card>,
    val choices: List<Card>,
    val answer: Int,
    val say: Speech? = null,
    val dots: Boolean = false,
) {
    init {
        require(answer in choices.indices) { "answer out of range" }
        require(choices.distinct().size == choices.size) { "duplicate choices" }
    }
}
