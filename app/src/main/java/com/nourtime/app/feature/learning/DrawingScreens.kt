package com.nourtime.app.feature.learning

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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
import com.nourtime.app.core.learning.Area
import com.nourtime.app.core.learning.ColoringPicture
import com.nourtime.app.core.learning.ColoringRules
import com.nourtime.app.core.learning.ConnectRules
import com.nourtime.app.core.learning.DotShape
import com.nourtime.app.core.learning.NumeralStyle
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.lock.Gendered

/**
 * The square drawing area: as big as the space left allows in both directions (a short phone or a
 * two-row palette leaves less height than width), at most 520 dp.
 */
private fun BoxWithConstraintsScope.canvasSide() = minOf(maxWidth, maxHeight, 520.dp)

// ---------------------------------------------------------------------------------------------
// Number Connect
// ---------------------------------------------------------------------------------------------

/** The path through [dots] of [shape] from dot 0 up to [upTo] segments, curves included. */
private fun DotShape.path(size: Size, upTo: Int = segmentCount): Path {
    val path = Path()
    if (dots.isEmpty()) return path
    path.moveTo(dots[0].x * size.width, dots[0].y * size.height)
    for (seg in 0 until upTo) {
        val d = dots[target(seg)]
        if (d.curved) {
            path.quadraticTo(d.cx!! * size.width, d.cy!! * size.height, d.x * size.width, d.y * size.height)
        } else {
            path.lineTo(d.x * size.width, d.y * size.height)
        }
    }
    return path
}

