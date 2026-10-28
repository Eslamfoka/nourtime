package com.nourtime.app.feature.setup

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nourtime.app.data.apps.Browsers
import com.nourtime.app.data.apps.InstalledApp
import com.nourtime.app.data.apps.InstalledAppsRepository
import com.nourtime.app.data.apps.filterApps
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.Bedtime
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.data.settings.ParentSettings
import com.nourtime.app.data.settings.ParentSettingsRepository
import com.nourtime.app.data.settings.WeekendRules
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

    fun setLimitNewApps(on: Boolean) = launch { repository.setLimitNewApps(on) }

    fun setSoundEnabled(on: Boolean) = launch { repository.setSoundEnabled(on) }

    fun setBedtime(bedtime: Bedtime) = launch { repository.setBedtime(bedtime) }

    fun setWeekend(weekend: WeekendRules) = launch { repository.setWeekend(weekend) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/**
 * One app in a switch list; [checked] means limited, or allowed during the lock, by list. [browser]
 * marks a web browser ([Browsers]).
 */
data class AppRow(val app: InstalledApp, val checked: Boolean, val browser: Boolean = false)

data class AppsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val rows: List<AppRow> = emptyList(),
    val limitedCount: Int = 0,
    /** Browsers on this phone the parent hasn't limited (nor allowed during the lock): the warning's list. */
    val unlimitedBrowsers: List<InstalledApp> = emptyList(),
)

/** The installed-app list with search and on/off switches. */
@HiltViewModel
class AppsViewModel @Inject constructor(
    private val apps: InstalledAppsRepository,
    private val settings: ParentSettingsRepository,
) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val browsers = MutableStateFlow<Set<String>>(emptySet())
    private val query = MutableStateFlow("")
    private var pinned: Set<String> = emptySet()

    val state: StateFlow<AppsUiState> = combine(installed, browsers, query, settings.settings) { list, web, q, s ->
        if (list == null) {
            AppsUiState(query = q)
        } else {
            appsUiState(list, web, q, pinned, s.limitedApps, s.allowedDuringLock)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    init {
        viewModelScope.launch {
            // Pin the already-limited apps to the top once, so rows don't jump while toggling.
            pinned = settings.settings.first().limitedApps
            installed.value = apps.launchableApps()
            browsers.value = runCatching { apps.browserPackages() }.getOrDefault(emptySet())
        }
    }

    /** "Limit browsers": every browser the warning lists, in one tap. */
    fun limitBrowsers() {
        val toLimit = state.value.unlimitedBrowsers.mapTo(HashSet()) { it.packageName }
        if (toLimit.isEmpty()) return
        viewModelScope.launch { settings.limitApps(toLimit) }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setLimited(packageName: String, limited: Boolean) {
        viewModelScope.launch { settings.setAppLimited(packageName, limited) }
    }

    suspend fun icon(packageName: String): ImageBitmap? = apps.icon(packageName)
}

/** The app list as shown: search results with switches and browser labels, and the browsers to suggest. */
internal fun appsUiState(
    list: List<InstalledApp>,
    browsers: Set<String>,
    query: String,
    pinned: Set<String>,
    limited: Set<String>,
    allowedDuringLock: Set<String>,
): AppsUiState {
    val suggest = Browsers.unlimited(browsers, limited, allowedDuringLock)
    return AppsUiState(
        loading = false,
        query = query,
        rows = filterApps(list, query, pinned).map { AppRow(it, it.packageName in limited, browser = it.packageName in browsers) },
        limitedCount = list.count { it.packageName in limited },
        unlimitedBrowsers = list.filter { it.packageName in suggest },
    )
}
