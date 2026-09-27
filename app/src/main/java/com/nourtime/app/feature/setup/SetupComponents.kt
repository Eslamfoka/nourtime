package com.nourtime.app.feature.setup

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Boy
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Girl
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.nourTextFieldColors
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.core.ui.formatDuration
import com.nourtime.app.data.settings.AgeGroup
import com.nourtime.app.data.settings.ChildGender
import com.nourtime.app.data.settings.TimeLimits
import kotlin.math.roundToInt

// ---------- Child profile ----------

@Composable
fun ChildProfileEditor(
    gender: ChildGender?,
    ageGroup: AgeGroup?,
    onGender: (ChildGender) -> Unit,
    onAgeGroup: (AgeGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(stringResource(R.string.profile_gender_label))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceCard(
                selected = gender == ChildGender.BOY,
                onClick = { onGender(ChildGender.BOY) },
                icon = Icons.Rounded.Boy,
                title = stringResource(R.string.profile_boy),
                modifier = Modifier.weight(1f),
            )
            ChoiceCard(
                selected = gender == ChildGender.GIRL,
                onClick = { onGender(ChildGender.GIRL) },
                icon = Icons.Rounded.Girl,
                title = stringResource(R.string.profile_girl),
                modifier = Modifier.weight(1f),
            )
        }
        SectionLabel(stringResource(R.string.profile_age_label))
        AgeGroup.entries.forEach { group ->
            ChoiceCard(
                selected = ageGroup == group,
                onClick = { onAgeGroup(group) },
                title = stringResource(group.titleRes),
                subtitle = stringResource(group.descriptionRes),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

val AgeGroup.titleRes: Int
    get() = when (this) {
        AgeGroup.AGES_3_6 -> R.string.age_3_6
        AgeGroup.AGES_7_9 -> R.string.age_7_9
        AgeGroup.AGES_10_12 -> R.string.age_10_12
    }

private val AgeGroup.descriptionRes: Int
    get() = when (this) {
        AgeGroup.AGES_3_6 -> R.string.age_3_6_desc
        AgeGroup.AGES_7_9 -> R.string.age_7_9_desc
        AgeGroup.AGES_10_12 -> R.string.age_10_12_desc
    }

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun ChoiceCard(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = NourTheme.colors.successText)
        }
    }
}

// ---------- Time budget ----------

private val BudgetPresets = listOf(15, 30, 45, 60, 90, 120)
private val LockPresets = listOf(2, 4, 6, 8, 12)

@Composable
fun TimeBudgetEditor(
    budgetMinutes: Int,
    lockPeriodHours: Int,
    onBudgetMinutes: (Int) -> Unit,
    onLockPeriodHours: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ValueSliderCard(
            title = stringResource(R.string.budget_title),
            hint = stringResource(R.string.budget_hint),
            value = budgetMinutes,
            range = TimeLimits.MIN_BUDGET_MINUTES..TimeLimits.MAX_BUDGET_MINUTES,
            step = TimeLimits.BUDGET_STEP_MINUTES,
            presets = BudgetPresets,
            format = { durationText(it) },
            onValue = onBudgetMinutes,
        )
        ValueSliderCard(
            title = stringResource(R.string.lock_period_title),
            hint = stringResource(R.string.lock_period_hint),
            value = lockPeriodHours,
            range = TimeLimits.MIN_LOCK_HOURS..TimeLimits.MAX_LOCK_HOURS,
            step = 1,
            presets = LockPresets,
            format = { pluralStringResource(R.plurals.duration_hours, it, it) },
            onValue = onLockPeriodHours,
        )
    }
}

/** "45 minutes", "1 hour 30 minutes", "2 hours". */
@Composable
fun durationText(minutes: Int): String {
    LocalConfiguration.current // recompose on locale change
    return LocalContext.current.resources.formatDuration(minutes)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValueSliderCard(
    title: String,
    hint: String,
    value: Int,
    range: IntRange,
    step: Int,
    presets: List<Int>,
    format: @Composable (Int) -> String,
    onValue: (Int) -> Unit,
) {
    // Local while dragging; saved when the finger lifts.
    var dragging by remember { mutableFloatStateOf(Float.NaN) }
    val shown = if (dragging.isNaN()) value else ((dragging / step).roundToInt() * step).coerceIn(range)

    NourCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            format(shown),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                onValue(shown)
                dragging = Float.NaN
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                activeTickColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                inactiveTickColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                FilterChip(
                    selected = shown == preset,
                    onClick = { onValue(preset) },
                    label = { Text(format(preset)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

// ---------- App picker ----------

@Composable
fun AppSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        colors = nourTextFieldColors(),
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        placeholder = { Text(stringResource(R.string.apps_search)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

/** The app list with loading and no-results states. */
@Composable
fun AppList(
    state: AppsUiState,
    onCheckedChange: (packageName: String, checked: Boolean) -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    header: LazyListScope.() -> Unit = {},
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        header()
        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                }
            }
            state.rows.isEmpty() -> item {
                Text(
                    stringResource(R.string.apps_no_results, state.query),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            else -> items(state.rows, key = { it.app.packageName }) { row ->
                AppRowItem(row, onCheckedChange = { onCheckedChange(row.app.packageName, it) }, loadIcon = loadIcon)
            }
        }
    }
}

@Composable
fun AppRowItem(
    row: AppRow,
    onCheckedChange: (Boolean) -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = row.checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(row.app.packageName, loadIcon)
        Text(row.app.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(
            checked = row.checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = NourTheme.colors.success,
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedBorderColor = NourTheme.colors.success,
            ),
        )
    }
}

@Composable
@SuppressLint("ProduceStateDoesNotAssignValue") // assigned after the suspend lookup
private fun AppIcon(packageName: String, loadIcon: suspend (String) -> ImageBitmap?) {
    val icon by produceState<ImageBitmap?>(null, packageName) { value = loadIcon(packageName) }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(44.dp)) }
    }
}
