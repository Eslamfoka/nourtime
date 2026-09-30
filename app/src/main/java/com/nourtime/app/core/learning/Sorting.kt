package com.nourtime.app.core.learning

import kotlin.random.Random

/**
 * A group in a Sorting level. Pictures go by word [category]; numbers by [parity] ("even" / "odd").
 * [labels] names the group per language tag ("ar", "en"); [emoji] is its sign.
 */
data class SortBinSpec(
    val category: String? = null,
    val parity: String? = null,
    val emoji: String = "",
    val labels: Map<String, String> = emptyMap(),
)

/** One Sorting level (from `sorting/levels.json`): [items] things to put in 2–3 [bins]. */
data class SortLevel(
    override val id: String,
    val bins: List<SortBinSpec>,
    val items: Int = 6,
    /** Numbers go up to this (parity bins). */
    val max: Int = 10,
) : Level

/** A group as shown: its sign and its name in the words language. */
data class SortBin(val emoji: String, val label: String, val parity: String? = null)

/** A thing to sort and the group it belongs to. */
data class SortItem(val card: Card, val bin: Int)

/**
 * Sorting, one thing at a time: put it in its group. The wrong group sends it back and counts as a
 * mistake. Stars from mistakes like the drawing games.
 */
data class SortRound(
    val bins: List<SortBin>,
    val items: List<SortItem>,
    val index: Int = 0,
    val mistakes: Int = 0,
    val tutorial: Boolean = false,
) {
    enum class Outcome { RIGHT, WRONG, DONE, IGNORED }

    val done: Boolean get() = index >= items.size
    val current: SortItem? get() = items.getOrNull(index)
    val stars: Int get() = Stars.fromMistakes(mistakes)

    /** How many things are already in [bin]. */
    fun count(bin: Int): Int = items.take(index).count { it.bin == bin }

    fun drop(bin: Int): Pair<SortRound, Outcome> {
        val item = current ?: return this to Outcome.IGNORED
        if (bin !in bins.indices) return this to Outcome.IGNORED
        if (item.bin != bin) return copy(mistakes = if (tutorial && index == 0) mistakes else mistakes + 1) to Outcome.WRONG
        val next = copy(index = index + 1)
        return next to if (next.done) Outcome.DONE else Outcome.RIGHT
    }
}

object SortGame {
    /** Deals a level in the language of [content]; empty when a group has too few things. */
    fun deal(spec: SortLevel, content: LettersLanguagePack, random: Random): SortRound? {
        val tag = content.language.tag
        val bins = spec.bins.map { SortBin(it.emoji, it.labels[tag] ?: it.labels["en"] ?: "", it.parity) }
        // As even as possible across the groups, then shuffled.
        val per = List(spec.bins.size) { i -> spec.items / spec.bins.size + if (i < spec.items % spec.bins.size) 1 else 0 }
        val used = mutableSetOf<String>()
        val items = spec.bins.flatMapIndexed { i, b ->
            val cards: List<Card> = when {
                b.parity != null -> (1..spec.max).filter { (it % 2 == 0) == (b.parity == "even") }.shuffled(random).take(per[i]).map(Card::Number)
                else -> content.words.filter { it.category == b.category && used.add(it.emoji) }.shuffled(random).take(per[i])
                    .map { Card.Picture(it.emoji, it.word, it.image) }
            }
            if (cards.size < per[i]) return null
            cards.map { SortItem(it, i) }
        }
        return SortRound(bins, items.shuffled(random))
    }
}
