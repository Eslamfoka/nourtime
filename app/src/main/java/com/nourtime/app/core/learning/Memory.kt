package com.nourtime.app.core.learning

import kotlin.random.Random

/** What the two cards of a Memory Match pair show. [key] is the name used in content files. */
enum class PairKind(val key: String) {
    /** The same picture twice. */
    SAME_PICTURE("same_picture"),

    /** A word and its picture. */
    WORD_PICTURE("word_picture"),

    /** A letter and a picture whose word starts with it. */
    LETTER_PICTURE("letter_picture"),

    /** A number and that many dots. */
    NUMBER_DOTS("number_dots"),

    /** A color and its name. */
    COLOR_NAME("color_name"),
}

/**
 * One Memory Match level (from `memory/levels.json`): [pairs] pairs on the table, of [kinds]
 * (mixed when several), pictures from [categories] (empty = all), numbers up to [maxNumber].
 */
data class MemoryLevel(
    override val id: String,
    val pairs: Int,
    val kinds: List<PairKind>,
    val categories: Set<String> = emptySet(),
    val maxNumber: Int = 10,
) : Level

/** One card on the table: [face] is what it shows when turned; cards with the same [pair] match. */
data class MemoryCard(val pair: Int, val face: Card)

/**
 * A game of Memory Match. Turn two cards: a pair stays up, otherwise both turn back after a moment
 * ([hide], called by the screen). Every two turned cards are one move; stars come from moves.
 */
data class MemoryRound(
    val cards: List<MemoryCard>,
    val up: List<Int> = emptyList(),
    val matched: Set<Int> = emptySet(),
    val moves: Int = 0,
    val tutorial: Boolean = false,
) {
    enum class Outcome { TURNED, MATCH, MISMATCH, DONE, IGNORED }

    val pairs: Int get() = cards.size / 2
    val done: Boolean get() = matched.size == cards.size

    /** Two cards up that don't match: waiting for [hide]. */
    val waiting: Boolean get() = up.size == 2

    fun isUp(i: Int): Boolean = i in matched || i in up

    fun turn(i: Int): Pair<MemoryRound, Outcome> {
        if (done || waiting || i !in cards.indices || isUp(i)) return this to Outcome.IGNORED
        if (up.isEmpty()) return copy(up = listOf(i)) to Outcome.TURNED
        val first = up.single()
        val moved = copy(moves = moves + 1)
        return if (cards[first].pair == cards[i].pair) {
            val next = moved.copy(up = emptyList(), matched = matched + first + i)
            next to if (next.done) Outcome.DONE else Outcome.MATCH
        } else {
            moved.copy(up = listOf(first, i)) to Outcome.MISMATCH
        }
    }

    /** Turns a mismatched pair back. */
    fun hide(): MemoryRound = if (waiting) copy(up = emptyList()) else this

    /** 3 stars within 1.5 moves a pair (a lucky child needs exactly one), 2 within 2.5. */
    val stars: Int get() = when {
        moves * 2 <= pairs * 3 -> 3
        moves * 2 <= pairs * 5 -> 2
        else -> 1
    }
}

object MemoryGame {
    /** Columns of the table for [cards] cards: rows of 2, 3 or 4. */
    fun columns(cards: Int): Int = when {
        cards <= 4 -> 2
        cards <= 6 -> 3
        else -> 4
    }

    /** Deals a level: [MemoryLevel.pairs] different pairs, shuffled. Empty when the pack is too small. */
    fun deal(spec: MemoryLevel, content: LettersLanguagePack, random: Random): List<MemoryCard> {
        val words = content.words.filter { spec.categories.isEmpty() || it.category in spec.categories }.distinctBy { it.emoji }.shuffled(random)
        val letters = content.letters.shuffled(random)
        val colors = content.colors.shuffled(random)
        val numbers = (1..spec.maxNumber).shuffled(random)
        val used = mutableSetOf<Any>()
        val cards = mutableListOf<MemoryCard>()
        var w = 0
        var l = 0
        var c = 0
        var n = 0
        for (pair in 0 until spec.pairs) {
            val kind = spec.kinds[pair % spec.kinds.size]
            val faces: Pair<Card, Card> = when (kind) {
                PairKind.SAME_PICTURE, PairKind.WORD_PICTURE -> {
                    // A picture already on the table (as a word or a picture) can't come twice.
                    // Two cards reading the same ("Orange" the fruit and the color) would be two answers.
                    val e = generateSequence { words.getOrNull(w++) }.firstOrNull { it.emoji !in used && "text:${it.word}" !in used }
                        ?.also { used += it.emoji; used += "text:${it.word}" } ?: return emptyList()
                    val picture = Card.Picture(e.emoji, e.word, e.image)
                    picture to if (kind == PairKind.SAME_PICTURE) picture.copy() else Card.Text(e.word)
                }
                PairKind.LETTER_PICTURE -> {
                    val e = generateSequence { letters.getOrNull(l++) }.firstOrNull { it.emoji !in used }?.also { used += it.emoji } ?: return emptyList()
                    Card.Text(e.letter, speech = e.name) to Card.Picture(e.emoji, e.word, e.image)
                }
                PairKind.NUMBER_DOTS -> {
                    val v = generateSequence { numbers.getOrNull(n++) }.firstOrNull { used.add("n$it") } ?: return emptyList()
                    Card.Number(v) to Card.Dots(v)
                }
                PairKind.COLOR_NAME -> {
                    val e = generateSequence { colors.getOrNull(c++) }.firstOrNull { it.argb !in used && "text:${it.name}" !in used }
                        ?.also { used += it.argb; used += "text:${it.name}" } ?: return emptyList()
                    Card.Swatch(e.argb, e.name) to Card.Text(e.name)
                }
            }
            cards += MemoryCard(pair, faces.first)
            cards += MemoryCard(pair, faces.second)
        }
        return cards.shuffled(random)
    }
}
