package com.nourtime.app.feature.setup.timepicker

import org.junit.Assert.assertEquals
import org.junit.Test

class TimePickerMathTest {

    private val budget = 5..240
    private val step = 5

    @Test
    fun `snap rounds to the step and stays in range`() {
        assertEquals(60, TimePickerMath.snap(61.9f, budget, step))
        assertEquals(65, TimePickerMath.snap(62.6f, budget, step))
        assertEquals(5, TimePickerMath.snap(-40f, budget, step))
        assertEquals(240, TimePickerMath.snap(999f, budget, step))
    }

    @Test
    fun `fraction of a value along the range`() {
        assertEquals(0f, TimePickerMath.fraction(5, budget), 0.0001f)
        assertEquals(1f, TimePickerMath.fraction(240, budget), 0.0001f)
        assertEquals(0.5f, TimePickerMath.fraction(122, 4..240), 0.01f)
    }

    // ---- Dial: 0° at the top, clockwise ----

    @Test
    fun `dial angle maps to a snapped value`() {
        assertEquals(5, TimePickerMath.dialValue(angleDegrees = 0f, previous = 5, range = budget, step = step))
        assertEquals(125, TimePickerMath.dialValue(angleDegrees = 184f, previous = 120, range = budget, step = step))
    }

    @Test
    fun `dragging past the top doesn't jump from the maximum to the minimum`() {
        assertEquals(240, TimePickerMath.dialValue(angleDegrees = 3f, previous = 235, range = budget, step = step))
    }

    @Test
    fun `dragging back past the top doesn't jump from the minimum to the maximum`() {
        assertEquals(5, TimePickerMath.dialValue(angleDegrees = 357f, previous = 10, range = budget, step = step))
    }

    @Test
    fun `angle of a touch point, clockwise from the top`() {
        assertEquals(0f, TimePickerMath.angleOf(dx = 0f, dy = -10f), 0.01f)
        assertEquals(90f, TimePickerMath.angleOf(dx = 10f, dy = 0f), 0.01f)
        assertEquals(180f, TimePickerMath.angleOf(dx = 0f, dy = 10f), 0.01f)
        assertEquals(270f, TimePickerMath.angleOf(dx = -10f, dy = 0f), 0.01f)
    }

    // ---- Liquid: 0 at the bottom, 1 at the top ----

    @Test
    fun `liquid level maps to a snapped value`() {
        assertEquals(5, TimePickerMath.liquidValue(level = 0f, range = budget, step = step))
        assertEquals(240, TimePickerMath.liquidValue(level = 1.2f, range = budget, step = step))
        assertEquals(120, TimePickerMath.liquidValue(level = 0.49f, range = budget, step = step))
    }

    // ---- Tokens ----

    @Test
    fun `token sizes follow the step`() {
        assertEquals(listOf(60, 15, 5), TimePickerMath.tokenSizes(5))
        assertEquals(listOf(12, 3, 1), TimePickerMath.tokenSizes(1))
    }

    @Test
    fun `the jar shows the value as the fewest tokens`() {
        assertEquals(listOf(60, 60, 15, 5), TimePickerMath.tokensFor(140, listOf(60, 15, 5)))
        assertEquals(listOf(5), TimePickerMath.tokensFor(5, listOf(60, 15, 5)))
    }

    @Test
    fun `adding and removing tokens stays in range`() {
        assertEquals(75, TimePickerMath.addToken(60, 15, budget))
        assertEquals(240, TimePickerMath.addToken(230, 60, budget))
        assertEquals(45, TimePickerMath.removeToken(60, 15, budget))
        assertEquals(5, TimePickerMath.removeToken(10, 60, budget))
    }
}
