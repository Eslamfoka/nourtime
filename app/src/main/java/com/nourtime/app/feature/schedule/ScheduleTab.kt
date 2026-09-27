package com.nourtime.app.feature.schedule

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourDangerButton
import com.nourtime.app.core.designsystem.component.NourDialogButton
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.core.designsystem.component.NourStar
import com.nourtime.app.data.db.PeriodKind
import com.nourtime.app.data.db.SchedulePeriod
import com.nourtime.app.data.schedule.ScheduleRepository
import com.nourtime.app.data.schedule.lengthMinutes
import com.nourtime.app.feature.home.formatMinuteOfDay
import com.nourtime.app.feature.home.showTimePicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
) : ViewModel() {
    val periods: StateFlow<List<SchedulePeriod>?> =
        repository.periods.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(period: SchedulePeriod) = viewModelScope.launch {
        if (period.id == 0L) repository.add(period.kind, period.startMinute, period.endMinute) else repository.update(period)
    }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    fun useSuggestedDay() = viewModelScope.launch { repository.addSuggestedDay() }
}

/** Timeline colours from the brief: study blue, play green, meal orange, sleep purple. */
val PeriodKind.color: Color
    get() = when (this) {
        PeriodKind.STUDY -> Color(0xFF4A90D9)
        PeriodKind.PLAY -> Color(0xFF4CAF7A)
        PeriodKind.MEAL -> Color(0xFFFF9F43)
        PeriodKind.SLEEP -> Color(0xFF8E6CD9)
    }

@get:StringRes
val PeriodKind.label: Int
    get() = when (this) {
        PeriodKind.STUDY -> R.string.period_study
        PeriodKind.PLAY -> R.string.period_play
        PeriodKind.MEAL -> R.string.period_meal
        PeriodKind.SLEEP -> R.string.period_sleep
    }

private const val DAY = 24 * 60
private const val SNAP = 15

