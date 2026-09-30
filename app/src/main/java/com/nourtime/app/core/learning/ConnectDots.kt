package com.nourtime.app.core.learning

import kotlin.math.hypot

/**
 * One dot of a Number Connect drawing, in 0..1 coordinates of a square canvas. The segment that
 * arrives at this dot from the previous one is straight, or a curve bent toward ([cx], [cy]).
 */
data class Dot(val x: Float, val y: Float, val cx: Float? = null, val cy: Float? = null) {
    val curved: Boolean get() = cx != null && cy != null
}

/** A drawing to connect. [closed]: the last dot connects back to the first. */
data class DotShape(val name: String, val emoji: String, val dots: List<Dot>, val closed: Boolean = true) {
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

object ConnectLevels {
    /**
     * Each level is one drawing; they grow in dots, then add curves. A dot's control point bends the
     * segment that arrives at it (for dot 0: the closing segment).
     */
    val all: List<DotShape> = listOf(
        DotShape("triangle", "🔺", listOf(Dot(0.5f, 0.15f), Dot(0.85f, 0.8f), Dot(0.15f, 0.8f))),
        DotShape(
            "house", "🏠",
            listOf(Dot(0.2f, 0.85f), Dot(0.2f, 0.45f), Dot(0.5f, 0.15f), Dot(0.8f, 0.45f), Dot(0.8f, 0.85f)),
        ),
        DotShape(
            "fish", "🐟",
            listOf(
                Dot(0.1f, 0.5f, cx = 0.38f, cy = 0.9f),
                Dot(0.68f, 0.42f, cx = 0.38f, cy = 0.1f),
                Dot(0.9f, 0.22f),
                Dot(0.9f, 0.78f),
                Dot(0.68f, 0.58f),
            ),
        ),
        DotShape(
            "moon", "🌙",
            listOf(
                Dot(0.62f, 0.1f, cx = 0.3f, cy = 0.5f),
                Dot(0.15f, 0.5f, cx = 0.2f, cy = 0.12f),
                Dot(0.62f, 0.9f, cx = 0.2f, cy = 0.88f),
            ),
        ),
        DotShape(
            "heart", "❤️",
            listOf(
                Dot(0.5f, 0.3f, cx = 0.22f, cy = 0.02f),
                Dot(0.9f, 0.35f, cx = 0.78f, cy = 0.02f),
                Dot(0.5f, 0.88f, cx = 0.9f, cy = 0.62f),
                Dot(0.1f, 0.35f, cx = 0.1f, cy = 0.62f),
            ),
        ),
        DotShape(
            "star", "⭐",
            listOf(
                Dot(0.5f, 0.08f), Dot(0.61f, 0.38f), Dot(0.93f, 0.38f), Dot(0.67f, 0.57f), Dot(0.77f, 0.9f),
                Dot(0.5f, 0.7f), Dot(0.23f, 0.9f), Dot(0.33f, 0.57f), Dot(0.07f, 0.38f), Dot(0.39f, 0.38f),
            ),
        ),
        DotShape(
            "boat", "⛵",
            listOf(
                Dot(0.1f, 0.62f), Dot(0.25f, 0.85f), Dot(0.75f, 0.85f), Dot(0.9f, 0.62f), Dot(0.52f, 0.62f),
                Dot(0.52f, 0.1f), Dot(0.85f, 0.45f), Dot(0.52f, 0.45f),
            ),
            closed = false,
        ),
        DotShape(
            "cat", "🐱",
            listOf(
                Dot(0.2f, 0.12f), Dot(0.38f, 0.3f), Dot(0.62f, 0.3f), Dot(0.8f, 0.12f), Dot(0.82f, 0.5f),
                Dot(0.5f, 0.88f, cx = 0.82f, cy = 0.88f),
                Dot(0.18f, 0.5f, cx = 0.18f, cy = 0.88f),
            ),
        ),
    )
}
