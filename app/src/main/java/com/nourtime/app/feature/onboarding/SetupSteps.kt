package com.nourtime.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.feature.home.LockTypeEditor
import com.nourtime.app.feature.setup.AppList
import com.nourtime.app.feature.setup.AppSearchField
import com.nourtime.app.feature.setup.AppsViewModel
import com.nourtime.app.feature.setup.ChildProfileEditor
import com.nourtime.app.feature.setup.ParentSettingsViewModel
import com.nourtime.app.feature.setup.TimeBudgetEditor

@Composable
fun ChildProfileStep(
    progress: Pair<Int, Int>?,
    onBack: (() -> Unit)?,
    onContinue: () -> Unit,
    viewModel: ParentSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = settings
    StepLayout(
        progress = progress,
        onBack = onBack,
        actions = {
            NourPrimaryButton(
                stringResource(R.string.action_continue),
                onContinue,
                enabled = s?.gender != null && s.ageGroup != null,
            )
        },
    ) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.profile_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (s != null) {
            ChildProfileEditor(s.gender, s.ageGroup, viewModel::setGender, viewModel::setAgeGroup)
        }
    }
}

@Composable
fun SelectAppsStep(
    progress: Pair<Int, Int>?,
    onBack: (() -> Unit)?,
    onContinue: () -> Unit,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StepLayout(
        progress = progress,
        onBack = onBack,
        scrollable = false,
        actions = {
            Text(
                pluralStringResource(R.plurals.apps_selected_count, state.limitedCount, state.limitedCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NourPrimaryButton(stringResource(R.string.action_continue), onContinue, enabled = state.limitedCount > 0)
        },
    ) {
        // Title and search scroll with the list so small screens show more apps.
        AppList(
            state = state,
            onLimitedChange = viewModel::setLimited,
            loadIcon = viewModel::icon,
            modifier = Modifier.weight(1f),
            header = {
                item {
                    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.apps_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AppSearchField(state.query, viewModel::onQueryChange)
                    }
                }
            },
        )
    }
}

@Composable
fun TimeBudgetStep(
    progress: Pair<Int, Int>?,
    onBack: (() -> Unit)?,
    onContinue: () -> Unit,
    viewModel: ParentSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    StepLayout(
        progress = progress,
        onBack = onBack,
        actions = { NourPrimaryButton(stringResource(R.string.action_continue), onContinue, enabled = settings != null) },
    ) {
        Text(stringResource(R.string.budget_step_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.budget_step_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        settings?.let { s ->
            TimeBudgetEditor(
                budgetMinutes = s.budgetMinutes,
                lockPeriodHours = s.lockPeriodHours,
                onBudgetMinutes = viewModel::setBudgetMinutes,
                onLockPeriodHours = viewModel::setLockPeriodHours,
            )
            Text(stringResource(R.string.settings_lock_section), style = MaterialTheme.typography.titleMedium)
            LockTypeEditor(s.lockType, viewModel::setLockType)
        }
    }
}
