package com.nourtime.app.feature.learning

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.FullScreenDialog
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LearningSettings
import com.nourtime.app.core.time.TrustedClock
import com.nourtime.app.data.learning.LearningRepository
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.home.NourSwitch
import com.nourtime.app.feature.setup.durationText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Arabic when the app shows Arabic, otherwise English. */
@Composable
fun currentLearnLanguage(): LearnLanguage =
    if (LocalContext.current.resources.configuration.locales[0].language == "ar") LearnLanguage.ARABIC else LearnLanguage.ENGLISH

@HiltViewModel
class LearningSettingsViewModel @Inject constructor(
    val repository: LearningRepository,
    private val trustedClock: TrustedClock,
) : ViewModel() {
    val state: StateFlow<LearningState?> = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _today = kotlinx.coroutines.flow.MutableStateFlow<LocalDate?>(null)
    val today: StateFlow<LocalDate?> = _today

    init {
        viewModelScope.launch { _today.value = trustedClock.now().toLocalDate() }
    }

    fun setEnabled(on: Boolean) = viewModelScope.launch { repository.setEnabled(on) }
    fun setMinutesPerLevel(m: Int) = viewModelScope.launch { repository.setMinutesPerLevel(m) }
    fun setDailyMax(m: Int) = viewModelScope.launch { repository.setDailyMax(m) }
}

/** Parent's Settings: Learning Hub on/off, minutes per level, daily maximum, and a preview. */
@Composable
fun LearningSettingsCard(ageGroup: AgeGroup?, gender: ChildGender?, viewModel: LearningSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val s = state?.settings ?: return
    var previewing by remember { mutableStateOf(false) }
    NourCard {
        Row {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.learn_settings_toggle_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.learn_settings_toggle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NourSwitch(s.enabled, viewModel::setEnabled)
        }
        if (s.enabled) {
            Text(stringResource(R.string.learn_settings_per_level), style = MaterialTheme.typography.titleSmall)
            MinuteChips(LearningSettings.MINUTES_PER_LEVEL_OPTIONS, s.minutesPerLevel, viewModel::setMinutesPerLevel)
            Text(stringResource(R.string.learn_settings_daily_max), style = MaterialTheme.typography.titleSmall)
            MinuteChips(LearningSettings.DAILY_MAX_OPTIONS, s.dailyMaxMinutes, viewModel::setDailyMax)
            today?.let { day ->
                Text(
                    stringResource(R.string.learn_settings_earned_today, durationText(state?.earnedOn(day) ?: 0)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.learn_settings_voice_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NourSecondaryButton(stringResource(R.string.learn_settings_try), { previewing = true })
    }
    if (previewing) LearningPreviewDialog(viewModel.repository, ageGroup, gender ?: ChildGender.GIRL) { previewing = false }
}

@Composable
private fun MinuteChips(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { m ->
            FilterChip(
                selected = m == selected,
                onClick = { onSelect(m) },
                label = { Text(if (m == 0) stringResource(R.string.learn_settings_daily_none) else durationText(m)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** The hub exactly as the child sees it, without earning minutes. */
@Composable
fun LearningPreviewDialog(repository: LearningRepository, ageGroup: AgeGroup?, gender: ChildGender, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val language = currentLearnLanguage()
    val controller = remember {
        LearningHubController(
            scope = scope,
            repo = repository,
            age = ageGroup,
            appLanguage = language,
            rewards = false,
            today = { LocalDate.now() },
        )
    }
    FullScreenDialog(onDismissRequest = { if (!controller.back()) onDismiss() }) {
        Box(Modifier.fillMaxSize()) {
            LearningHub(controller, gender, onClose = onDismiss)
        }
    }
}
