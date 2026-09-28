package com.nourtime.app.feature.setup.timepicker

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nourtime.app.R
import com.nourtime.app.data.ui.TimePickerStyle
import kotlinx.coroutines.CancellationException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The time-picker theme for this phone (U1), provided at the app root from [com.nourtime.app.data.ui.UiPreferences]. */
val LocalTimePickerStyle = staticCompositionLocalOf { TimePickerStyle.DIAL }

/** What a picker's value counts: the budget is in minutes, the lock period in hours. */
enum class TimeUnitKind { MINUTES, HOURS }

/**
 * Picks a value in [range] (snapped to [step]) with the phone's theme: a dial, tokens in a jar, or a
 * liquid fill. [onPreview] follows a drag (null when it ends without a change); [onCommit] saves.
 * Every theme is also a TalkBack slider, and the preset chips next to it stay as the simple way.
 */
@Composable
fun TimePicker(
    value: Int,
    range: IntRange,
    step: Int,
    unit: TimeUnitKind,
    valueText: String,
    onPreview: (Int?) -> Unit,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = LocalTimePickerStyle.current
    // "Surprise me" picks once per appearance, so the theme doesn't change mid-drag.
    val style = remember(chosen) { chosen.resolve() }
    // Gesture handlers live across recompositions: they must call the latest callbacks, or a drag
    // saves a stale copy of the settings (seen: a weekend drag reverted the weekend lock period).
    val latestCommit by rememberUpdatedState(onCommit)
    val latestPreview by rememberUpdatedState(onPreview)
    val commit = remember { { v: Int -> latestCommit(v) } }
    val preview = remember { { v: Int? -> latestPreview(v) } }
    val a11y = Modifier.semantics {
        stateDescription = valueText
        progressBarRangeInfo = ProgressBarRangeInfo(
            current = value.toFloat(),
            range = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
        )
        setProgress { target ->
            commit(TimePickerMath.snap(target, range, step))
            true
        }
    }
    Box(modifier.fillMaxWidth().then(a11y), contentAlignment = Alignment.Center) {
        when (style) {
            TimePickerStyle.TOKENS -> TimeTokens(value, range, step, unit, commit)
            TimePickerStyle.LIQUID -> TimeLiquid(value, range, step, preview, commit)
            else -> TimeDial(value, range, step, preview, commit)
        }
    }
}

// ---------- 1. Dial ----------

