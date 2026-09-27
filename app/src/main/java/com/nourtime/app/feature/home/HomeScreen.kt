package com.nourtime.app.feature.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourDangerButton
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.core.designsystem.component.StatusPill
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.core.permissions.NourPermission
import com.nourtime.app.core.permissions.PermissionChecker
import com.nourtime.app.core.ui.startFirstAvailable
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.lock.TimeUpPreviewDialog
import com.nourtime.app.feature.remote.ParentPhoneSection
import com.nourtime.app.feature.onboarding.ui
import com.nourtime.app.feature.schedule.ScheduleTab
import com.nourtime.app.feature.setup.AppList
import com.nourtime.app.feature.setup.AppSearchField
import com.nourtime.app.feature.setup.AppsViewModel
import com.nourtime.app.feature.setup.ChildProfileEditor
import com.nourtime.app.feature.setup.ParentSettingsViewModel
import com.nourtime.app.feature.setup.TimeBudgetEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissions: PermissionChecker,
) : ViewModel() {
    private val _status = MutableStateFlow(permissions.statusOfAll())
    val status: StateFlow<Map<NourPermission, Boolean>> = _status.asStateFlow()

    fun refresh() {
        _status.value = permissions.statusOfAll()
    }

    fun settingsIntents(permission: NourPermission) = permissions.settingsIntents(permission)
}

private enum class Tab(val icon: ImageVector, val label: Int) {
    HOME(Icons.Rounded.Home, R.string.nav_home),
    APPS(Icons.Rounded.Apps, R.string.nav_apps),
    SCHEDULE(Icons.Rounded.CalendarMonth, R.string.nav_schedule),
    SETTINGS(Icons.Rounded.Settings, R.string.nav_settings),
}

/** Parent UI with the brief's bottom bar: Home, Apps, Schedule, Settings. */
@Composable
fun MainRoute() {
    var tab by rememberSaveable { mutableIntStateOf(Tab.HOME.ordinal) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        when (Tab.entries[tab]) {
            Tab.HOME -> HomeTab(padding)
            Tab.APPS -> AppsTab(padding)
            Tab.SCHEDULE -> ScheduleTab(padding)
            Tab.SETTINGS -> SettingsTab(padding)
        }
    }
}

@Composable
internal fun TabColumn(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
internal fun PermissionsSection(viewModel: PermissionsViewModel) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Text(stringResource(R.string.home_permissions_title), style = MaterialTheme.typography.titleLarge)
    if (status.values.any { !it }) {
        Text(
            stringResource(R.string.home_permissions_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    NourCard {
        NourPermission.entries.forEach { permission ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(permission.ui.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(permission.ui.title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (status[permission] == true) {
                    StatusPill(true, stringResource(R.string.status_allowed), "")
                } else {
                    OutlinedButton(
                        onClick = {
                            if (!context.startFirstAvailable(viewModel.settingsIntents(permission))) {
                                Toast.makeText(context, R.string.cannot_open_settings, Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) { Text(stringResource(R.string.action_fix)) }
                }
            }
        }
    }
}

@Composable
private fun AppsTab(padding: PaddingValues, viewModel: AppsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppList(
        state = state,
        onCheckedChange = viewModel::setLimited,
        loadIcon = viewModel::icon,
        modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp),
        header = {
            item {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.nav_apps), style = MaterialTheme.typography.headlineMedium)
                    if (!state.loading && state.limitedCount == 0) {
                        NourCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text(stringResource(R.string.apps_none_selected), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text(
                            pluralStringResource(R.plurals.apps_selected_count, state.limitedCount, state.limitedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppSearchField(state.query, viewModel::onQueryChange)
                }
            }
        },
    )
}

@Composable
private fun SettingsTab(padding: PaddingValues, viewModel: ParentSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    TabColumn(padding) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
        settings?.let { s ->
            Text(stringResource(R.string.settings_child_section), style = MaterialTheme.typography.titleLarge)
            ChildProfileEditor(s.gender, s.ageGroup, viewModel::setGender, viewModel::setAgeGroup)
            Text(stringResource(R.string.settings_time_section), style = MaterialTheme.typography.titleLarge)
            TimeBudgetEditor(s.budgetMinutes, s.lockPeriodHours, viewModel::setBudgetMinutes, viewModel::setLockPeriodHours)
            DailyResetCard(s.dailyResetMinute, viewModel::setDailyResetMinute)
            Text(stringResource(R.string.settings_lock_section), style = MaterialTheme.typography.titleLarge)
            LockTypeEditor(s.lockType, viewModel::setLockType)
            BedtimeCard(s.bedtime, viewModel::setBedtime)
            Text(stringResource(R.string.allowed_section), style = MaterialTheme.typography.titleLarge)
            AllowedDuringLockCard(s.allowedDuringLock.size)
            Text(stringResource(R.string.settings_protection_section), style = MaterialTheme.typography.titleLarge)
            ToggleCard(stringResource(R.string.protect_settings_title), stringResource(R.string.protect_settings_hint), s.protectSystemSettings, viewModel::setProtectSystemSettings)
            ToggleCard(stringResource(R.string.sound_title), stringResource(R.string.sound_hint), s.soundEnabled, viewModel::setSoundEnabled)
            Text(stringResource(R.string.settings_security_section), style = MaterialTheme.typography.titleLarge)
            var securityEdit by remember { mutableStateOf<SecurityEdit?>(null) }
            NourSecondaryButton(stringResource(R.string.change_pin), { securityEdit = SecurityEdit.PIN })
            NourSecondaryButton(stringResource(R.string.change_question), { securityEdit = SecurityEdit.QUESTION })
            securityEdit?.let { edit -> SecurityEditDialog(edit, onClose = { securityEdit = null }) }
            var previewing by remember { mutableStateOf(false) }
            NourSecondaryButton(stringResource(R.string.preview_button), { previewing = true })
            if (previewing) {
                TimeUpPreviewDialog(s.ageGroup ?: AgeGroup.AGES_3_6, s.gender ?: ChildGender.GIRL) { previewing = false }
            }
            Text(stringResource(R.string.remote_section), style = MaterialTheme.typography.titleLarge)
            ParentPhoneSection()
            var uninstalling by remember { mutableStateOf(false) }
            NourDangerButton(stringResource(R.string.uninstall_button), { uninstalling = true })
            if (uninstalling) UninstallDialog(onClose = { uninstalling = false })
        }
    }
}

/** Optional daily refill at a time the parent picks (brief §2). */
@Composable
private fun DailyResetCard(minute: Int?, onChange: (Int?) -> Unit) {
    val context = LocalContext.current
    NourCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.daily_reset_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.daily_reset_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = minute != null,
                onCheckedChange = { on -> onChange(if (on) DEFAULT_RESET_MINUTE else null) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = NourTheme.colors.success,
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedBorderColor = NourTheme.colors.success,
                ),
            )
        }
        if (minute != null) {
            val time = formatMinuteOfDay(minute)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.daily_reset_at, time), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { showTimePicker(context, minute, onChange) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text(stringResource(R.string.action_change_time)) }
            }
        }
    }
}

private const val DEFAULT_RESET_MINUTE = 7 * 60
