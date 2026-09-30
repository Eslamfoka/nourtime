package com.nourtime.app.core.learning

import kotlin.math.hypot

/**
 * One dot of a Number Connect drawing, in 0..1 coordinates of a square canvas. The segment that
 * arrives at this dot from the previous one is straight, or a curve bent toward ([cx], [cy]).
 */
data class Dot(val x: Float, val y: Float, val cx: Float? = null, val cy: Float? = null) {
    val curved: Boolean get() = cx != null && cy != null
}

/**
 * A drawing to connect (from `connect/shapes.json`). [closed]: the last dot connects back to the
 * first. [image] is an illustration shown when the drawing is complete (optional).
 */
data class DotShape(
    override val id: String,
    val emoji: String,
    val dots: List<Dot>,
    val closed: Boolean = true,
    val image: String? = null,
) : Level {
    /** Segments to draw: into dot 1, 2, … and, when closed, back into dot 0. */
    val segmentCount: Int get() = if (closed) dots.size else dots.size - 1

    /** The dot a segment ends at. */
    fun target(segment: Int): Int = (segment + 1) % dots.size
}

/**
 * Progress through one drawing. The child drags from the last reached dot to the next number;
 * letting go near another dot is a mistake (the line snaps back).
 */
data class ConnectRound(val shape: DotShape, val drawn: Int = 0, val mistakes: Int = 0, val tutorial: Boolean = false) {
    val done: Boolean get() = drawn >= shape.segmentCount

    /** The dot the line starts from now. */
    val from: Int get() = drawn % shape.dots.size

    /** The dot to reach next. */
    val next: Int get() = shape.target(drawn)

    val stars: Int get() = Stars.fromMistakes(mistakes)

    /** The finger reached [dot] while drawing: the next dot draws the segment, others do nothing yet. */
    fun reach(dot: Int): ConnectRound = if (!done && dot == next) copy(drawn = drawn + 1) else this

    /** The finger let go near [dot] (or nowhere, null). A wrong numbered dot counts as a mistake. */
    fun release(dot: Int?): ConnectRound =
        if (done || dot == null || dot == from || dot == next || tutorial) this else copy(mistakes = mistakes + 1)
}

object ConnectRules {
    /**
     * True when the finger's move from ([ax], [ay]) to ([bx], [by]) passed within [radius] of [dot].
     * Touch events come in steps, so a quick swipe can jump over a dot between two of them.
     */
    fun passesNear(dot: Dot, ax: Float, ay: Float, bx: Float, by: Float, radius: Float): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((dot.x - ax) * dx + (dot.y - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(ax + t * dx - dot.x, ay + t * dy - dot.y) <= radius
    }

    /** True when ([x], [y]) is within [radius] of [dot]. */
    fun near(dot: Dot, x: Float, y: Float, radius: Float): Boolean = hypot(dot.x - x, dot.y - y) <= radius

    /** The dot nearest to ([x], [y]) within [radius] (all in 0..1 canvas units), or null. */
    fun dotAt(dots: List<Dot>, x: Float, y: Float, radius: Float): Int? =
        dots.indices
            .map { it to hypot(dots[it].x - x, dots[it].y - y) }
            .filter { it.second <= radius }
            .minByOrNull { it.second }
            ?.first

    /** Touch radius: bigger for small children. */
    fun radiusFor(young: Boolean): Float = if (young) 0.09f else 0.065f
}
