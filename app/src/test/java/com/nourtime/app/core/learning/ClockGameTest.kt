package com.nourtime.app.core.learning

import com.nourtime.app.core.learning.content.ContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Tell the Time (G3). */
class ClockGameTest {

    private fun time(c: Card): Pair<Int, Int> = when (c) {
        is Card.Clock -> c.hour to c.minute
        is Card.Time -> c.hour to c.minute
        else -> error("not a time: $c")
    }

    @Test
    fun `every clock question has one right time among distinct valid times`() {
        TestContent.clock.levels.forEach { spec ->
            repeat(40) { seed ->
                val qs = ClockGame.questions(spec, Random(seed))
                assertEquals(spec.questions, qs.size)
                qs.forEach { q ->
                    val asked = time(q.prompt.single())
                    assertEquals(asked, time(q.choices[q.answer]))
                    assertEquals(spec.choices, q.choices.map(::time).toSet().size)
                    q.choices.map(::time).forEach { (h, m) -> assertTrue("$h:$m", h in 1..12 && m in 0..59) }
                    assertTrue(asked.second in spec.precision.minutes)
                    when (q.task) {
                        Task.CLOCK_TO_TIME -> {
                            assertTrue(q.prompt.single() is Card.Clock)
                            assertTrue(q.choices.all { it is Card.Time })
                        }
                        Task.TIME_TO_CLOCK -> {
                            assertTrue(q.prompt.single() is Card.Time)
                            assertTrue(q.choices.all { it is Card.Clock })
                        }
                        else -> error("unexpected ${q.task}")
                    }
                }
            }
        }
    }

    @Test
    fun `wrong times include the classic mix-up of the two hands`() {
        val spec = ClockLevel("x", listOf(Task.CLOCK_TO_TIME), ClockPrecision.QUARTER, choices = 4)
        val seen = (0 until 50).flatMap { ClockGame.wrongTimes(3, 30, spec, Random(it)) }.toSet()
        assertTrue(6 to 15 in seen) // the long hand read as the hour
        assertTrue(4 to 30 in seen || 2 to 30 in seen)
    }

    @Test
    fun `the pack goes from o'clock with two choices to any minute`() {
        val levels = TestContent.clock.levels
        assertTrue(levels.size >= 15)
        assertEquals(ClockPrecision.HOUR, levels.first().precision)
        assertEquals(2, levels.first().choices)
        assertEquals(ClockPrecision.MINUTE, levels.last().precision)
        // Precision never gets easier from one level to the next.
        levels.zipWithNext().forEach { (a, b) -> assertTrue("${a.id} -> ${b.id}", b.precision >= a.precision) }
    }

    @Test
    fun `broken clock levels are left out`() {
        val problems = mutableListOf<String>()
        val files = mapOf(
            ContentLoader.CLOCK to """{"schema": 1, "levels": [
                {"id": "ok", "tasks": ["clock_to_time"], "precision": "half"},
                {"id": "p", "tasks": ["clock_to_time"], "precision": "seconds"},
                {"id": "t", "tasks": ["solve"], "precision": "half"}
            ]}""",
        )
        val l = ContentLoader({ files[it] }, { problems += it })
        assertEquals(listOf("ok"), l.clock().levels.map { it.id })
        assertEquals(2, problems.size)
    }
}
