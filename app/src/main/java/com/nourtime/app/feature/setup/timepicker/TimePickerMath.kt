package com.nourtime.app.feature.setup.timepicker

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Value maths shared by the time-picker themes (U1): all values are snapped to [step] and kept in range. */
object TimePickerMath {

    fun snap(raw: Float, range: IntRange, step: Int): Int =
        ((raw / step).roundToInt() * step).coerceIn(range)

    /** Where [value] sits along [range], 0..1. */
    fun fraction(value: Int, range: IntRange): Float =
        if (range.last == range.first) 0f else (value - range.first).toFloat() / (range.last - range.first)

    private fun valueAt(fraction: Float, range: IntRange, step: Int): Int =
        snap(range.first + fraction.coerceIn(0f, 1f) * (range.last - range.first), range, step)

    // ---------- Dial ----------

    /** Angle of a touch point around the dial's centre: 0° at the top, growing clockwise. */
    fun angleOf(dx: Float, dy: Float): Float {
        val degrees = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
        return if (degrees < 0) degrees + 360f else degrees
    }

    /**
     * Value for a dial angle. Crossing the top in one move would jump between the ends of the range,
     * so a jump of more than half the dial keeps the end the finger came from.
     */
    fun dialValue(angleDegrees: Float, previous: Int, range: IntRange, step: Int): Int {
        val next = angleDegrees / 360f
        val before = fraction(previous, range)
        if (abs(next - before) > 0.5f) return if (before > 0.5f) range.last else range.first
        return valueAt(next, range, step)
    }

    // ---------- Liquid ----------

    /** Value for a fill level: 0 at the bottom of the shape, 1 at the top. */
    fun liquidValue(level: Float, range: IntRange, step: Int): Int = valueAt(level, range, step)

    // ---------- Tokens ----------

    /** Three token sizes, largest first: 5 → 60, 15, 5 minutes; 1 → 12, 3, 1 hours. */
    fun tokenSizes(step: Int): List<Int> = listOf(step * 12, step * 3, step)

    /** The value as the fewest tokens, largest first. */
    fun tokensFor(value: Int, sizes: List<Int>): List<Int> {
        var left = value
        return buildList {
            for (size in sizes) {
                while (left >= size) {
                    add(size)
                    left -= size
                }
            }
        }
    }

    fun addToken(value: Int, token: Int, range: IntRange): Int = (value + token).coerceIn(range)

    fun removeToken(value: Int, token: Int, range: IntRange): Int = (value - token).coerceIn(range)
}