@Composable
private fun TimeDial(value: Int, range: IntRange, step: Int, onPreview: (Int?) -> Unit, onCommit: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    var dragged by remember { mutableStateOf<Int?>(null) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val thumb = MaterialTheme.colorScheme.secondary
    val tick = MaterialTheme.colorScheme.outlineVariant
    val sweep by animateFloatAsState(TimePickerMath.fraction(value, range) * 360f, label = "dial")

    val ring = with(LocalDensity.current) { 22.dp.toPx() }

    fun update(position: Offset, size: IntSize, start: Boolean) {
        val angle = TimePickerMath.angleOf(position.x - size.width / 2f, position.y - size.height / 2f)
        val next = if (start) {
            // The first touch goes straight to that spot; the wrap guard is only for moving fingers.
            TimePickerMath.snap(range.first + angle / 360f * (range.last - range.first), range, step)
        } else {
            TimePickerMath.dialValue(angle, dragged ?: current, range, step)
        }
        if (next != (dragged ?: current)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        dragged = next
        onPreview(next)
    }

    Canvas(
        Modifier
            .padding(vertical = 8.dp)
            .size(220.dp)
            // Only touches on the ring move it: a swipe that starts elsewhere still scrolls the page.
            .grab(
                key = range to step,
                accepts = { position, size ->
                    val radius = minOf(size.width, size.height) / 2f - ring
                    val distance = (position - Offset(size.width / 2f, size.height / 2f)).getDistance()
                    abs(distance - radius) <= ring * 1.6f
                },
                onMove = ::update,
                onRelease = {
                    dragged?.let(onCommit)
                    dragged = null
                },
                onCancel = {
                    dragged = null
                    onPreview(null)
                },
            ),
    ) {
        val stroke = 22.dp.toPx()
        val radius = size.minDimension / 2 - stroke
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        // Twelve ticks, like a clock face.
        repeat(12) { i ->
            val a = (i * 30.0 - 90.0) * PI / 180.0
            val inner = radius - stroke
            val outer = radius - stroke / 2 - 2.dp.toPx()
            drawLine(
                tick,
                Offset(center.x + (inner * cos(a)).toFloat(), center.y + (inner * sin(a)).toFloat()),
                Offset(center.x + (outer * cos(a)).toFloat(), center.y + (outer * sin(a)).toFloat()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        val shownSweep = dragged?.let { TimePickerMath.fraction(it, range) * 360f } ?: sweep
        drawArc(fill, -90f, shownSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val a = (shownSweep - 90f) * PI.toFloat() / 180f
        val knob = Offset(center.x + radius * cos(a), center.y + radius * sin(a))
        drawCircle(thumb, stroke * 0.85f, knob)
        drawCircle(fill, stroke * 0.35f, knob)
    }
}

// ---------- 2. Tokens in a jar ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeTokens(value: Int, range: IntRange, step: Int, unit: TimeUnitKind, onCommit: (Int) -> Unit) {
    val sizes = remember(step) { TimePickerMath.tokenSizes(step) }
    var jar by remember { mutableStateOf(Rect.Zero) }
    val current by rememberUpdatedState(value)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                .onGloballyPositioned { jar = it.boundsInRoot() },
        ) {
            FlowRow(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TimePickerMath.tokensFor(value, sizes).forEach { token ->
                    val label = tokenLabel(token, unit)
                    val remove = stringResource(R.string.picker_token_remove, label)
                    Coin(label, big = token == sizes.first(), modifier = Modifier
                        .semantics { contentDescription = remove }
                        .clickable { onCommit(TimePickerMath.removeToken(current, token, range)) })
                }
            }
        }
        Text(
            stringResource(R.string.picker_tokens_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            sizes.forEach { token ->
                DraggableCoin(
                    label = tokenLabel(token, unit),
                    big = token == sizes.first(),
                    isOverJar = { jar.contains(it) },
                    onAdd = { onCommit(TimePickerMath.addToken(current, token, range)) },
                )
            }
        }
    }
}

@Composable
private fun DraggableCoin(label: String, big: Boolean, isOverJar: (Offset) -> Boolean, onAdd: () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val add = stringResource(R.string.picker_token_add, label)
    Coin(
        label,
        big,
        Modifier
            .zIndex(if (drag != Offset.Zero) 1f else 0f)
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .onGloballyPositioned { origin = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) - drag }
            .semantics { contentDescription = add }
            .clickable(onClick = onAdd)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (isOverJar(origin + drag)) onAdd()
                        drag = Offset.Zero
                    },
                    onDragCancel = { drag = Offset.Zero },
                ) { change, amount ->
                    change.consume()
                    drag += amount
                }
            },
    )
}

