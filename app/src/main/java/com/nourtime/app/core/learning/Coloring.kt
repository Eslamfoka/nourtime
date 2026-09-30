package com.nourtime.app.core.learning

/** A closed area of a coloring picture, in 0..1 coordinates of a square canvas. */
sealed interface Area {
    fun contains(x: Float, y: Float): Boolean

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) : Area {
        override fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    }

    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : Area {
        override fun contains(x: Float, y: Float): Boolean {
            val dx = (x - cx) / rx
            val dy = (y - cy) / ry
            return dx * dx + dy * dy <= 1f
        }
    }

    /** A polygon; points as x0, y0, x1, y1, … */
    class Poly(vararg val xy: Float) : Area {
        init {
            require(xy.size >= 6 && xy.size % 2 == 0)
        }

        val points: List<Pair<Float, Float>> get() = xy.toList().chunked(2) { it[0] to it[1] }

        /** Even-odd ray casting. */
        override fun contains(x: Float, y: Float): Boolean {
            val pts = points
            var inside = false
            var j = pts.size - 1
            for (i in pts.indices) {
                val (xi, yi) = pts[i]
                val (xj, yj) = pts[j]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            return inside
        }

        override fun equals(other: Any?) = other is Poly && other.xy.contentEquals(xy)
        override fun hashCode() = xy.contentHashCode()
    }
}

/** An area and the color (index into [ColoringPicture.colors]) it has in the reference. */
data class Region(val area: Area, val color: Int)

/** Regions are drawn in order; a later region sits on top of earlier ones. */
data class ColoringPicture(val name: String, val emoji: String, val colors: List<Long>, val regions: List<Region>) {
    init {
        require(regions.all { it.color in colors.indices })
    }
}

/**
 * One picture being colored. [palette] is what the child can pick from: the picture's colors plus
 * distractors. Filling a region with a color it doesn't have in the reference is a mistake, but the
 * child can fill it again.
 */
data class ColoringRound(
    val picture: ColoringPicture,
    val palette: List<Long>,
    val fills: List<Long?> = List(picture.regions.size) { null },
    val selected: Long? = null,
    val mistakes: Int = 0,
    val tutorial: Boolean = false,
) {
    val done: Boolean get() = picture.regions.indices.all { isRight(it) }

    val stars: Int get() = Stars.fromMistakes(mistakes)

    fun isRight(region: Int): Boolean = fills[region] == picture.colors[picture.regions[region].color]

    fun target(region: Int): Long = picture.colors[picture.regions[region].color]

    /** First region still wrong or empty: what the tutorial points at. */
    val hintRegion: Int? get() = picture.regions.indices.firstOrNull { !isRight(it) }

    fun select(color: Long): ColoringRound = if (color in palette) copy(selected = color) else this

    enum class Outcome { RIGHT, WRONG, IGNORED }

    fun fill(region: Int): Pair<ColoringRound, Outcome> {
        val color = selected ?: return this to Outcome.IGNORED
        if (region !in fills.indices || done || fills[region] == color) return this to Outcome.IGNORED
        val next = copy(fills = fills.toMutableList().also { it[region] = color })
        return if (color == target(region)) {
            next to Outcome.RIGHT
        } else {
            next.copy(mistakes = mistakes + if (tutorial) 0 else 1) to Outcome.WRONG
        }
    }
}

object ColoringRules {
    /** The topmost region at ([x], [y]), or null outside the picture. */
    fun regionAt(picture: ColoringPicture, x: Float, y: Float): Int? =
        picture.regions.indices.lastOrNull { picture.regions[it].area.contains(x, y) }
}

object ColoringLevels {
    const val RED = 0xFFE53935
    const val ORANGE = 0xFFFB8C00
    const val YELLOW = 0xFFFDD835
    const val GREEN = 0xFF43A047
    const val BLUE = 0xFF1E88E5
    const val PURPLE = 0xFF8E24AA
    const val PINK = 0xFFF06292
    const val BROWN = 0xFF795548
    const val BLACK = 0xFF212121
    const val WHITE = 0xFFFFFFFF
    const val SKY = 0xFF81D4FA

    private val DISTRACTORS = listOf(PURPLE, PINK, SKY, ORANGE, BLUE, GREEN, RED, YELLOW)

