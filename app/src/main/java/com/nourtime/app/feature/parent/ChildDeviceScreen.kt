package com.nourtime.app.feature.parent

import android.text.format.DateUtils
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nourtime.app.service.timer.message
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.FullScreenDialog
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourDangerButton
import com.nourtime.app.core.designsystem.component.NourDialogButton
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.core.designsystem.component.NourTextButton
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.core.timer.TimerCommand
import com.nourtime.app.data.apps.InstalledApp
import com.nourtime.app.data.apps.InstalledAppsRepository
import com.nourtime.app.data.apps.filterApps
import com.nourtime.app.data.usage.WeekReport
import com.nourtime.app.feature.home.BedtimeCard
import com.nourtime.app.feature.home.WeekCard
import com.nourtime.app.feature.home.WeekendSection
import com.nourtime.app.feature.learning.LearningSettingsEditor
import com.nourtime.app.feature.home.DailyResetCard
import com.nourtime.app.feature.home.LockTypeEditor
import com.nourtime.app.feature.setup.AppList
import com.nourtime.app.feature.setup.AppRow
import com.nourtime.app.feature.setup.AppSearchField
import com.nourtime.app.feature.setup.AppsUiState
import com.nourtime.app.feature.setup.TimeBudgetEditor
import com.nourtime.app.feature.setup.durationText
import com.nourtime.app.feature.setup.timepicker.TimePickerStyleSetting
import com.nourtime.app.remote.model.AskPolicy
import com.nourtime.app.remote.model.AskState
import com.nourtime.app.remote.model.RemoteSettings
import com.nourtime.app.remote.model.TimeRequest
import com.nourtime.app.remote.parent.ChildDevice
import com.nourtime.app.remote.parent.DeviceSummary
import com.nourtime.app.remote.parent.Load
import com.nourtime.app.remote.parent.ParentDevices
import com.nourtime.app.remote.parent.ParentUser
import com.nourtime.app.remote.parent.SentCommand
import com.nourtime.app.remote.parent.loadWithRetry
import com.nourtime.app.remote.parent.valueOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDate
import javax.inject.Inject

