package com.nourtime.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.data.apps.InstalledApp
import com.nourtime.app.data.apps.InstalledAppsRepository
import com.nourtime.app.data.apps.filterApps
import com.nourtime.app.data.settings.ParentSettingsRepository
import com.nourtime.app.feature.setup.AppList
import com.nourtime.app.feature.setup.AppRow
import com.nourtime.app.feature.setup.AppSearchField
import com.nourtime.app.feature.setup.AppsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Educational apps allowed during lock periods and bedtime (Phase 1.5, task 2). Limited apps
 * aren't offered: an app is either limited or allowed.
 */
@HiltViewModel
class AllowedAppsViewModel @Inject constructor(
    private val apps: InstalledAppsRepository,
    private val settings: ParentSettingsRepository,
) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")
    private var pinned: Set<String> = emptySet()

    val state: StateFlow<AppsUiState> = combine(installed, query, settings.settings) { list, q, s ->
        if (list == null) {
            AppsUiState(query = q)
        } else {
            val candidates = list.filter { it.packageName !in s.limitedApps }
            AppsUiState(
                loading = false,
                query = q,
                rows = filterApps(candidates, q, pinned).map { AppRow(it, it.packageName in s.allowedDuringLock) },
                limitedCount = candidates.count { it.packageName in s.allowedDuringLock },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    init {
        viewModelScope.launch {
            // Pin the already-allowed apps to the top once, so rows don't jump while toggling.
            pinned = settings.settings.first().allowedDuringLock
            installed.value = apps.launchableApps()
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch { settings.setAppAllowedDuringLock(packageName, allowed) }
    }

    suspend fun icon(packageName: String): ImageBitmap? = apps.icon(packageName)
}

@Composable
internal fun AllowedDuringLockCard(count: Int) {
    var choosing by remember { mutableStateOf(false) }
    NourCard {
        Text(
            stringResource(R.string.allowed_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (count == 0) stringResource(R.string.allowed_none) else pluralStringResource(R.plurals.apps_selected_count, count, count),
            style = MaterialTheme.typography.titleSmall,
        )
        NourSecondaryButton(stringResource(R.string.allowed_choose), { choosing = true })
    }
    if (choosing) AllowedAppsDialog(onClose = { choosing = false })
}

@Composable
private fun AllowedAppsDialog(onClose: () -> Unit, viewModel: AllowedAppsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.allowed_section), style = MaterialTheme.typography.headlineSmall)
                Text(
                    pluralStringResource(R.plurals.apps_selected_count, state.limitedCount, state.limitedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppSearchField(state.query, viewModel::onQueryChange)
                AppList(
                    state = state,
                    onCheckedChange = viewModel::setAllowed,
                    loadIcon = viewModel::icon,
                    modifier = Modifier.weight(1f),
                )
                NourPrimaryButton(stringResource(R.string.action_done), onClose)
            }
        }
    }
}
