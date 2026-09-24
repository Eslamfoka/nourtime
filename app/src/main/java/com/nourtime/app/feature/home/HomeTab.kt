package com.nourtime.app.feature.home

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.BuildConfig
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.IconBadge
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourDangerButton
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.detection.ForegroundState
import com.nourtime.app.core.permissions.NourPermission
import com.nourtime.app.core.permissions.PermissionChecker
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.core.timer.TimerStatus
import com.nourtime.app.core.ui.formatCountdown
import com.nourtime.app.core.ui.startFirstAvailable
import com.nourtime.app.data.apps.InstalledAppsRepository
import com.nourtime.app.feature.setup.durationText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    engine: TimeEngine,
    tracker: ForegroundAppTracker,
    private val apps: InstalledAppsRepository,
    private val permissions: PermissionChecker,
) : ViewModel() {
    val status = engine.status
    val detection = tracker.state

    suspend fun label(packageName: String) = apps.label(packageName)

    fun accessibilitySettings() = permissions.settingsIntents(NourPermission.ACCESSIBILITY)
}

@Composable
internal fun HomeTab(padding: PaddingValues, viewModel: HomeViewModel = hiltViewModel(), permissionsViewModel: PermissionsViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val detection by viewModel.detection.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            }
            BudgetRing(s)
            StatusCard(s, viewModel::label)
        }
        if (BuildConfig.DEBUG) DebugDetectionCard(detection)
        PermissionsSection(permissionsViewModel)
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
        Canvas(Modifier.size(232.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(gold, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
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
            )
        }
    }
}

/** One of: available, in use now (with the app name), or locked with the refill countdown. */
@Composable
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

/** Debug builds only: what detection sees, to verify on real Samsung/Xiaomi phones. */
@Composable
private fun DebugDetectionCard(state: ForegroundState) {
    NourCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text("Detection (debug)", style = MaterialTheme.typography.titleSmall)
        Text(
            "source=${state.source}\nforeground=${state.foreground}\nvisible=${state.visible.joinToString()}\n" +
                "screenUsable=${state.screen.usable}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
