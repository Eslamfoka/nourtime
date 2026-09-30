package com.nourtime.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class DrawingGamesTest {

    // --- Number Connect ---

    private val square = DotShape("square", "⬜", listOf(Dot(0.1f, 0.1f), Dot(0.9f, 0.1f), Dot(0.9f, 0.9f), Dot(0.1f, 0.9f)))

    @Test
    fun `a closed shape ends back at dot one`() {
        var r = ConnectRound(square)
        assertEquals(4, square.segmentCount)
        listOf(1, 2, 3, 0).forEach { r = r.reach(it) }
        assertTrue(r.done)
        assertEquals(3, r.stars)
    }

    @Test
    fun `an open shape stops at the last dot`() {
        val open = square.copy(closed = false)
        var r = ConnectRound(open)
        listOf(1, 2, 3).forEach { r = r.reach(it) }
        assertTrue(r.done)
    }

    @Test
    fun `only the next number draws a line`() {
        val r = ConnectRound(square).reach(2)
        assertEquals(0, r.drawn)
        assertEquals(1, r.reach(1).drawn)
    }

    @Test
    fun `letting go on a wrong dot is a mistake, on nothing or the start it isn't`() {
        val r = ConnectRound(square)
        assertEquals(1, r.release(3).mistakes)
        assertEquals(0, r.release(null).mistakes)
        assertEquals(0, r.release(0).mistakes)
        assertEquals(0, r.copy(tutorial = true).release(3).mistakes)
        assertEquals(1, ConnectRound(square, mistakes = 4).stars)
        assertEquals(2, ConnectRound(square, mistakes = 2).stars)
    }

    @Test
    fun `the nearest dot within reach is found`() {
        assertEquals(1, ConnectRules.dotAt(square.dots, 0.85f, 0.12f, 0.1f))
        assertNull(ConnectRules.dotAt(square.dots, 0.5f, 0.5f, 0.1f))
    }

    @Test
    fun `a quick swipe over a dot counts`() {
        val dot = Dot(0.5f, 0.5f)
        assertTrue(ConnectRules.passesNear(dot, 0.2f, 0.5f, 0.9f, 0.52f, 0.05f))
        assertFalse(ConnectRules.passesNear(dot, 0.2f, 0.8f, 0.9f, 0.8f, 0.05f))
        assertTrue(ConnectRules.passesNear(dot, 0.52f, 0.5f, 0.52f, 0.5f, 0.05f))
    }

    @Test
    fun `level shapes are drawable`() {
        TestContent.connect.levels.forEach { shape ->
            assertTrue(shape.id, shape.dots.size >= 3)
            shape.dots.forEach { assertTrue(shape.id, it.x in 0f..1f && it.y in 0f..1f) }
            // Dots far enough apart that a finger can't hit two at once, even with the big radius.
            for (i in shape.dots.indices) for (j in i + 1 until shape.dots.size) {
                val d = hypot(shape.dots[i].x - shape.dots[j].x, shape.dots[i].y - shape.dots[j].y)
                assertTrue("${shape.id} $i-$j too close ($d)", d > 2 * ConnectRules.radiusFor(young = false))
            }
        }
        assertEquals(3, TestContent.connect.levels.first().dots.size)
        assertEquals(8, TestContent.connect.levels.size)
    }

    // --- Coloring Match ---

    @Test
    fun `areas contain their inside and not their outside`() {
        assertTrue(Area.Box(0.1f, 0.1f, 0.5f, 0.5f).contains(0.3f, 0.3f))
        assertFalse(Area.Box(0.1f, 0.1f, 0.5f, 0.5f).contains(0.6f, 0.3f))
        assertTrue(Area.Oval(0.5f, 0.5f, 0.2f, 0.1f).contains(0.65f, 0.5f))
        assertFalse(Area.Oval(0.5f, 0.5f, 0.2f, 0.1f).contains(0.5f, 0.65f))
        val triangle = Area.Poly(0.5f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)
        assertTrue(triangle.contains(0.5f, 0.6f))
        assertFalse(triangle.contains(0.15f, 0.2f))
    }

    @Test
    fun `the topmost region wins`() {
        val fish = TestContent.coloring.pack.levels.first { it.id == "fish" }
        val eye = ColoringRules.regionAt(fish, 0.26f, 0.42f)!!
        assertEquals(2, fish.regions[eye].color) // white eye, not the orange body under it
        assertEquals(3, fish.regions[ColoringRules.regionAt(fish, 0.25f, 0.45f)!!].color) // pupil
        assertNull(ColoringRules.regionAt(fish, 0.02f, 0.02f))
    }

    @Test
    fun `filling right colors finishes the picture, wrong ones count as mistakes`() {
        val apple = TestContent.coloring.pack.levels.first()
        var r = ColoringRound(apple, apple.colors)
        assertEquals(ColoringRound.Outcome.IGNORED, r.fill(0).second) // no color chosen yet
        r = r.select(0xFF43A047)
        val (wrong, o1) = r.fill(0)
        assertEquals(ColoringRound.Outcome.WRONG, o1)
        assertEquals(1, wrong.mistakes)
        r = wrong
        apple.regions.forEachIndexed { i, region ->
            r = r.select(apple.colors[region.color]).fill(i).first
        }
        assertTrue(r.done)
        assertEquals(3, r.stars)
        assertNull(r.hintRegion)
    }

    @Test
    fun `every region of every picture can be tapped somewhere`() {
        TestContent.coloring.pack.levels.forEach { picture ->
            val hit = mutableSetOf<Int>()
            for (i in 0..200) for (j in 0..200) ColoringRules.regionAt(picture, i / 200f, j / 200f)?.let(hit::add)
            assertEquals(picture.id, picture.regions.indices.toSet(), hit)
        }
    }

    @Test
    fun `palettes hold the picture colors and grow with the level`() {
        val pack = TestContent.coloring
        pack.pack.levels.forEachIndexed { level, picture ->
            val palette = ColoringPalette.of(picture, pack.distractors, Random(level))
            assertTrue(palette.containsAll(picture.colors))
            assertEquals(palette.size, palette.toSet().size)
            assertEquals(picture.colors.size + picture.extraColors, palette.size)
        }
        assertEquals(0, pack.pack.levels[0].extraColors)
        assertEquals(2, pack.pack.levels[4].extraColors)
    }
}
