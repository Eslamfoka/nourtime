package com.nourtime.app.feature.learning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.core.learning.NumeralStyle
import kotlin.math.cos
import kotlin.math.sin

/** "3:05" in the child's numerals. The hour comes first in every language (a time, not math). */
internal fun timeText(hour: Int, minute: Int, numerals: NumeralStyle): String =
    numerals.format(hour) + ":" + numerals.format(minute / 10) + numerals.format(minute % 10)

/**
 * An analog clock: 12 numbers in the child's numerals, minute ticks, a short thick hour hand and a
 * long minute hand. Clocks run clockwise in every language, so this is never mirrored.
 */
@Composable
internal fun ClockFace(hour: Int, minute: Int, numerals: NumeralStyle, size: Dp) {
    val measurer = rememberTextMeasurer()
    val description = timeText(hour, minute, numerals)
    Canvas(Modifier.size(size).semantics { contentDescription = description }) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        fun at(angleDeg: Float, radius: Float): Offset {
            val a = Math.toRadians(angleDeg.toDouble() - 90.0)
            return Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
        }
        drawCircle(NourPalette.White, r, c)
        drawCircle(NourPalette.Navy, r - r * 0.03f, c, style = Stroke(r * 0.06f))
        for (m in 0 until 60) {
            val long = m % 5 == 0
            drawLine(
                NourPalette.Navy.copy(alpha = if (long) 0.8f else 0.3f),
                at(m * 6f, r * 0.9f),
                at(m * 6f, r * (if (long) 0.8f else 0.85f)),
                strokeWidth = r * (if (long) 0.03f else 0.015f),
            )
        }
        val hourAngle = (hour % 12) * 30f + minute * 0.5f
        drawLine(NourPalette.Navy, c, at(hourAngle, r * 0.42f), strokeWidth = r * 0.09f, cap = StrokeCap.Round)
        // The minute hand reaches the minute ticks (needed to read 11:05 against 11:06); the numbers
        // are drawn over it on small white discs, so none is ever hidden.
        drawLine(NourPalette.Coral, c, at(minute * 6f, r * 0.84f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
        val style = TextStyle(fontSize = (r * 0.2f / density).sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy)
        for (h in 1..12) {
            val text = measurer.measure(numerals.format(h), style)
            val p = at(h * 30f, r * 0.64f)
            drawCircle(NourPalette.White, r * 0.12f, p)
            drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height / 2f))
        }
        drawCircle(NourPalette.Navy, r * 0.07f, c)
    }
}