/** One child's phone on the parent's phone (Phase 2): status, extra time, lock, usage, settings. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChildDeviceViewModel @Inject constructor(
    private val remote: ParentDevices,
    private val localApps: InstalledAppsRepository,
) : ViewModel() {

    private val deviceId = MutableStateFlow<String?>(null)
    private val ids = deviceId.filterNotNull()

    private val retries = MutableStateFlow(0)

    /** Ready(null) when the phone is gone (removed, or the child disconnected it). */
    val device: StateFlow<Load<ChildDevice?>> = ids.flatMapLatest { id -> loadWithRetry(retries) { remote.device(id) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)
    // The details below fail on their own (an error used to read as "no use today" or an empty app
    // list); "Try again" restarts all of them with the phone itself.
    val usage: StateFlow<Load<Map<String, Long>>> = load { remote.usage(it, LocalDate.now()) }
    /** The last 7 days (Phase 4b), by the parent's own date like "today" above. */
    val week: StateFlow<Load<WeekReport>> = load { id ->
        val today = LocalDate.now()
        remote.usageRange(id, WeekReport.firstDayNeeded(today), today).map { WeekReport.of(it, today) }
    }
    val apps: StateFlow<Load<List<InstalledApp>>> = load { remote.apps(it) }
    val commands: StateFlow<Load<List<SentCommand>>> = load { remote.recentCommands(it) }

    /** The child's newest request for more time (Phase 4c). */
    val request: StateFlow<Load<TimeRequest?>> = load { remote.latestRequest(it) }

    private fun <T> load(source: (String) -> Flow<T>): StateFlow<Load<T>> =
        ids.flatMapLatest { id -> loadWithRetry(retries) { source(id) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)
    private val _answerFailed = MutableStateFlow(false)
    val answerFailed: StateFlow<Boolean> = _answerFailed

    /** The last change the server refused or that couldn't reach it; cleared by the next one that works. */
    private val _failure = MutableStateFlow<DeviceActionFailure?>(null)
    val failure: StateFlow<DeviceActionFailure?> = _failure.asStateFlow()

    private val _removing = MutableStateFlow(false)
    val removing: StateFlow<Boolean> = _removing.asStateFlow()

    fun bind(id: String) {
        deviceId.value = id
    }

    fun retry() {
        retries.value++
    }

    fun dismissFailure() {
        _failure.value = null
    }

    /** Offline, the command waits in the local queue ("waiting for the child's phone"); only a refusal fails. */
    fun send(command: TimerCommand, user: ParentUser) {
        val id = deviceId.value ?: return
        viewModelScope.launch { track(DeviceActionFailure.SEND) { remote.send(id, command, user.uid) } }
    }

    /** [minutes] approves the request with that bonus; null declines it. Needs the network. */
    fun answer(requestId: String, minutes: Int?, user: ParentUser) {
        val id = deviceId.value ?: return
        _answerFailed.value = false
        viewModelScope.launch {
            runCatching { remote.answer(id, requestId, minutes, user.uid) }.onFailure { _answerFailed.value = true }
        }
    }

    /** Applies [change] to the settings on the server and writes the next revision. */
    fun edit(change: (RemoteSettings) -> RemoteSettings) {
        val id = deviceId.value ?: return
        viewModelScope.launch { track(DeviceActionFailure.SAVE) { remote.writeSettings(id, change) } }
    }

    /** Leaves the screen only once the server has it; on a failure the phone stays and says why. */
    fun remove(onDone: () -> Unit) {
        val id = deviceId.value ?: return
        if (_removing.value) return
        viewModelScope.launch {
            _removing.value = true
            val ok = track(DeviceActionFailure.REMOVE) { remote.remove(id) }
            _removing.value = false
            if (ok) onDone()
        }
    }

    private suspend fun track(kind: DeviceActionFailure, action: suspend () -> Unit): Boolean =
        runCatching { action() }
            .onSuccess { if (_failure.value == kind) _failure.value = null }
            .onFailure {
                if (it is CancellationException) throw it
                Log.w("ChildDevice", "$kind failed", it)
                _failure.value = kind
            }
            .isSuccess

    /** The child's app icons aren't uploaded; the parent's phone shows its own copy when it has the app. */
    suspend fun icon(packageName: String): ImageBitmap? = localApps.icon(packageName)
}

enum class DeviceActionFailure { SEND, SAVE, REMOVE }

private val BONUS_CHOICES = listOf(15, 30, 60)

@Composable
fun ChildDeviceScreen(
    deviceId: String,
    user: ParentUser,
    onBack: () -> Unit,
    // Keyed: view models live as long as the activity, and another phone's data must never show here.
    viewModel: ChildDeviceViewModel = hiltViewModel(key = deviceId),
) {
    LaunchedEffect(deviceId) { viewModel.bind(deviceId) }
    val load by viewModel.device.collectAsStateWithLifecycle()
    val device = load.valueOrNull()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val removingNow by viewModel.removing.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val commands by viewModel.commands.collectAsStateWithLifecycle()
    val request by viewModel.request.collectAsStateWithLifecycle()
    val answerFailed by viewModel.answerFailed.collectAsStateWithLifecycle()
    val now by rememberNow()
    var confirm by remember { mutableStateOf<TimerCommand?>(null) }
    var removing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<AppListKind?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(device?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
            }
            when (load) {
                Load.Loading -> LoadingRow(stringResource(R.string.device_loading))
                Load.Failed -> LoadFailedCard(stringResource(R.string.device_load_failed), viewModel::retry)
                is Load.Ready -> Unit
            }
            val d = device ?: return@Column
            val summary = DeviceSummary.of(d.status, now)
            if (listOf(usage, week, apps, commands, request).any { it == Load.Failed }) {
                LoadFailedCard(stringResource(R.string.device_details_failed), viewModel::retry)
            }
            request.valueOrNull()?.takeIf { AskPolicy.state(it, now) == AskState.Waiting }?.let { r ->
                AskCard(d.name, r, now, answerFailed, onAnswer = { minutes -> viewModel.answer(r.id, minutes, user) })
            }

            NourCard {
                Text(summaryText(summary), style = MaterialTheme.typography.titleLarge)
                d.status?.updatedAtMs?.let { at ->
                    val minutes = DeviceSummary.minutesAgo(at, now)
                    Text(
                        if (minutes == null) {
                            stringResource(R.string.device_updated_now)
                        } else {
                            stringResource(R.string.device_updated, DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString())
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.degraded()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = NourTheme.colors.danger, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.parent_protection_warning), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                d.alerts.visible(now).forEach { alert ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = NourTheme.colors.danger, modifier = Modifier.size(18.dp))
                        Text(stringResource(alert.message), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            d.alerts.pendingPinReset(now)?.let { until -> PinResetCard(until, onApprove = { confirm = TimerCommand.ApprovePinReset }) }
            val appList = apps.valueOrNull().orEmpty()
            ActionsCard(summary, commands.valueOrNull().orEmpty(), onCommand = { confirm = it })
            UsageCard(usage, appList)
            week.valueOrNull()?.let { report -> WeekCard(report, appList.firstOrNull { it.packageName == report.topApp }?.label) }

            val settings = d.settings
            Text(stringResource(R.string.device_settings), style = MaterialTheme.typography.titleLarge)
            if (settings == null) {
                Text(stringResource(R.string.device_settings_waiting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TimeBudgetEditor(
                    settings.budgetMinutes,
                    settings.lockPeriodHours,
                    onBudgetMinutes = { v -> viewModel.edit { it.copy(budgetMinutes = v) } },
                    onLockPeriodHours = { v -> viewModel.edit { it.copy(lockPeriodHours = v) } },
                )
                TimePickerStyleSetting()
                DailyResetCard(settings.dailyResetMinute) { v -> viewModel.edit { it.copy(dailyResetMinute = v) } }
                LockTypeEditor(settings.lockType) { v -> viewModel.edit { it.copy(lockType = v) } }
                BedtimeCard(settings.bedtime) { v -> viewModel.edit { it.copy(bedtime = v) } }
                WeekendSection(settings.weekend) { v -> viewModel.edit { it.copy(weekend = v) } }
                AppListButton(stringResource(R.string.device_limited_apps), settings.limitedApps.size) { picking = AppListKind.LIMITED }
                AppListButton(stringResource(R.string.allowed_section), settings.allowedDuringLock.size) { picking = AppListKind.ALLOWED }
                Text(stringResource(R.string.device_learning), style = MaterialTheme.typography.titleLarge)
                NourCard {
                    // "Today" by the parent's date, like the usage above.
                    LearningSettingsEditor(
                        settings = settings.learning,
                        voices = settings.voices,
                        earnedToday = d.learning?.earnedOn(LocalDate.now()),
                        bankMinutes = d.learning?.bankMinutes,
                        onEnabled = { v -> viewModel.edit { it.copy(learning = it.learning.copy(enabled = v)) } },
                        onMinutesPerLevel = { v -> viewModel.edit { it.copy(learning = it.learning.copy(minutesPerLevel = v)) } },
                        onDailyMax = { v -> viewModel.edit { it.copy(learning = it.learning.copy(dailyMaxMinutes = v)) } },
                        onVoice = { pack -> viewModel.edit { it.copy(voices = it.voices + (pack.language to pack)) } },
                    )
                }
            }
            if (removingNow) {
                LoadingRow(stringResource(R.string.device_removing))
            } else {
                NourDangerButton(stringResource(R.string.device_remove), { removing = true })
            }
            // Room to scroll the last button above the failure bar.
            if (failure != null) Spacer(Modifier.height(96.dp))
        }
        // Pinned to the bottom: the change that failed may be anywhere on this long screen.
        failure?.let { f ->
            FailureBar(stringResource(f.message), viewModel::dismissFailure, Modifier.align(Alignment.BottomCenter))
        }
    }

    confirm?.let { command ->
        ConfirmCommandDialog(
            command = command,
            onConfirm = {
                viewModel.send(command, user)
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.device_remove_title)) },
            text = { Text(stringResource(R.string.device_remove_body)) },
            confirmButton = {
                NourDialogButton(stringResource(R.string.device_remove), {
                    removing = false
                    viewModel.remove(onBack)
                }, destructive = true)
            },
            dismissButton = { NourDialogButton(stringResource(R.string.action_cancel), { removing = false }) },
        )
    }
    val settings = device?.settings
    val kind = picking
    if (kind != null && settings != null) {
        RemoteAppsDialog(kind, settings, apps, viewModel::retry, viewModel::icon, failure?.let { stringResource(it.message) }, onToggle = { pkg, on ->
            viewModel.edit { if (kind == AppListKind.LIMITED) it.withLimited(pkg, on) else it.withAllowed(pkg, on) }
        }, onClose = { picking = null })
    }
}

@Composable
private fun FailureBar(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.safeDrawingPadding().padding(16.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = NourTheme.colors.danger, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) { ActionErrorText(text) }
            NourTextButton(stringResource(R.string.action_dismiss), onDismiss)
        }
    }
}

/**
 * A new PIN chosen on the child's phone through the security question waits a day. If the parent did
 * it, they approve it here; if not, unlocking the child's phone with the current PIN cancels it.
 */
@Composable
private fun PinResetCard(activeAtMs: Long, onApprove: () -> Unit) {
    val context = LocalContext.current
    val at = DateUtils.formatDateTime(
        context,
        activeAtMs,
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL,
    )
    NourCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = NourTheme.colors.danger)
            Text(stringResource(R.string.device_pin_reset_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.device_pin_reset_body, at), style = MaterialTheme.typography.bodyMedium)
        NourPrimaryButton(stringResource(R.string.device_pin_reset_approve), onApprove)
    }
}

