package com.nourtime.app.feature.learning

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.core.learning.Dot
import com.nourtime.app.core.learning.TraceRound
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.lock.Gendered
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private fun DrawScope.polyline(points: List<Dot>, upTo: Int = points.lastIndex): Path = Path().apply {
    points.firstOrNull()?.let { moveTo(it.x * size.width, it.y * size.height) }
    for (i in 1..upTo.coerceAtMost(points.lastIndex)) lineTo(points[i].x * size.width, points[i].y * size.height)
}

/** Letter Tracing: follow each stroke of a big letter from its green start, in order, then tap its dots. */
@Composable
internal fun ColumnScope.TracingScreen(controller: LearningHubController, state: LearningState?, s: HubScreen.Tracing, gender: ChildGender) {
    val numerals = controller.numerals(state)
    val look = lookOf(s.game)
    val round = s.round
    var strayed by remember(s.level) { mutableStateOf(false) }

    HubTopBar(stringResource(R.string.learn_level, numerals.format(s.level + 1)), { controller.back() })
    val message = when {
        s.celebrating -> stringResource(Gendered(R.string.learn_great_m, R.string.learn_great_f).pick(gender)) + " " + round.letter.letter
        strayed -> stringResource(Gendered(R.string.learn_trace_stray_m, R.string.learn_trace_stray_f).pick(gender))
        round.tutorial && round.stroke == 0 && round.reached == 0 -> stringResource(Gendered(R.string.learn_tutorial_tracing_m, R.string.learn_tutorial_tracing_f).pick(gender))
        round.current?.dot == true -> stringResource(Gendered(R.string.learn_trace_dot_m, R.string.learn_trace_dot_f).pick(gender))
        else -> stringResource(Gendered(R.string.learn_task_tracing_m, R.string.learn_task_tracing_f).pick(gender))
    }
    Text(
        message,
        style = MaterialTheme.typography.titleLarge,
        color = if (s.celebrating) NourPalette.MintDeep else NourPalette.Navy,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = NourPalette.White,
            shadowElevation = 4.dp,
            modifier = Modifier.size(minOf(maxWidth, maxHeight, 520.dp)),
        ) {
            TraceCanvas(
                round = round,
                done = s.celebrating,
                accent = look.accent,
                numerals = numerals,
                canStart = controller::traceCanStart,
                onMove = { x, y ->
                    val o = controller.traceMove(x, y)
                    if (o == TraceRound.Outcome.STRAYED) strayed = true
                    if (o == TraceRound.Outcome.MOVED || o == TraceRound.Outcome.STROKE_DONE) strayed = false
                    o
                },
                onTap = { x, y ->
                    if (controller.traceTap(x, y) != TraceRound.Outcome.IGNORED) strayed = false
                },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun TraceCanvas(
    round: TraceRound,
    done: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    numerals: com.nourtime.app.core.learning.NumeralStyle,
    canStart: (Float, Float) -> Boolean,
    onMove: (Float, Float) -> TraceRound.Outcome,
    onTap: (Float, Float) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p",
    )
    val handT by rememberInfiniteTransition(label = "hand").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "h",
    )
    val start by rememberUpdatedState(canStart)
    val move by rememberUpdatedState(onMove)
    val tap by rememberUpdatedState(onTap)
    val letter = round.letter

    Canvas(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .semantics { contentDescription = letter.letter }
            .pointerInput(letter) {
                detectTapGestures { o -> tap(o.x / size.width, o.y / size.height) }
            }
            .pointerInput(letter) {
                // Where the finger goes down decides (not where the touch slop is crossed, which a
                // quick finger has already left behind); a stroke's end or a stray ends the drag.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!start(down.position.x / size.width, down.position.y / size.height)) return@awaitEachGesture
                    down.consume()
                    var active = true
                    drag(down.id) { change ->
                        if (!active) return@drag
                        change.consume()
                        when (move(change.position.x / size.width, change.position.y / size.height)) {
                            TraceRound.Outcome.STRAYED, TraceRound.Outcome.STROKE_DONE, TraceRound.Outcome.DONE -> active = false
                            else -> Unit
                        }
                    }
                }
            },
    ) {
        val width = size.minDimension * 0.085f
        val guide = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val ink = Stroke(width * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val dotR = width * 0.75f
        // The whole letter as a faded guide, then what's traced so far.
        letter.strokes.forEach { st ->
            if (st.dot) drawCircle(NourPalette.Navy.copy(alpha = 0.12f), dotR, Offset(st.start.x * size.width, st.start.y * size.height))
            else drawPath(polyline(st.points), NourPalette.Navy.copy(alpha = 0.12f), style = guide)
        }
        letter.strokes.forEachIndexed { i, st ->
            val finished = done || i < round.stroke
            when {
                st.dot && finished -> drawCircle(accent, dotR, Offset(st.start.x * size.width, st.start.y * size.height))
                !st.dot && finished -> drawPath(polyline(st.points), accent, style = ink)
                !st.dot && i == round.stroke && round.reached > 0 -> drawPath(polyline(st.points, round.reached), accent, style = ink)
            }
        }
        if (done) return@Canvas
        // Numbers at the start of each stroke still to do, so the order is visible.
        letter.strokes.forEachIndexed { i, st ->
            if (i < round.stroke) return@forEachIndexed
            val label = measurer.measure(numerals.format(i + 1), TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy.copy(alpha = 0.7f)))
            val c = Offset(st.start.x * size.width, st.start.y * size.height)
            drawText(label, topLeft = Offset((c.x + dotR).coerceAtMost(size.width - label.size.width), (c.y - dotR - label.size.height).coerceAtLeast(0f)))
        }
        val current = round.current ?: return@Canvas
        val here = round.here ?: return@Canvas
        val h = Offset(here.x * size.width, here.y * size.height)
        // Where to put the finger: a pulsing green dot, with an arrow showing the way to go.
        drawCircle(NourPalette.Mint.copy(alpha = 0.35f + 0.3f * pulse), dotR * (1.5f + 0.4f * pulse), h)
        drawCircle(NourPalette.MintDeep, dotR * 0.8f, h)
        if (!current.dot) {
            val ahead = current.points[minOf(round.reached + 4, current.points.lastIndex)]
            val a = atan2((ahead.y - here.y) * size.height, (ahead.x - here.x) * size.width)
            val tip = Offset(h.x + cos(a) * dotR * 2.6f, h.y + sin(a) * dotR * 2.6f)
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - cos(a - 0.5f) * dotR * 1.1f, tip.y - sin(a - 0.5f) * dotR * 1.1f)
                lineTo(tip.x - cos(a + 0.5f) * dotR * 1.1f, tip.y - sin(a + 0.5f) * dotR * 1.1f)
                close()
            }
            drawPath(arrow, NourPalette.MintDeep)
        }
        // First time: a hand showing the way along the first bit of the stroke.
        if (round.tutorial && round.stroke == 0 && round.reached == 0 && !current.dot) {
            val to = current.points[minOf(8, current.points.lastIndex)]
            val p = Offset((here.x + (to.x - here.x) * handT) * size.width, (here.y + (to.y - here.y) * handT) * size.height)
            val hand = measurer.measure("👆", TextStyle(fontSize = 40.sp))
            drawText(hand, topLeft = Offset(p.x - hand.size.width * 0.35f, p.y))
        }
    }
}
