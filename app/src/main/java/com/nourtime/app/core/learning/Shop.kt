package com.nourtime.app.core.learning

import kotlin.random.Random

/**
 * One Little Shop level (from `shop/levels.json`). [coins] are the coin values in the purse (as many
 * of each as needed). `pay`: put together a price between [minPrice] and [maxPrice]. `change`: the
 * child paid [paid] (a note) for that price and gives the change back.
 */
data class ShopLevel(
    override val id: String,
    val coins: List<Int>,
    val modes: List<String> = listOf("pay"),
    val minPrice: Int = 1,
    val maxPrice: Int = 10,
    val paid: Int = 10,
    val items: Int = 4,
    val categories: Set<String> = emptySet(),
) : Level

/** One thing to buy: [target] is what the coins must add up to (the price, or the change). */
data class ShopTask(val item: WordEntry, val price: Int, val paid: Int?, val target: Int) {
    val change: Boolean get() = paid != null
}

/**
 * Buying, one thing at a time: coins go on the counter; exactly the target finishes the thing, going
 * over is a mistake and the coins come back. A coin on the counter can be taken back freely.
 */
data class ShopRound(
    val tasks: List<ShopTask>,
    val coins: List<Int>,
    val index: Int = 0,
    val counter: List<Int> = emptyList(),
    val mistakes: Int = 0,
    val tutorial: Boolean = false,
) {
    enum class Outcome { ADDED, OVER, PAID, DONE, IGNORED }

    val done: Boolean get() = index >= tasks.size
    val current: ShopTask? get() = tasks.getOrNull(index)
    val total: Int get() = counter.sum()
    val stars: Int get() = Stars.fromMistakes(mistakes)

    fun add(coin: Int): Pair<ShopRound, Outcome> {
        val task = current ?: return this to Outcome.IGNORED
        if (coin !in coins) return this to Outcome.IGNORED
        val now = counter + coin
        return when {
            now.sum() < task.target -> copy(counter = now) to Outcome.ADDED
            now.sum() > task.target -> copy(counter = emptyList(), mistakes = if (tutorial && index == 0) mistakes else mistakes + 1) to Outcome.OVER
            else -> copy(index = index + 1, counter = emptyList()).let { it to if (it.done) Outcome.DONE else Outcome.PAID }
        }
    }

    /** Takes coin [i] back from the counter. */
    fun remove(i: Int): ShopRound = if (i in counter.indices) copy(counter = counter.filterIndexed { j, _ -> j != i }) else this
}

object ShopGame {
    const val PAY = "pay"
    const val CHANGE = "change"

    /** Deals a level; null when the words language has too few things to sell. */
    fun deal(spec: ShopLevel, content: LettersLanguagePack, random: Random): ShopRound? {
        val items = content.words.filter { spec.categories.isEmpty() || it.category in spec.categories }.shuffled(random).take(spec.items)
        if (items.size < spec.items) return null
        val tasks = items.mapIndexed { i, item ->
            val price = random.nextInt(spec.minPrice, spec.maxPrice + 1)
            if (spec.modes[i % spec.modes.size] == CHANGE) ShopTask(item, price, spec.paid, spec.paid - price) else ShopTask(item, price, null, price)
        }
        return ShopRound(tasks, spec.coins.sorted())
    }

    /** Fewest coins that make [amount] (greedy works for these coin sets); for a hint and the tests. */
    fun fewestCoins(amount: Int, coins: List<Int>): List<Int> {
        var left = amount
        val out = mutableListOf<Int>()
        for (c in coins.sortedDescending()) while (left >= c) {
            out += c
            left -= c
        }
        return if (left == 0) out else emptyList()
    }
}
