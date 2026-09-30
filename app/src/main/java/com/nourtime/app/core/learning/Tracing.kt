package com.nourtime.app.core.learning

import kotlin.math.hypot

/**
 * One stroke of a letter, in 0..1 canvas coordinates: a line to follow from its first point (the
 * start, where the finger goes down) to its last, or a [dot] to tap (the dots of ب ت ث …).
 * [points] are evenly spaced so progress can be counted point by point.
 */
data class TraceStroke(val points: List<Dot>, val dot: Boolean = false) {
    val start: Dot get() = points.first()
}

/** A letter to trace (from `tracing/<language>.json`). [path] is the SVG outline shown as the guide. */
data class TraceLetter(
    override val id: String,
    val letter: String,
    val strokes: List<TraceStroke>,
) : Level

object TraceRules {
    /** Distance between the points of a stroke (0..1 canvas units). */
    const val SPACING = 0.02f

    /** How far ahead of the reached point the finger may be and still count (so it can't jump). */
    const val LOOKAHEAD = 0.14f

    /** Within this of the path is "on the line". Bigger for small children. */
    fun tolerance(young: Boolean): Float = if (young) 0.09f else 0.07f

    /** Off the line by more than this ends the drag as a mistake. */
    fun strayLimit(young: Boolean): Float = tolerance(young) * 1.7f

    /** [points] resampled every [SPACING] along the polyline, first and last kept. */
    fun resample(points: List<Dot>): List<Dot> {
        if (points.size < 2) return points
        val out = mutableListOf(points.first())
        var carry = 0f
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val len = hypot(b.x - a.x, b.y - a.y)
            var t = SPACING - carry
            while (t <= len) {
                out += Dot(a.x + (b.x - a.x) * t / len, a.y + (b.y - a.y) * t / len)
                t += SPACING
            }
            carry = len - (t - SPACING)
        }
        if (out.last() != points.last()) out += points.last()
        return out
    }
}

/**
 * Progress through a letter. The child traces stroke by stroke, in order and in the right direction:
 * the finger goes down near the reached point and moves forward along the line; lifting the finger
 * keeps what was traced; straying off the line counts as a mistake and ends that drag.
 */
data class TraceRound(
    val letter: TraceLetter,
    val stroke: Int = 0,
    /** Index of the last point reached on the current stroke. */
    val reached: Int = 0,
    val mistakes: Int = 0,
    val tutorial: Boolean = false,
) {
    enum class Outcome { MOVED, STRAYED, STROKE_DONE, DONE, IGNORED }

    val done: Boolean get() = stroke >= letter.strokes.size
    val current: TraceStroke? get() = letter.strokes.getOrNull(stroke)

    /** Where the finger should go down now. */
    val here: Dot? get() = current?.points?.get(reached)

    val stars: Int get() = Stars.fromMistakes(mistakes)

    /** True when a finger going down at ([x], [y]) may continue the current stroke. */
    fun canStart(x: Float, y: Float, young: Boolean): Boolean {
        val p = here ?: return false
        return current?.dot == false && hypot(p.x - x, p.y - y) <= TraceRules.tolerance(young) * 1.5f
    }

    /** The finger moved to ([x], [y]) while tracing. */
    fun move(x: Float, y: Float, young: Boolean): Pair<TraceRound, Outcome> {
        val s = current ?: return this to Outcome.IGNORED
        if (s.dot) return this to Outcome.IGNORED
        val window = (TraceRules.LOOKAHEAD / TraceRules.SPACING).toInt()
        val last = minOf(s.points.lastIndex, reached + window)
        // The point nearest the finger, the earlier one on a tie: where a stroke turns back on itself
        // (the top of ج), the part coming back is close too, and must not be skipped to.
        var best = reached
        var nearest = Float.MAX_VALUE
        for (i in reached..last) {
            val d = hypot(s.points[i].x - x, s.points[i].y - y)
            if (d < nearest) {
                nearest = d
                best = i
            }
        }
        if (nearest > TraceRules.strayLimit(young)) return copy(mistakes = if (tutorial) mistakes else mistakes + 1) to Outcome.STRAYED
        if (nearest > TraceRules.tolerance(young) || best <= reached) return this to Outcome.IGNORED
        if (best < s.points.lastIndex) return copy(reached = best) to Outcome.MOVED
        val next = copy(stroke = stroke + 1, reached = 0)
        return next to if (next.done) Outcome.DONE else Outcome.STROKE_DONE
    }

    /** A tap at ([x], [y]): finishes a dot stroke when it's on the dot. */
    fun tap(x: Float, y: Float, young: Boolean): Pair<TraceRound, Outcome> {
        val s = current ?: return this to Outcome.IGNORED
        if (!s.dot) return this to Outcome.IGNORED
        if (hypot(s.start.x - x, s.start.y - y) > TraceRules.tolerance(young) * 1.5f) return this to Outcome.IGNORED
        val next = copy(stroke = stroke + 1, reached = 0)
        return next to if (next.done) Outcome.DONE else Outcome.STROKE_DONE
    }
}
