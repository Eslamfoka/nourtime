package com.nourtime.app.feature.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.IconBadge
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourDangerButton
import com.nourtime.app.core.designsystem.component.NourDialogButton
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.permissions.NourPermission
import com.nourtime.app.core.permissions.PermissionChecker
import com.nourtime.app.core.time.TrustedClock
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.core.timer.TimerCommand
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.core.timer.TimerStatus
import com.nourtime.app.core.ui.formatCountdown
import com.nourtime.app.core.ui.startFirstAvailable
import com.nourtime.app.data.apps.InstalledAppsRepository
import com.nourtime.app.data.db.DailyUsage
import com.nourtime.app.data.usage.UsageRepository
import com.nourtime.app.data.usage.WeekReport
import com.nourtime.app.feature.setup.durationText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val engine: TimeEngine,
    tracker: ForegroundAppTracker,
    private val apps: InstalledAppsRepository,
    private val permissions: PermissionChecker,
    trustedClock: TrustedClock,
    usage: UsageRepository,
) : ViewModel() {
    val status = engine.status
    val detection = tracker.state

    // Re-checked every minute so the cards roll over at midnight while Home stays open.
    private val date = flow {
        while (true) {
            emit(trustedClock.now().toLocalDate())
            delay(60_000)
        }
    }.distinctUntilChanged()

    /** Today's use per limited app, most used first (brief §6 stats). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val today: StateFlow<List<DailyUsage>> = date
        .flatMapLatest { usage.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The last 7 days (Phase 4b). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val week: StateFlow<WeekReport?> = date
        .flatMapLatest { day -> usage.observeRange(WeekReport.firstDayNeeded(day), day).map { WeekReport.of(it, day) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun label(packageName: String) = apps.label(packageName)

    fun accessibilitySettings() = permissions.settingsIntents(NourPermission.ACCESSIBILITY)

    /** Dashboard quick actions: the same commands the parent's phone sends. */
    fun lockNow() {
        viewModelScope.launch { engine.apply(TimerCommand.LockNow) }
    }

    fun endLock() {
        viewModelScope.launch { engine.apply(TimerCommand.EndLock) }
    }
}

/** Where a dashboard tile leads. */
enum class Section { APPS, SCHEDULE, SETTINGS, PERMISSIONS }

/**
 * The parent's single dashboard: time left, the two quick actions, and tiles to Apps, Schedule and
 * Settings (no bottom bar). Usage stays below; the permission list lives in Settings.
 */
@Composable
internal fun Dashboard(
    padding: PaddingValues,
    onOpen: (Section) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    permissionsViewModel: PermissionsViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val permissions by permissionsViewModel.status.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionsViewModel.refresh() }
    var confirm by remember { mutableStateOf<TimerCommand?>(null) }

    TabColumn(padding) {
        val s = status
        if (s == null) {
            NourCard { Text(stringResource(R.string.status_starting), style = MaterialTheme.typography.bodyLarge) }
        } else {
            if (s.protectionDegraded) {
                DegradedCard(onFix = {
                    if (!context.startFirstAvailable(viewModel.accessibilitySettings())) {
                        Toast.makeText(context, R.string.cannot_open_settings, Toast.LENGTH_LONG).show()
                    }
                })
            } else if (permissions.values.any { !it }) {
                PermissionsWarningCard(onFix = { onOpen(Section.PERMISSIONS) })
            }
            BudgetRing(s)
            StatusCard(s, viewModel::label)
            QuickActions(
                locked = s.phase == TimerPhase.LOCKED,
                onLockNow = { confirm = TimerCommand.LockNow },
                onEndLock = { confirm = TimerCommand.EndLock },
            )
        }
        SectionTiles(onOpen)
        TodayCard(today, viewModel::label)
        week?.let { report ->
            var topName by remember(report.topApp) { mutableStateOf<String?>(null) }
            LaunchedEffect(report.topApp) { topName = report.topApp?.let { viewModel.label(it) } }
            WeekCard(report, topName)
        }
    }

    confirm?.let { command ->
        val lockNow = command == TimerCommand.LockNow
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (lockNow) R.string.device_lock_now else R.string.device_end_lock)) },
            text = { Text(stringResource(if (lockNow) R.string.device_lock_now_body else R.string.device_end_lock_body)) },
            confirmButton = {
                NourDialogButton(stringResource(if (lockNow) R.string.device_lock_now else R.string.device_end_lock), {
                    if (lockNow) viewModel.lockNow() else viewModel.endLock()
                    confirm = null
                })
            },
            dismissButton = { NourDialogButton(stringResource(R.string.action_cancel), { confirm = null }) },
        )
    }
}

