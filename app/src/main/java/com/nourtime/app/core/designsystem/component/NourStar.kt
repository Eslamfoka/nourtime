package com.nourtime.app.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.nourtime.app.core.designsystem.theme.NourPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class NourFace { HAPPY, CLOCK }

/**
 * Nour, the small glowing star (brief §12). [NourFace.CLOCK] draws the logo variant with a clock face.
 * Original artwork drawn in code; more poses (playing, studying, eating, sleeping) come with the templates.
 */
@Composable
fun NourStar(
    modifier: Modifier = Modifier,
    face: NourFace = NourFace.HAPPY,
    animated: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "nour")
    val float by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animated) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "float",
    )

    Canvas(
        modifier.graphicsLayer {
            translationY = -size.height * 0.03f * float
            rotationZ = -3f + 6f * float
        },
    ) {
        val r = size.minDimension / 2f
        val c = center

        drawCircle(
            brush = Brush.radialGradient(
                listOf(NourPalette.Gold.copy(alpha = 0.35f + 0.15f * float), Color.Transparent),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )

        val outer = r * 0.62f
        val star = starPath(c, outer, outer * 0.52f)
        val body = Brush.linearGradient(
            listOf(NourPalette.GoldLight, NourPalette.Gold, NourPalette.GoldDeep),
            start = Offset(c.x - outer, c.y - outer),
            end = Offset(c.x + outer, c.y + outer),
        )
        drawPath(star, body, style = Stroke(width = outer * 0.22f, join = StrokeJoin.Round))
        drawPath(star, body)

        when (face) {
            NourFace.HAPPY -> drawHappyFace(c, outer)
            NourFace.CLOCK -> drawClockFace(c, outer)
        }
    }
}

private fun starPath(center: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = -PI / 2 + i * PI / 5
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawHappyFace(c: Offset, outer: Float) {
    val eyeY = c.y + outer * 0.02f
    val eyeDx = outer * 0.2f
    val eyeR = outer * 0.075f
    drawCircle(NourPalette.Navy, eyeR, Offset(c.x - eyeDx, eyeY))
    drawCircle(NourPalette.Navy, eyeR, Offset(c.x + eyeDx, eyeY))
    drawCircle(Color.White, eyeR * 0.35f, Offset(c.x - eyeDx + eyeR * 0.3f, eyeY - eyeR * 0.3f))
    drawCircle(Color.White, eyeR * 0.35f, Offset(c.x + eyeDx + eyeR * 0.3f, eyeY - eyeR * 0.3f))

    val cheekR = outer * 0.08f
    drawCircle(NourPalette.Coral.copy(alpha = 0.45f), cheekR, Offset(c.x - eyeDx * 1.6f, eyeY + outer * 0.14f))
    drawCircle(NourPalette.Coral.copy(alpha = 0.45f), cheekR, Offset(c.x + eyeDx * 1.6f, eyeY + outer * 0.14f))

    val smile = outer * 0.16f
    drawArc(
        color = NourPalette.Navy,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(c.x - smile, eyeY + outer * 0.02f),
        size = Size(smile * 2, smile * 1.4f),
        style = Stroke(width = outer * 0.055f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawClockFace(c: Offset, outer: Float) {
    val face = outer * 0.36f
    val center = Offset(c.x, c.y + outer * 0.06f)
    drawCircle(NourPalette.Cream, face, center)
    drawCircle(NourPalette.Navy, face, center, style = Stroke(width = outer * 0.05f))
    val hand = Stroke(width = outer * 0.06f, cap = StrokeCap.Round)
    drawLine(NourPalette.Navy, center, Offset(center.x, center.y - face * 0.62f), hand.width, StrokeCap.Round)
    drawLine(NourPalette.Navy, center, Offset(center.x + face * 0.45f, center.y + face * 0.1f), hand.width, StrokeCap.Round)
    drawCircle(NourPalette.Coral, outer * 0.045f, center)
}