private val DeviceActionFailure.message: Int
    get() = when (this) {
        DeviceActionFailure.SEND -> R.string.device_send_failed
        DeviceActionFailure.SAVE -> R.string.device_save_failed
        DeviceActionFailure.REMOVE -> R.string.device_remove_failed
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionsCard(summary: DeviceSummary, commands: List<SentCommand>, onCommand: (TimerCommand) -> Unit) {
    NourCard {
        Text(stringResource(R.string.device_bonus), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BONUS_CHOICES.forEach { minutes ->
                NourSecondaryButton(stringResource(R.string.device_bonus_chip, durationText(minutes)), { onCommand(TimerCommand.Bonus(minutes)) }, Modifier.fillMaxWidth(0.45f))
            }
        }
        when (summary) {
            is DeviceSummary.Locked -> NourPrimaryButton(stringResource(R.string.device_end_lock), { onCommand(TimerCommand.EndLock) })
            // A lock waits under the break: end it, or stop the break with a fresh lock.
            is DeviceSummary.Break -> {
                NourPrimaryButton(stringResource(R.string.device_end_lock), { onCommand(TimerCommand.EndLock) })
                NourSecondaryButton(stringResource(R.string.device_lock_now), { onCommand(TimerCommand.LockNow) })
            }
            else -> NourPrimaryButton(stringResource(R.string.device_lock_now), { onCommand(TimerCommand.LockNow) })
        }
        val latest = commands.firstOrNull()
        if (latest != null) {
            Text(
                stringResource(
                    when {
                        latest.expired -> R.string.device_command_expired
                        latest.applied -> R.string.device_command_applied
                        else -> R.string.device_command_pending
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Sara is asking for more time": approve with a bonus (one tap, no confirmation) or say not now. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskCard(name: String, request: TimeRequest, now: Long, failed: Boolean, onAnswer: (Int?) -> Unit) {
    NourCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.MoreTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.ask_parent_title, name.ifBlank { stringResource(R.string.ask_parent_your_child) }), style = MaterialTheme.typography.titleMedium)
        }
        request.createdAtMs?.let { at ->
            Text(
                if (DeviceSummary.minutesAgo(at, now) == null) {
                    stringResource(R.string.ask_parent_just_now)
                } else {
                    DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ASK_CHOICES.forEach { minutes ->
                NourPrimaryButton(stringResource(R.string.device_bonus_chip, durationText(minutes)), { onAnswer(minutes) }, Modifier.fillMaxWidth(0.45f))
            }
        }
        NourTextButton(stringResource(R.string.ask_parent_decline), { onAnswer(null) })
        if (failed) {
            ActionErrorText(stringResource(R.string.ask_parent_failed))
        }
    }
}

private val ASK_CHOICES = listOf(15, 30)

@Composable
private fun UsageCard(load: Load<Map<String, Long>>, apps: List<InstalledApp>) {
    val labels = apps.associate { it.packageName to it.label }
    val usage = load.valueOrNull()
    NourCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.device_today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (usage != null) Text(durationText(minutesRoundedUp(usage.values.sum())), style = MaterialTheme.typography.titleMedium)
        }
        when (load) {
            Load.Loading -> LoadingRow(stringResource(R.string.device_loading))
            // The card at the top says why and offers "Try again".
            Load.Failed -> Text(stringResource(R.string.device_details_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is Load.Ready -> Unit
        }
        if (usage == null) return@NourCard
        if (usage.values.sum() == 0L) {
            Text(stringResource(R.string.device_today_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        usage.entries.filter { it.value > 0 }.sortedByDescending { it.value }.forEach { (pkg, ms) ->
            Row {
                Text(labels[pkg] ?: pkg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(durationText(minutesRoundedUp(ms)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AppListButton(title: String, count: Int, onClick: () -> Unit) {
    NourCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(pluralStringResource(R.plurals.apps_selected_count, count, count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NourSecondaryButton(stringResource(R.string.allowed_choose), onClick)
    }
}

private enum class AppListKind { LIMITED, ALLOWED }

@Composable
private fun RemoteAppsDialog(
    kind: AppListKind,
    settings: RemoteSettings,
    appsLoad: Load<List<InstalledApp>>,
    onRetry: () -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
    failure: String?,
    onToggle: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val chosen = if (kind == AppListKind.LIMITED) settings.limitedApps else settings.allowedDuringLock
    val pinned = remember { chosen }
    val apps = appsLoad.valueOrNull().orEmpty()
    val candidates = if (kind == AppListKind.ALLOWED) apps.filter { it.packageName !in settings.limitedApps } else apps
    val state = AppsUiState(
        loading = false,
        query = query,
        rows = filterApps(candidates, query, pinned).map { AppRow(it, it.packageName in chosen) },
        limitedCount = chosen.size,
    )
    FullScreenDialog(onDismissRequest = onClose) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(if (kind == AppListKind.LIMITED) R.string.device_limited_apps else R.string.allowed_section),
                    style = MaterialTheme.typography.headlineSmall,
                )
                when (appsLoad) {
                    Load.Loading -> LoadingRow(stringResource(R.string.device_loading))
                    Load.Failed -> LoadFailedCard(stringResource(R.string.device_apps_failed), onRetry)
                    is Load.Ready -> if (apps.isEmpty()) {
                        Text(stringResource(R.string.device_no_app_list), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AppSearchField(query, { query = it })
                AppList(state = state, onCheckedChange = onToggle, loadIcon = loadIcon, modifier = Modifier.weight(1f))
                failure?.let { ActionErrorText(it) }
                NourPrimaryButton(stringResource(R.string.action_done), onClose)
            }
        }
    }
}

@Composable
private fun ConfirmCommandDialog(command: TimerCommand, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, body) = when (command) {
        is TimerCommand.Bonus -> stringResource(R.string.device_bonus_confirm_title, durationText(command.minutes)) to stringResource(R.string.device_bonus_confirm_body)
        TimerCommand.LockNow -> stringResource(R.string.device_lock_now) to stringResource(R.string.device_lock_now_body)
        TimerCommand.EndLock -> stringResource(R.string.device_end_lock) to stringResource(R.string.device_end_lock_body)
        TimerCommand.ApprovePinReset -> stringResource(R.string.device_pin_reset_approve) to stringResource(R.string.device_pin_reset_approve_body)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { NourDialogButton(stringResource(R.string.device_send), onConfirm) },
        dismissButton = { NourDialogButton(stringResource(R.string.action_cancel), onDismiss) },
    )
}
