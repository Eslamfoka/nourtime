package com.nourtime.app.feature.setup

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nourtime.app.data.apps.InstalledApp
import com.nourtime.app.data.apps.InstalledAppsRepository
import com.nourtime.app.data.apps.filterApps
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.Bedtime
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.data.settings.ParentSettings
import com.nourtime.app.data.settings.ParentSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Child profile and time settings, shared by onboarding and the Settings tab. */
@HiltViewModel
class ParentSettingsViewModel @Inject constructor(
    private val repository: ParentSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<ParentSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setGender(gender: ChildGender) = launch { repository.setGender(gender) }

    fun setAgeGroup(ageGroup: AgeGroup) = launch { repository.setAgeGroup(ageGroup) }

    fun setBudgetMinutes(minutes: Int) = launch { repository.setBudgetMinutes(minutes) }

    fun setLockPeriodHours(hours: Int) = launch { repository.setLockPeriodHours(hours) }

    fun setDailyResetMinute(minute: Int?) = launch { repository.setDailyResetMinute(minute) }

    fun setLockType(type: LockType) = launch { repository.setLockType(type) }

    fun setProtectSystemSettings(on: Boolean) = launch { repository.setProtectSystemSettings(on) }

    fun setSoundEnabled(on: Boolean) = launch { repository.setSoundEnabled(on) }

    fun setBedtime(bedtime: Bedtime) = launch { repository.setBedtime(bedtime) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

data class AppRow(val app: InstalledApp, val limited: Boolean)

data class AppsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val rows: List<AppRow> = emptyList(),
    val limitedCount: Int = 0,
)

/** The installed-app list with search and on/off switches. */
@HiltViewModel
class AppsViewModel @Inject constructor(
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
            val limited = s.limitedApps
            AppsUiState(
                loading = false,
                query = q,
                rows = filterApps(list, q, pinned).map { AppRow(it, it.packageName in limited) },
                limitedCount = list.count { it.packageName in limited },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    init {
        viewModelScope.launch {
            // Pin the already-limited apps to the top once, so rows don't jump while toggling.
            pinned = settings.settings.first().limitedApps
            installed.value = apps.launchableApps()
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setLimited(packageName: String, limited: Boolean) {
        viewModelScope.launch { settings.setAppLimited(packageName, limited) }
    }

    suspend fun icon(packageName: String): ImageBitmap? = apps.icon(packageName)
}
