package com.nourtime.app.core.learning

import kotlin.random.Random

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

    /**
     * SVG path data [d] in a [width] x [height] view box (an illustrator's drawing). Drawn exactly from
     * [d]; tapped against the flattened [polygons] (0..1 coordinates).
     */
    class Svg(val d: String, val width: Float, val height: Float) : Area {
        val polygons: List<FloatArray> = SvgPath.flatten(d, width, height)

        override fun contains(x: Float, y: Float) = SvgPath.contains(polygons, x, y)

        override fun equals(other: Any?) = other is Svg && other.d == d && other.width == width && other.height == height
        override fun hashCode() = d.hashCode()
    }
}

/** An area and the color (index into [ColoringPicture.colors]) it has in the reference. */
data class Region(val area: Area, val color: Int)

/**
 * A picture to color (from `coloring/pictures.json`). Regions are drawn in order; a later region sits
 * on top of earlier ones. [extraColors] wrong colors join the palette to make the level harder.
 */
data class ColoringPicture(
    override val id: String,
    val emoji: String,
    val colors: List<Long>,
    val regions: List<Region>,
    val extraColors: Int = 0,
) : Level {
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

object ColoringPalette {
    /** The picture's colors plus [ColoringPicture.extraColors] wrong ones from [distractors], shuffled. */
    fun of(picture: ColoringPicture, distractors: List<Long>, random: Random): List<Long> =
        (picture.colors + distractors.filter { it !in picture.colors }.shuffled(random).take(picture.extraColors)).shuffled(random)
}