    val all: List<ColoringPicture> = listOf(
        ColoringPicture(
            "apple", "🍎", listOf(RED, GREEN, BROWN),
            listOf(
                Region(Area.Oval(0.5f, 0.58f, 0.32f, 0.3f), 0),
                Region(Area.Box(0.47f, 0.14f, 0.53f, 0.32f), 2),
                Region(Area.Poly(0.53f, 0.26f, 0.78f, 0.12f, 0.7f, 0.3f), 1),
            ),
        ),
        ColoringPicture(
            "tree", "🌳", listOf(GREEN, BROWN, RED),
            listOf(
                Region(Area.Box(0.43f, 0.55f, 0.57f, 0.92f), 1),
                Region(Area.Oval(0.5f, 0.38f, 0.34f, 0.28f), 0),
                Region(Area.Oval(0.36f, 0.34f, 0.06f, 0.06f), 2),
                Region(Area.Oval(0.62f, 0.44f, 0.06f, 0.06f), 2),
            ),
        ),
        ColoringPicture(
            "house", "🏠", listOf(YELLOW, RED, BROWN, BLUE),
            listOf(
                Region(Area.Box(0.2f, 0.48f, 0.8f, 0.9f), 0),
                Region(Area.Poly(0.12f, 0.5f, 0.5f, 0.14f, 0.88f, 0.5f), 1),
                Region(Area.Box(0.44f, 0.64f, 0.58f, 0.9f), 2),
                Region(Area.Box(0.26f, 0.56f, 0.38f, 0.68f), 3),
                Region(Area.Box(0.64f, 0.56f, 0.76f, 0.68f), 3),
            ),
        ),
        ColoringPicture(
            "fish", "🐟", listOf(ORANGE, YELLOW, WHITE, BLACK),
            listOf(
                Region(Area.Poly(0.62f, 0.5f, 0.92f, 0.25f, 0.92f, 0.75f), 1),
                Region(Area.Oval(0.42f, 0.5f, 0.3f, 0.2f), 0),
                Region(Area.Poly(0.36f, 0.34f, 0.5f, 0.16f, 0.54f, 0.34f), 1),
                Region(Area.Oval(0.26f, 0.45f, 0.06f, 0.06f), 2),
                Region(Area.Oval(0.25f, 0.45f, 0.025f, 0.025f), 3),
            ),
        ),
        ColoringPicture(
            "flower", "🌸", listOf(GREEN, PINK, YELLOW),
            listOf(
                Region(Area.Box(0.48f, 0.5f, 0.52f, 0.95f), 0),
                Region(Area.Oval(0.64f, 0.74f, 0.12f, 0.05f), 0),
                Region(Area.Oval(0.5f, 0.2f, 0.1f, 0.1f), 1),
                Region(Area.Oval(0.66f, 0.33f, 0.1f, 0.1f), 1),
                Region(Area.Oval(0.6f, 0.52f, 0.1f, 0.1f), 1),
                Region(Area.Oval(0.4f, 0.52f, 0.1f, 0.1f), 1),
                Region(Area.Oval(0.34f, 0.33f, 0.1f, 0.1f), 1),
                Region(Area.Oval(0.5f, 0.37f, 0.09f, 0.09f), 2),
            ),
        ),
        ColoringPicture(
            "car", "🚗", listOf(RED, SKY, BLACK, 0xFF9E9E9E),
            listOf(
                Region(Area.Poly(0.28f, 0.48f, 0.36f, 0.3f, 0.66f, 0.3f, 0.76f, 0.48f), 0),
                Region(Area.Box(0.1f, 0.47f, 0.9f, 0.7f), 0),
                Region(Area.Poly(0.34f, 0.46f, 0.4f, 0.34f, 0.49f, 0.34f, 0.49f, 0.46f), 1),
                Region(Area.Poly(0.53f, 0.46f, 0.53f, 0.34f, 0.63f, 0.34f, 0.7f, 0.46f), 1),
                Region(Area.Oval(0.3f, 0.72f, 0.1f, 0.1f), 2),
                Region(Area.Oval(0.7f, 0.72f, 0.1f, 0.1f), 2),
                Region(Area.Oval(0.3f, 0.72f, 0.04f, 0.04f), 3),
                Region(Area.Oval(0.7f, 0.72f, 0.04f, 0.04f), 3),
            ),
        ),
    )

    /** The palette for level [level]: the picture's colors, plus distractors from level 3 on. */
    fun palette(level: Int, random: kotlin.random.Random): List<Long> {
        val picture = all[level.coerceIn(all.indices)]
        val extra = when {
            level >= 4 -> 2
            level >= 2 -> 1
            else -> 0
        }
        return (picture.colors + DISTRACTORS.filter { it !in picture.colors }.shuffled(random).take(extra)).shuffled(random)
    }
}
