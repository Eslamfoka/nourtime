package com.nourtime.app.core.learning

import com.nourtime.app.core.learning.content.ContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Letter Tracing (G4). */
class TracingTest {

    /** Traces the whole letter the way a careful child would: every point of every stroke, taps on dots. */
    private fun traceAll(letter: TraceLetter): TraceRound {
        var r = TraceRound(letter)
        while (!r.done) {
            val s = r.current!!
            if (s.dot) {
                r = r.tap(s.start.x, s.start.y, young = false).first
                continue
            }
            assertTrue(letter.id, r.canStart(s.start.x, s.start.y, young = false))
            val stroke = r.stroke
            for (p in s.points.drop(1)) {
                r = r.move(p.x, p.y, young = false).first
                if (r.stroke != stroke) break
            }
            assertTrue("${letter.id} stroke ${stroke + 1} not finished", r.stroke == stroke + 1)
        }
        return r
    }

    @Test
    fun `every shipped letter can be traced to the end without a mistake`() {
        LearnLanguage.entries.forEach { lang ->
            TestContent.tracing(lang).levels.forEach { letter ->
                val r = traceAll(letter)
                assertEquals(letter.id, 0, r.mistakes)
                assertEquals(3, r.stars)
            }
        }
    }

    @Test
    fun `both alphabets are complete`() {
        assertEquals(28, TestContent.tracing(LearnLanguage.ARABIC).levels.size)
        assertEquals(26, TestContent.tracing(LearnLanguage.ENGLISH).levels.size)
        assertEquals(26, TestContent.tracing(LearnLanguage.ENGLISH).levels.map { it.letter }.toSet().size)
        // Level ids are unique across languages too: stars are saved by id.
        val ids = LearnLanguage.entries.flatMap { TestContent.tracing(it).levels.map(TraceLetter::id) }
        assertEquals(ids.size, ids.toSet().size)
        // The first letters are single straight strokes.
        assertEquals(1, TestContent.tracing(LearnLanguage.ARABIC).levels.first().strokes.size)
        assertEquals("L", TestContent.tracing(LearnLanguage.ENGLISH).levels.first().letter)
    }

    @Test
    fun `points are evenly spaced`() {
        val pts = TraceRules.resample(listOf(Dot(0f, 0f), Dot(0.5f, 0f), Dot(0.5f, 0.5f)))
        pts.zipWithNext().forEach { (a, b) -> assertTrue(hypot(b.x - a.x, b.y - a.y) <= TraceRules.SPACING + 1e-4f) }
        assertEquals(Dot(0.5f, 0.5f), pts.last())
        assertTrue(pts.size in 49..52)
    }

    private val line = TraceLetter("t", "I", listOf(TraceStroke(TraceRules.resample(listOf(Dot(0.5f, 0.1f), Dot(0.5f, 0.9f)))), TraceStroke(listOf(Dot(0.2f, 0.5f)), dot = true)))

    @Test
    fun `a stroke must start at its start and can't jump ahead`() {
        val r = TraceRound(line)
        assertTrue(r.canStart(0.5f, 0.12f, young = false))
        assertTrue(!r.canStart(0.5f, 0.9f, young = false)) // the wrong end
        // Jumping to the end along the line isn't progress (too far ahead).
        assertEquals(TraceRound.Outcome.STRAYED, r.move(0.5f, 0.85f, young = false).second)
        // Small steps are.
        val (r2, o2) = r.move(0.5f, 0.2f, young = false)
        assertEquals(TraceRound.Outcome.MOVED, o2)
        assertTrue(r2.reached > 0)
    }

    @Test
    fun `straying off the line is a mistake, lifting the finger keeps the progress`() {
        var r = TraceRound(line)
        r = r.move(0.5f, 0.2f, young = false).first
        val kept = r.reached
        val (strayed, o) = r.move(0.8f, 0.2f, young = false)
        assertEquals(TraceRound.Outcome.STRAYED, o)
        assertEquals(1, strayed.mistakes)
        assertEquals(kept, strayed.reached)
        assertTrue(strayed.canStart(0.5f, 0.2f, young = false))
    }

    @Test
    fun `dots are tapped, not traced, and finish the letter`() {
        var r = TraceRound(line)
        line.strokes[0].points.drop(1).forEach { r = r.move(it.x, it.y, young = false).first }
        assertEquals(1, r.stroke)
        assertEquals(TraceRound.Outcome.IGNORED, r.tap(0.8f, 0.8f, young = false).second)
        val (end, o) = r.tap(0.21f, 0.5f, young = false)
        assertEquals(TraceRound.Outcome.DONE, o)
        assertTrue(end.done)
    }

    @Test
    fun `broken tracing letters are left out`() {
        val problems = mutableListOf<String>()
        val files = mapOf(
            ContentLoader.tracingPath(LearnLanguage.ENGLISH) to """{"schema": 1, "language": "en", "levels": [
                {"id": "ok", "letter": "I", "strokes": [{"path": "M50 10 L50 90"}]},
                {"id": "out", "letter": "I", "strokes": [{"path": "M50 10 L50 190"}]},
                {"id": "two", "letter": "I", "strokes": [{"path": "M50 10 L50 90 M20 10 L20 90"}]},
                {"id": "none", "letter": "I", "strokes": [{}]}
            ]}""",
        )
        val l = ContentLoader({ files[it] }, { problems += it })
        assertEquals(listOf("ok"), l.tracing(LearnLanguage.ENGLISH).levels.map { it.id })
        assertEquals(3, problems.filter { "tracing/en" in it }.size)
    }
}