@Composable
fun ScheduleTab(padding: PaddingValues, viewModel: ScheduleViewModel = hiltViewModel()) {
    val periods by viewModel.periods.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SchedulePeriod?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.nav_schedule), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.schedule_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val list = periods ?: return@Column
        if (list.isEmpty()) {
            EmptySchedule(onSuggested = viewModel::useSuggestedDay, onAdd = { editing = newPeriod() })
            return@Column
        }
        NourCard {
            Timeline(list, onTap = { editing = it }, onMove = { viewModel.save(it) })
            Legend()
        }
        list.forEach { period -> PeriodRow(period) { editing = period } }
        NourPrimaryButton(stringResource(R.string.schedule_add), { editing = newPeriod() })
    }

    editing?.let { period ->
        PeriodDialog(
            initial = period,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = if (period.id != 0L) {
                {
                    viewModel.delete(period.id)
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

private fun newPeriod() = SchedulePeriod(kind = PeriodKind.PLAY, startMinute = 16 * 60, endMinute = 17 * 60)

@Composable
private fun EmptySchedule(onSuggested: () -> Unit, onAdd: () -> Unit) {
    NourCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NourStar(Modifier.size(120.dp))
            Text(stringResource(R.string.schedule_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.schedule_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NourPrimaryButton(stringResource(R.string.schedule_suggested), onSuggested)
            NourSecondaryButton(stringResource(R.string.schedule_add), onAdd)
        }
    }
}

/**
 * A 24-hour bar, always drawn left-to-right like a clock. Tap a block to edit it; drag it sideways
 * to move it in 15-minute steps.
 */
@Composable
private fun Timeline(periods: List<SchedulePeriod>, onTap: (SchedulePeriod) -> Unit, onMove: (SchedulePeriod) -> Unit) {
    val currentPeriods by rememberUpdatedState(periods)
    var dragging by remember { mutableStateOf<SchedulePeriod?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    // Where the finger went down: onDragStart only reports where the drag threshold was crossed,
    // which can already be past the end of a short block.
    var pressX by remember { mutableFloatStateOf(0f) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.outline

    fun hit(x: Float, width: Float): SchedulePeriod? {
        val minute = (x / width * DAY).roundToInt().coerceIn(0, DAY - 1)
        return currentPeriods.lastOrNull { p ->
            val start = p.startMinute
            val end = p.startMinute + p.lengthMinutes
            minute in start until end || minute + DAY in start until end
        }
    }

    Column {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset -> pressX = offset.x },
                            onTap = { offset -> hit(offset.x, size.width.toFloat())?.let(onTap) },
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = hit(pressX, size.width.toFloat())
                                dragDelta = offset.x - pressX
                            },
                            onHorizontalDrag = { _, amount -> dragDelta += amount },
                            onDragEnd = {
                                val p = dragging
                                if (p != null) {
                                    val minutes = (dragDelta / size.width * DAY / SNAP).roundToInt() * SNAP
                                    if (minutes != 0) {
                                        onMove(p.copy(startMinute = (p.startMinute + minutes).mod(DAY), endMinute = (p.endMinute + minutes).mod(DAY)))
                                    }
                                }
                                dragging = null
                                dragDelta = 0f
                            },
                            onDragCancel = {
                                dragging = null
                                dragDelta = 0f
                            },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(track)
                    val perMinute = size.width / DAY
                    currentPeriods.forEach { p ->
                        val shift = if (dragging?.id == p.id) dragDelta else 0f
                        val start = p.startMinute * perMinute + shift
                        val len = p.lengthMinutes * perMinute
                        // Periods crossing midnight are drawn in two parts.
                        listOf(start, start - size.width, start + size.width).forEach { x ->
                            drawRoundRect(p.kind.color, Offset(x, 6.dp.toPx()), Size(len, size.height - 12.dp.toPx()), CornerRadius(6.dp.toPx()))
                        }
                    }
                    for (h in 0..24 step 3) {
                        val x = h * 60 * perMinute
                        drawLine(tick, Offset(x, 0f), Offset(x, 6.dp.toPx()), 1.dp.toPx())
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 6, 12, 18, 24).forEach { h ->
                    Text(formatMinuteOfDay((h % 24) * 60), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
        PeriodKind.entries.forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp).background(kind.color, CircleShape))
                Text(stringResource(kind.label), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PeriodRow(period: SchedulePeriod, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(16.dp).background(period.kind.color, CircleShape))
            Text(stringResource(period.kind.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.bedtime_window, formatMinuteOfDay(period.startMinute), formatMinuteOfDay(period.endMinute)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodDialog(
    initial: SchedulePeriod,
    onDismiss: () -> Unit,
    onSave: (SchedulePeriod) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var period by remember(initial) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == 0L) R.string.schedule_add else R.string.schedule_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeriodKind.entries.forEach { kind ->
                        FilterChip(
                            selected = period.kind == kind,
                            onClick = { period = period.copy(kind = kind) },
                            label = { Text(stringResource(kind.label)) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(kind.color, CircleShape)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                Text(
                    stringResource(R.string.bedtime_window, formatMinuteOfDay(period.startMinute), formatMinuteOfDay(period.endMinute)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showTimePicker(context, period.startMinute) { period = period.copy(startMinute = it) } },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.bedtime_start)) }
                    OutlinedButton(
                        onClick = { showTimePicker(context, period.endMinute) { period = period.copy(endMinute = it) } },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.bedtime_end)) }
                }
                if (onDelete != null) {
                    if (confirmDelete) {
                        Text(stringResource(R.string.schedule_delete_confirm), style = MaterialTheme.typography.bodyMedium)
                        NourDangerButton(stringResource(R.string.schedule_delete), onDelete)
                    } else {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text(stringResource(R.string.schedule_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            NourDialogButton(stringResource(R.string.schedule_save), { onSave(period) }, enabled = period.startMinute != period.endMinute)
        },
        dismissButton = { NourDialogButton(stringResource(R.string.action_cancel), onDismiss) },
    )
}