/** "Lock now" and "End the lock": always on the dashboard, only the one that fits is enabled. */
@Composable
private fun QuickActions(locked: Boolean, onLockNow: () -> Unit, onEndLock: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile(
            icon = Icons.Rounded.Lock,
            label = stringResource(R.string.device_lock_now),
            enabled = !locked,
            onClick = onLockNow,
            container = MaterialTheme.colorScheme.secondary,
            content = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            icon = Icons.Rounded.LockOpen,
            label = stringResource(R.string.device_end_lock),
            enabled = locked,
            onClick = onEndLock,
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Apps, Schedule and Settings, replacing the old bottom bar. */
@Composable
private fun SectionTiles(onOpen: (Section) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val tile = MaterialTheme.colorScheme.surface
        val onTile = MaterialTheme.colorScheme.onSurface
        ActionTile(Icons.Rounded.Apps, stringResource(R.string.nav_apps), true, { onOpen(Section.APPS) }, tile, onTile, Modifier.weight(1f))
        ActionTile(Icons.Rounded.CalendarMonth, stringResource(R.string.nav_schedule), true, { onOpen(Section.SCHEDULE) }, tile, onTile, Modifier.weight(1f))
        ActionTile(Icons.Rounded.Settings, stringResource(R.string.nav_settings), true, { onOpen(Section.SETTINGS) }, tile, onTile, Modifier.weight(1f))
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (enabled) container else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        shadowElevation = if (enabled) 2.dp else 0.dp,
        modifier = modifier.heightIn(min = 96.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PermissionsWarningCard(onFix: () -> Unit) {
    NourCard(containerColor = NourTheme.colors.danger.copy(alpha = 0.16f)) {
        StatusRow(Icons.Rounded.WarningAmber, stringResource(R.string.home_permissions_title), stringResource(R.string.home_permissions_missing))
        NourDangerButton(stringResource(R.string.action_fix), onFix)
    }
}

/** Large gold ring with the remaining budget (brief §12, Home). */
@Composable
private fun BudgetRing(status: TimerStatus) {
    val fraction = if (status.phase == TimerPhase.LOCKED || status.budgetMs == 0L) 0f
    else status.remainingMs.toFloat() / status.budgetMs
    val animated by animateFloatAsState(fraction, tween(600), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val gold = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                // Near a full ring the rounded ends would overlap into a notch; square them off there.
                val sweep = 360f * animated
                val capDegrees = Math.toDegrees((stroke / (arcSize.width / 2)).toDouble()).toFloat()
                val cap = if (sweep >= 360f - capDegrees) StrokeCap.Butt else StrokeCap.Round
                drawArc(gold, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = cap))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatCountdown(if (status.phase == TimerPhase.LOCKED) 0 else status.remainingMs),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                stringResource(R.string.home_left_of, durationText((status.budgetMs / 60_000).toInt())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                // Inside the ring (about 156 dp across): a long budget wraps instead of crossing it.
                modifier = Modifier.widthIn(max = 136.dp),
            )
        }
    }
}

/** One of: available, in use now (with the app name), or locked with the refill countdown. */
@Composable
@SuppressLint("ProduceStateDoesNotAssignValue") // assigned after the suspend lookup
private fun StatusCard(status: TimerStatus, label: suspend (String) -> String) {
    val app = status.appsInUse.firstOrNull()
    val appName by produceState<String?>(null, app) { value = app?.let { label(it) } }
    val (icon, title, body) = when {
        status.phase == TimerPhase.LOCKED -> Triple(
            Icons.Rounded.LockClock,
            stringResource(R.string.status_locked),
            stringResource(R.string.status_refills_in, formatCountdown(status.lockRemainingMs)),
        )
        status.counting -> Triple(
            Icons.Rounded.PlayCircle,
            stringResource(R.string.status_in_use),
            appName.orEmpty(),
        )
        else -> Triple(
            Icons.Rounded.HourglassTop,
            stringResource(R.string.status_available),
            stringResource(R.string.status_available_body),
        )
    }
    val container = when {
        status.phase == TimerPhase.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
        else -> NourTheme.colors.success.copy(alpha = 0.16f)
    }
    NourCard(containerColor = container) {
        StatusRow(icon, title, body)
    }
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconBadge(icon, size = 48.dp, container = MaterialTheme.colorScheme.surface, tint = MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DegradedCard(onFix: () -> Unit) {
    NourCard(containerColor = NourTheme.colors.danger.copy(alpha = 0.16f)) {
        StatusRow(Icons.Rounded.WarningAmber, stringResource(R.string.status_degraded_title), stringResource(R.string.status_degraded_body))
        NourDangerButton(stringResource(R.string.action_fix), onFix)
    }
}

/** Debug builds only (Settings): what detection sees, to verify on real phones. */
@Composable
internal fun DebugDetectionCard(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.detection.collectAsStateWithLifecycle()
    NourCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text("Detection (debug)", style = MaterialTheme.typography.titleSmall)
        Text(
            "source=${state.source}\nforeground=${state.foreground}\nvisible=${state.visible.joinToString()}\n" +
                "screenUsable=${state.screen.usable}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Simple bars of today's use per app (brief §12, Home). */
@Composable
@SuppressLint("ProduceStateDoesNotAssignValue") // assigned after the suspend lookup
private fun TodayCard(today: List<DailyUsage>, label: suspend (String) -> String) {
    val totalMs = today.sumOf { it.usedMs }
    NourCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.stats_today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(usageText(totalMs), style = MaterialTheme.typography.titleMedium)
        }
        if (today.isEmpty()) {
            Text(
                stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val max = today.maxOfOrNull { it.usedMs }?.coerceAtLeast(1) ?: 1
        today.forEach { row ->
            val name by produceState(row.packageName, row.packageName) { value = label(row.packageName) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                Row {
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(usageText(row.usedMs), style = MaterialTheme.typography.bodyMedium)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(row.usedMs.toFloat() / max)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/** "Less than a minute" for short use, otherwise "12 minutes" / "1 hour 5 minutes". */
@Composable
internal fun usageText(ms: Long): String =
    if (ms in 1 until 60_000) stringResource(R.string.stats_under_minute) else durationText((ms / 60_000).toInt())