@Composable
internal fun ColumnScope.ConnectScreen(controller: LearningHubController, state: LearningState?, s: HubScreen.Connecting, gender: ChildGender, age: AgeGroup?) {
    val numerals = controller.numerals(state)
    val look = lookOf(s.game)
    val round = s.round
    val shape = round.shape

    HubTopBar(stringResource(R.string.learn_level, numerals.format(s.level + 1)), { controller.back() })
    val message = when {
        s.celebrating -> stringResource(Gendered(R.string.learn_great_m, R.string.learn_great_f).pick(gender)) + " " + shape.emoji
        round.tutorial && round.drawn == 0 -> stringResource(
            Gendered(R.string.learn_tutorial_connect_m, R.string.learn_tutorial_connect_f).pick(gender),
            numerals.format(1),
            numerals.format(2),
        )
        else -> stringResource(R.string.learn_task_connect)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleLarge,
            color = if (s.celebrating) NourPalette.MintDeep else NourPalette.Navy,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        ConnectPreview(shape, look.accent, stringResource(R.string.learn_connect_preview))
    }
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = NourPalette.White,
            shadowElevation = 4.dp,
            modifier = Modifier.size(canvasSide()),
        ) {
            ConnectCanvas(
                shape = shape,
                drawn = round.drawn,
                done = s.celebrating,
                tutorial = round.tutorial && round.drawn == 0,
                numerals = numerals,
                accent = look.accent,
                radius = ConnectRules.radiusFor(young = age == null || age == AgeGroup.AGES_3_6),
                // The numbers are only a visual guide: no voice on a tap or a drag (owner, 2026-10-03).
                onReach = controller::connectReach,
                onRelease = controller::connectRelease,
                fromDot = round.from,
                nextDot = round.next,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

/** A small picture of the finished drawing, so the child knows what they're building (U5). */
@Composable
private fun ConnectPreview(shape: DotShape, accent: Color, description: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NourPalette.White,
        shadowElevation = 2.dp,
        modifier = Modifier.size(76.dp).semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val outline = shape.path(size)
            if (shape.closed) drawPath(outline, accent.copy(alpha = 0.35f))
            drawPath(outline, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun ConnectCanvas(
    shape: DotShape,
    drawn: Int,
    done: Boolean,
    tutorial: Boolean,
    numerals: NumeralStyle,
    accent: Color,
    radius: Float,
    fromDot: Int,
    nextDot: Int,
    onReach: (Int) -> Unit,
    onRelease: (Int?) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    var finger by remember { mutableStateOf<Offset?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p",
    )
    val handT by rememberInfiniteTransition(label = "hand").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "h",
    )
    // Gesture callbacks read the latest round through these.
    val from by rememberUpdatedState(fromDot)
    val next by rememberUpdatedState(nextDot)
    val finished by rememberUpdatedState(done)
    val reach by rememberUpdatedState(onReach)
    val release by rememberUpdatedState(onRelease)

    Canvas(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .semantics { contentDescription = shape.emoji }
            .pointerInput(shape) {
                fun norm(o: Offset) = Offset(o.x / size.width, o.y / size.height)
                detectDragGestures(
                    onDragStart = { start ->
                        val p = norm(start)
                        // The drag starts after the touch slop, so a quick flick is already a little way
                        // from the dot here: accept a wider circle around the dot being drawn from.
                        dragging = !finished && ConnectRules.near(shape.dots[from], p.x, p.y, radius * 1.5f)
                        finger = if (dragging) start else null
                    },
                    onDrag = { change, _ ->
                        if (!dragging) return@detectDragGestures
                        change.consume()
                        val a = norm(change.previousPosition)
                        val b = norm(change.position)
                        finger = change.position
                        if (ConnectRules.passesNear(shape.dots[next], a.x, a.y, b.x, b.y, radius)) reach(next)
                    },
                    onDragEnd = {
                        val f = finger
                        if (dragging && f != null) {
                            val p = norm(f)
                            release(ConnectRules.dotAt(shape.dots, p.x, p.y, radius))
                        }
                        dragging = false
                        finger = null
                    },
                    onDragCancel = {
                        dragging = false
                        finger = null
                    },
                )
            },
    ) {
        val outline = shape.path(size)
        val stroke = 7.dp.toPx()
        // Faded outline of the whole drawing, then the part already drawn.
        drawPath(outline, NourPalette.Navy.copy(alpha = 0.12f), style = Stroke(stroke * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (done && shape.closed) drawPath(outline, accent.copy(alpha = 0.35f))
        drawPath(shape.path(size, drawn), accent, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // The line following the finger.
        finger?.let { f ->
            val a = shape.dots[from]
            drawLine(accent.copy(alpha = 0.6f), Offset(a.x * size.width, a.y * size.height), f, stroke, cap = StrokeCap.Round)
        }
        val dotR = 13.dp.toPx()
        shape.dots.forEachIndexed { i, d ->
            val c = Offset(d.x * size.width, d.y * size.height)
            // Dot 1 is where the line starts, so it's lit from the beginning.
            val reached = done || i == 0 || (1..drawn).any { shape.target(it - 1) == i }
            if (!done && i == next) drawCircle(NourPalette.Gold.copy(alpha = 0.35f + 0.3f * pulse), dotR * (1.6f + 0.4f * pulse), c)
            drawCircle(if (reached) accent else NourPalette.White, dotR, c)
            drawCircle(NourPalette.Navy, dotR, c, style = Stroke(2.5f.dp.toPx()))
            // Number above-left of its dot, kept inside the canvas.
            val label = measurer.measure(numerals.format(i + 1), TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NourPalette.Navy))
            val lx = (c.x - label.size.width - dotR * 0.6f).coerceIn(0f, size.width - label.size.width)
            val ly = (c.y - label.size.height - dotR * 0.4f).coerceIn(0f, size.height - label.size.height)
            drawText(label, topLeft = Offset(lx, ly))
        }
        if (tutorial) drawHand(measurer, shape.dots[0].let { Offset(it.x, it.y) }, shape.dots[1].let { Offset(it.x, it.y) }, handT)
    }
}

/** The tutorial's hand sliding from [a] to [b] (0..1 coordinates). */
private fun DrawScope.drawHand(measurer: androidx.compose.ui.text.TextMeasurer, a: Offset, b: Offset, t: Float) {
    val p = Offset((a.x + (b.x - a.x) * t) * size.width, (a.y + (b.y - a.y) * t) * size.height)
    val hand = measurer.measure("👆", TextStyle(fontSize = 40.sp))
    drawText(hand, topLeft = Offset(p.x - hand.size.width * 0.35f, p.y))
}

// ---------------------------------------------------------------------------------------------
// Coloring Match
// ---------------------------------------------------------------------------------------------

private fun Area.path(size: Size): Path = Path().also { path ->
    when (this) {
        is Area.Box -> path.addRect(Rect(left * size.width, top * size.height, right * size.width, bottom * size.height))
        is Area.Oval -> path.addOval(Rect((cx - rx) * size.width, (cy - ry) * size.height, (cx + rx) * size.width, (cy + ry) * size.height))
        is Area.Poly -> {
            val pts = points
            path.moveTo(pts[0].first * size.width, pts[0].second * size.height)
            pts.drop(1).forEach { (x, y) -> path.lineTo(x * size.width, y * size.height) }
            path.close()
        }
        is Area.Svg -> {
            path.addPath(SvgPaths.parsed(d))
            path.transform(Matrix().apply { scale(size.width / width, size.height / height) })
        }
    }
}

/** SVG path data parsed by Compose (exact curves and arcs), kept so a drawing isn't re-parsed every frame. */
private object SvgPaths {
    private val cache = android.util.LruCache<String, Path>(256)

    fun parsed(d: String): Path = cache.get(d) ?: PathParser().parsePathString(d).toPath().also { cache.put(d, it) }
}

private fun DrawScope.drawPicture(picture: ColoringPicture, fill: (Int) -> Color, outline: Float) {
    picture.regions.forEachIndexed { i, region ->
        val p = region.area.path(size)
        drawPath(p, fill(i))
        drawPath(p, NourPalette.Navy, style = Stroke(outline, join = StrokeJoin.Round))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.ColoringScreen(controller: LearningHubController, state: LearningState?, s: HubScreen.Coloring, gender: ChildGender) {
    val numerals = controller.numerals(state)
    val speaker = LocalSpeaker.current
    val round = s.round
    val picture = round.picture
    val colorNames = controller.hubContent.collectAsStateWithLifecycle().value.colorNames
    val tutorial = round.tutorial && round.fills.all { it == null }
    val hint = if (tutorial) round.hintRegion else null

    HubTopBar(stringResource(R.string.learn_level, numerals.format(s.level + 1)), { controller.back() })
    val message = when {
        s.celebrating -> stringResource(Gendered(R.string.learn_great_m, R.string.learn_great_f).pick(gender)) + " " + picture.emoji
        s.lastWrong -> stringResource(Gendered(R.string.learn_try_again_m, R.string.learn_try_again_f).pick(gender))
        tutorial -> stringResource(Gendered(R.string.learn_tutorial_coloring_m, R.string.learn_tutorial_coloring_f).pick(gender))
        else -> stringResource(Gendered(R.string.learn_task_coloring_m, R.string.learn_task_coloring_f).pick(gender))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        // The colored reference: the same drawing with its real colors.
        Surface(shape = RoundedCornerShape(20.dp), color = NourPalette.White, shadowElevation = 2.dp, modifier = Modifier.size(104.dp)) {
            Canvas(Modifier.fillMaxSize().padding(8.dp).semantics { contentDescription = picture.emoji }) {
                drawPicture(picture, { Color(round.target(it)) }, 1.5f.dp.toPx())
            }
        }
        Text(
            message,
            style = MaterialTheme.typography.titleLarge,
            color = if (s.celebrating) NourPalette.MintDeep else NourPalette.Navy,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(12.dp))
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p",
    )
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = NourPalette.White,
            shadowElevation = 4.dp,
            modifier = Modifier.size(canvasSide()),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .pointerInput(picture) {
                        detectTapGestures { o ->
                            ColoringRules.regionAt(picture, o.x / size.width, o.y / size.height)?.let(controller::colorFill)
                        }
                    },
            ) {
                drawPicture(picture, { i -> round.fills[i]?.let(::Color) ?: NourPalette.White }, 3.dp.toPx())
                // Tutorial: once the right color is picked, the hand points at where it goes.
                if (hint != null && round.selected == round.target(hint)) {
                    val b = picture.regions[hint].area.path(size).getBounds()
                    val hand = measurer.measure("👆", TextStyle(fontSize = 40.sp))
                    drawText(hand, topLeft = Offset(b.center.x - hand.size.width * 0.35f, b.center.y + pulse * 12.dp.toPx()))
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    // Up to five swatches in one row; bigger palettes wrap into two even rows, so every swatch keeps
    // a finger-sized target on a narrow phone.
    val perRow = round.palette.size.let { if (it <= 5) it else (it + 1) / 2 }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = perRow,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        round.palette.forEach { argb ->
            val selected = round.selected == argb
            val hinted = hint != null && round.selected != round.target(hint) && argb == round.target(hint)
            val name = colorNames[argb].orEmpty()
            Box(
                Modifier
                    .size(52.dp)
                    .scale(if (selected) 1.15f else if (hinted) 1f + 0.12f * pulse else 1f)
                    .background(Color(argb), CircleShape)
                    .border(if (selected) 4.dp else 2.dp, if (selected) NourPalette.Navy else NourPalette.Navy.copy(alpha = 0.2f), CircleShape)
                    .semantics {
                        contentDescription = name
                        role = Role.RadioButton
                        this.selected = selected
                    }
                    .pointerInput(argb) {
                        detectTapGestures {
                            if (name.isNotEmpty()) speaker.say(name, controller.appLanguage)
                            controller.colorSelect(argb)
                        }
                    },
            )
        }
    }
}
