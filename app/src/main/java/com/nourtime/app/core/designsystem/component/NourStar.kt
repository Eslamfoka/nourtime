package com.nourtime.app.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import com.nourtime.app.core.designsystem.theme.NourPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Nour's states (brief §12). [CLOCK] is the logo variant. */
enum class NourPose { HAPPY, CLOCK, WAVING, PLAYING, STUDYING, EATING, SLEEPING }

/**
 * Nour, the small glowing star (brief §12). Original artwork drawn in code, so it scales to any size
 * and needs no bundled animation files.
 */
@Composable
fun NourStar(
    modifier: Modifier = Modifier,
    pose: NourPose = NourPose.HAPPY,
    animated: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "nour")
    val float by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animated) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "float",
    )
    val beat by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animated) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beat",
    )
    val tilt = when (pose) {
        NourPose.WAVING -> -12f + 24f * beat
        NourPose.SLEEPING -> 8f
        else -> -3f + 6f * float
    }

    Canvas(
        modifier.graphicsLayer {
            translationY = -size.height * (if (pose == NourPose.SLEEPING) 0.015f else 0.03f) * float
        },
    ) {
        val r = size.minDimension / 2f
        val c = center
        val glow = if (pose == NourPose.SLEEPING) 0.2f else 0.35f
        drawCircle(
            brush = Brush.radialGradient(listOf(NourPalette.Gold.copy(alpha = glow + 0.15f * float), Color.Transparent), c, r),
            radius = r,
            center = c,
        )

        val outer = r * 0.62f
        val body = Brush.linearGradient(
            listOf(NourPalette.GoldLight, NourPalette.Gold, NourPalette.GoldDeep),
            start = Offset(c.x - outer, c.y - outer),
            end = Offset(c.x + outer, c.y + outer),
        )
        rotate(tilt, c) {
            val star = starPath(c, outer, outer * 0.52f)
            drawPath(star, body, style = Stroke(width = outer * 0.22f, join = StrokeJoin.Round))
            drawPath(star, body)
            when (pose) {
                NourPose.CLOCK -> drawClockFace(c, outer)
                NourPose.SLEEPING -> drawSleepingFace(c, outer)
                else -> drawHappyFace(c, outer)
            }
        }

        when (pose) {
            NourPose.PLAYING -> drawBall(Offset(c.x + outer * 0.95f, c.y + outer * 0.55f - outer * 0.45f * abs(1 - 2 * beat)), outer * 0.28f)
            NourPose.STUDYING -> drawBook(Offset(c.x, c.y + outer * 0.95f), outer)
            NourPose.EATING -> drawPlate(Offset(c.x, c.y + outer * 0.98f), outer)
            NourPose.SLEEPING -> drawZzz(Offset(c.x + outer * 0.75f, c.y - outer * 0.75f), outer, float)
            NourPose.WAVING -> drawSparkles(c, outer, beat)
            else -> Unit
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

private fun DrawScope.drawCheeks(c: Offset, outer: Float, eyeY: Float, eyeDx: Float) {
    val cheekR = outer * 0.08f
    drawCircle(NourPalette.Coral.copy(alpha = 0.45f), cheekR, Offset(c.x - eyeDx * 1.6f, eyeY + outer * 0.14f))
    drawCircle(NourPalette.Coral.copy(alpha = 0.45f), cheekR, Offset(c.x + eyeDx * 1.6f, eyeY + outer * 0.14f))
}

private fun DrawScope.drawHappyFace(c: Offset, outer: Float) {
    val eyeY = c.y + outer * 0.02f
    val eyeDx = outer * 0.2f
    val eyeR = outer * 0.075f
    drawCircle(NourPalette.Navy, eyeR, Offset(c.x - eyeDx, eyeY))
    drawCircle(NourPalette.Navy, eyeR, Offset(c.x + eyeDx, eyeY))
    drawCircle(Color.White, eyeR * 0.35f, Offset(c.x - eyeDx + eyeR * 0.3f, eyeY - eyeR * 0.3f))
    drawCircle(Color.White, eyeR * 0.35f, Offset(c.x + eyeDx + eyeR * 0.3f, eyeY - eyeR * 0.3f))
    drawCheeks(c, outer, eyeY, eyeDx)
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

private fun DrawScope.drawSleepingFace(c: Offset, outer: Float) {
    val eyeY = c.y + outer * 0.04f
    val eyeDx = outer * 0.2f
    val w = outer * 0.13f
    val stroke = Stroke(width = outer * 0.05f, cap = StrokeCap.Round)
    listOf(c.x - eyeDx, c.x + eyeDx).forEach { x ->
        drawArc(NourPalette.Navy, 20f, 140f, false, Offset(x - w, eyeY - w * 0.6f), Size(w * 2, w * 1.2f), style = stroke)
    }
    drawCheeks(c, outer, eyeY, eyeDx)
    drawCircle(NourPalette.Navy, outer * 0.035f, Offset(c.x, eyeY + outer * 0.2f))
}

private fun DrawScope.drawClockFace(c: Offset, outer: Float) {
    val face = outer * 0.36f
    val center = Offset(c.x, c.y + outer * 0.06f)
    drawCircle(NourPalette.Cream, face, center)
    drawCircle(NourPalette.Navy, face, center, style = Stroke(width = outer * 0.05f))
    val width = outer * 0.06f
    drawLine(NourPalette.Navy, center, Offset(center.x, center.y - face * 0.62f), width, StrokeCap.Round)
    drawLine(NourPalette.Navy, center, Offset(center.x + face * 0.45f, center.y + face * 0.1f), width, StrokeCap.Round)
    drawCircle(NourPalette.Coral, outer * 0.045f, center)
}

private fun DrawScope.drawBall(center: Offset, radius: Float) {
    drawCircle(NourPalette.Coral, radius, center)
    val stroke = Stroke(width = radius * 0.16f, cap = StrokeCap.Round)
    drawArc(Color.White, -60f, 120f, false, Offset(center.x - radius * 1.4f, center.y - radius), Size(radius * 2, radius * 2), style = stroke)
    drawArc(Color.White, 120f, 120f, false, Offset(center.x + radius * -0.6f, center.y - radius), Size(radius * 2, radius * 2), style = stroke)
}

private fun DrawScope.drawBook(center: Offset, outer: Float) {
    val w = outer * 0.62f
    val h = outer * 0.42f
    val page = Color(0xFFFFFFFF)
    val cover = Color(0xFF4A90D9)
    drawRoundRect(cover, Offset(center.x - w - outer * 0.04f, center.y - h / 2 - outer * 0.04f), Size(w * 2 + outer * 0.08f, h + outer * 0.08f), CornerRadius(outer * 0.06f))
    drawRoundRect(page, Offset(center.x - w, center.y - h / 2), Size(w - outer * 0.02f, h), CornerRadius(outer * 0.04f))
    drawRoundRect(page, Offset(center.x + outer * 0.02f, center.y - h / 2), Size(w - outer * 0.02f, h), CornerRadius(outer * 0.04f))
    val line = Color(0xFFB8C7DC)
    for (i in 1..3) {
        val y = center.y - h / 2 + h * i / 4.5f
        drawLine(line, Offset(center.x - w * 0.85f, y), Offset(center.x - w * 0.15f, y), outer * 0.025f)
        drawLine(line, Offset(center.x + w * 0.15f, y), Offset(center.x + w * 0.85f, y), outer * 0.025f)
    }
    // Pencil
    val start = Offset(center.x + w * 1.05f, center.y - h * 1.6f)
    val end = Offset(center.x + w * 0.55f, center.y - h * 0.35f)
    drawLine(NourPalette.GoldDeep, start, end, outer * 0.1f, StrokeCap.Round)
    drawLine(NourPalette.Navy, end, Offset(end.x - (start.x - end.x) * 0.12f, end.y + (end.y - start.y) * 0.12f), outer * 0.05f, StrokeCap.Round)
}

private fun DrawScope.drawPlate(center: Offset, outer: Float) {
    val w = outer * 0.8f
    val h = outer * 0.3f
    drawOval(Color.White, Offset(center.x - w, center.y - h / 2), Size(w * 2, h))
    drawOval(NourPalette.Navy.copy(alpha = 0.25f), Offset(center.x - w, center.y - h / 2), Size(w * 2, h), style = Stroke(outer * 0.03f))
    drawOval(Color(0xFFFFB27A), Offset(center.x - w * 0.45f, center.y - h * 0.3f), Size(w * 0.9f, h * 0.45f))
    val tool = NourPalette.Navy.copy(alpha = 0.7f)
    drawLine(tool, Offset(center.x - w * 1.2f, center.y - h * 0.9f), Offset(center.x - w * 1.2f, center.y + h * 0.8f), outer * 0.05f, StrokeCap.Round)
    drawLine(tool, Offset(center.x + w * 1.2f, center.y - h * 0.9f), Offset(center.x + w * 1.2f, center.y + h * 0.8f), outer * 0.05f, StrokeCap.Round)
    drawCircle(tool, outer * 0.07f, Offset(center.x + w * 1.2f, center.y - h * 0.9f))
}

private fun DrawScope.drawZzz(origin: Offset, outer: Float, phase: Float) {
    val color = NourPalette.Cream.copy(alpha = 0.9f)
    listOf(0.16f to 0f, 0.12f to 0.35f, 0.09f to 0.65f).forEach { (s, t) ->
        val size = outer * s
        val o = Offset(origin.x + outer * t * 0.8f, origin.y - outer * t * 0.8f - outer * 0.1f * phase)
        val path = Path().apply {
            moveTo(o.x, o.y)
            lineTo(o.x + size, o.y)
            lineTo(o.x, o.y + size)
            lineTo(o.x + size, o.y + size)
        }
        drawPath(path, color, style = Stroke(width = outer * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawSparkles(c: Offset, outer: Float, beat: Float) {
    val color = NourPalette.Gold.copy(alpha = 0.4f + 0.6f * beat)
    listOf(-0.95f to -0.85f, 1.0f to -0.7f, 1.05f to 0.6f).forEach { (dx, dy) ->
        val p = Offset(c.x + outer * dx, c.y + outer * dy)
        val s = outer * (0.06f + 0.04f * beat)
        drawLine(color, Offset(p.x - s, p.y), Offset(p.x + s, p.y), outer * 0.03f, StrokeCap.Round)
        drawLine(color, Offset(p.x, p.y - s), Offset(p.x, p.y + s), outer * 0.03f, StrokeCap.Round)
    }
}