@Composable
private fun Coin(label: String, big: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = if (big) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        contentColor = if (big) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 2.dp,
        modifier = modifier.size(if (big) 64.dp else 52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun tokenLabel(amount: Int, unit: TimeUnitKind): String = when {
    unit == TimeUnitKind.HOURS -> stringResource(R.string.picker_token_hours, amount)
    amount % 60 == 0 -> stringResource(R.string.picker_token_hours, amount / 60)
    else -> stringResource(R.string.picker_token_minutes, amount)
}

// ---------- 3. Liquid fill ----------

@Composable
private fun TimeLiquid(value: Int, range: IntRange, step: Int, onPreview: (Int?) -> Unit, onCommit: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    var dragged by remember { mutableStateOf<Int?>(null) }
    val level by animateFloatAsState(TimePickerMath.fraction(dragged ?: value, range), label = "liquid")
    val handle = with(LocalDensity.current) { 44.dp.toPx() }
    val reducedMotion = reducedMotion()
    val phase = if (reducedMotion) {
        0f
    } else {
        val waves = rememberInfiniteTransition(label = "waves")
        waves.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    }
    val liquid = MaterialTheme.colorScheme.primary
    val foam = MaterialTheme.colorScheme.primaryContainer
    val glass = MaterialTheme.colorScheme.surfaceVariant
    val rim = MaterialTheme.colorScheme.secondary
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 44.dp, bottomEnd = 44.dp)

    fun levelAt(y: Float, height: Float) = 1f - y / height

    Box(
        Modifier
            .padding(vertical = 8.dp)
            .size(width = 150.dp, height = 220.dp)
            .clip(shape)
            .border(3.dp, rim, shape)
            .pointerInput(range, step) {
                detectTapGestures { position ->
                    onCommit(TimePickerMath.liquidValue(levelAt(position.y, size.height.toFloat()), range, step))
                }
            }
            // Only a touch at the surface (the handle) drags it, so scrolling past the bottle still scrolls.
            .grab(
                key = range to step,
                accepts = { position, size ->
                    val surface = size.height * (1f - TimePickerMath.fraction(current, range))
                    abs(position.y - surface) <= handle
                },
                onMove = { position, size, _ ->
                    val next = TimePickerMath.liquidValue(levelAt(position.y, size.height.toFloat()), range, step)
                    if (next != (dragged ?: current)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    dragged = next
                    onPreview(next)
                },
                onRelease = {
                    dragged?.let(onCommit)
                    dragged = null
                },
                onCancel = {
                    dragged = null
                    onPreview(null)
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(glass)
            val top = size.height * (1f - level)
            val amplitude = if (reducedMotion) 0f else 5.dp.toPx()
            val wave = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, top)
                var x = 0f
                while (x <= size.width) {
                    lineTo(x, top + amplitude * sin(2f * PI.toFloat() * x / size.width + phase))
                    x += 4f
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(wave, liquid)
            // A thin lighter band on the surface, and the handle to grab it by.
            drawLine(foam, Offset(0f, top - amplitude), Offset(size.width, top - amplitude), strokeWidth = 2.dp.toPx())
            val grip = Size(56.dp.toPx(), 10.dp.toPx())
            drawRoundRect(
                rim,
                topLeft = Offset(center.x - grip.width / 2, (top - grip.height / 2).coerceIn(0f, size.height - grip.height)),
                size = grip,
                cornerRadius = CornerRadius(grip.height / 2),
            )
            // Scale marks every quarter.
            for (i in 1..3) {
                val y = size.height * i / 4f
                drawLine(rim.copy(alpha = 0.35f), Offset(size.width * 0.72f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

/**
 * Follows one finger that starts where [accepts] says, from the first touch (so a tap counts too),
 * and consumes it so the page doesn't scroll. Touches elsewhere are left to the page.
 */
private fun Modifier.grab(
    key: Any?,
    accepts: (Offset, IntSize) -> Boolean,
    onMove: (position: Offset, size: IntSize, start: Boolean) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (!accepts(down.position, size)) return@awaitEachGesture
        down.consume()
        try {
            onMove(down.position, size, true)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                if (change.positionChange() != Offset.Zero) {
                    change.consume()
                    onMove(change.position, size, false)
                }
            }
            onRelease()
        } catch (e: CancellationException) {
            onCancel()
            throw e
        }
    }
}

/** True when the phone's "Remove animations" (animator scale 0) is on. */
@Composable
private fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Parent's choice of theme, for Settings (and the parent's phone). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimePickerStyleChooser(selected: TimePickerStyle, onSelect: (TimePickerStyle) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.picker_style_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.picker_style_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimePickerStyle.entries.forEach { style ->
                FilterChip(
                    selected = style == selected,
                    onClick = { onSelect(style) },
                    label = { Text(stringResource(style.labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

private val TimePickerStyle.labelRes: Int
    get() = when (this) {
        TimePickerStyle.DIAL -> R.string.picker_style_dial
        TimePickerStyle.TOKENS -> R.string.picker_style_tokens
        TimePickerStyle.LIQUID -> R.string.picker_style_liquid
        TimePickerStyle.SURPRISE -> R.string.picker_style_surprise
    }
