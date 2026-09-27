package com.nourtime.app.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.blocking.BlockDecision
import com.nourtime.app.core.blocking.BlockReason
import com.nourtime.app.core.designsystem.component.FullScreenDialog
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.feature.setup.titleRes

/** Lets the parent see exactly what the child will see, for each period and age (brief §4). */
@Composable
fun TimeUpPreviewDialog(ageGroup: AgeGroup, gender: ChildGender, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(TemplateKind.DEFAULT) }
    var age by remember { mutableStateOf(ageGroup) }
    FullScreenDialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxSize()) {
            TimeUpScreen(
                state = LockScreenState(
                    decision = BlockDecision(BlockReason.TIME_UP, wholeDevice = false, needsSecurityAnswer = false),
                    template = kind,
                    ageGroup = age,
                    gender = gender,
                    countdownMs = if (kind == TemplateKind.PARENTS_ONLY) null else 2 * 60 * 60_000L + 15 * 60_000L,
                    soundEnabled = false,
                ),
                onOk = onDismiss,
                onParents = onDismiss,
            )
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ChipRow(TemplateKind.entries.filter { it != TemplateKind.PARENTS_ONLY }, kind, { kind = it }) { stringResource(it.labelRes) }
                ChipRow(AgeGroup.entries, age, { age = it }) { stringResource(it.titleRes) }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(items: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

private val TemplateKind.labelRes: Int
    get() = when (this) {
        TemplateKind.DEFAULT -> R.string.preview_default
        TemplateKind.PLAY -> R.string.period_play
        TemplateKind.STUDY -> R.string.period_study
        TemplateKind.MEAL -> R.string.period_meal
        TemplateKind.SLEEP -> R.string.period_sleep
        TemplateKind.PARENTS_ONLY -> R.string.preview_default
    }
