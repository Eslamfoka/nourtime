package com.nourtime.app.core.learning

import com.nourtime.app.core.learning.content.ContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Little Shop (G8). */
class ShopTest {

    @Test
    fun `every level deals in both languages and every amount can be paid with its coins`() {
        LearnLanguage.entries.forEach { lang ->
            val pack = TestContent.letters(lang)
            TestContent.shop.levels.forEach { spec ->
                repeat(20) { seed ->
                    val dealt = ShopGame.deal(spec, pack, Random(seed))
                    assertNotNull("${lang.tag} ${spec.id}", dealt)
                    var r = dealt!!
                    assertEquals(spec.items, r.tasks.size)
                    r.tasks.forEach { t ->
                        assertTrue(t.price in spec.minPrice..spec.maxPrice)
                        assertEquals(if (t.change) spec.paid - t.price else t.price, t.target)
                        assertTrue(t.target > 0)
                    }
                    // Paying each with the fewest coins finishes the level without a mistake.
                    while (!r.done) {
                        val coins = ShopGame.fewestCoins(r.current!!.target, r.coins)
                        assertTrue(coins.isNotEmpty())
                        coins.forEach { r = r.add(it).first }
                    }
                    assertEquals(0, r.mistakes)
                }
            }
        }
    }

    private val apple = WordEntry("apple", "Apple", "🍎", "food")

    @Test
    fun `going over is a mistake and the coins come back, a coin can be taken back`() {
        var r = ShopRound(listOf(ShopTask(apple, 7, null, 7), ShopTask(apple, 3, 10, 7)), listOf(1, 2, 5))
        r = r.add(5).first
        val (over, o) = r.add(5)
        assertEquals(ShopRound.Outcome.OVER, o)
        assertEquals(1, over.mistakes)
        assertEquals(0, over.total)
        r = over.add(5).first.add(1).first
        assertEquals(6, r.total)
        r = r.remove(1)
        assertEquals(5, r.total)
        val (paid, o2) = r.add(2)
        assertEquals(ShopRound.Outcome.PAID, o2)
        assertEquals(1, paid.index)
        assertEquals(ShopRound.Outcome.IGNORED, paid.add(20).second) // not in the purse
        assertEquals(ShopRound.Outcome.DONE, paid.add(5).first.add(2).second)
    }

    @Test
    fun `the fewest coins are found`() {
        assertEquals(listOf(50, 20, 5, 2, 2), ShopGame.fewestCoins(79, listOf(1, 2, 5, 10, 20, 50)))
    }

    @Test
    fun `broken shop levels are left out`() {
        val problems = mutableListOf<String>()
        val files = mapOf(
            ContentLoader.SHOP to """{"schema": 1, "levels": [
                {"id": "ok", "coins": [1, 2, 5]},
                {"id": "no-one", "coins": [2, 5]},
                {"id": "paid", "coins": [1], "modes": ["change"], "maxPrice": 10, "paid": 10},
                {"id": "mode", "coins": [1], "modes": ["steal"]}
            ]}""",
        )
        val l = ContentLoader({ files[it] }, { problems += it })
        assertEquals(listOf("ok"), l.shop().levels.map { it.id })
        assertEquals(3, problems.size)
    }
}
