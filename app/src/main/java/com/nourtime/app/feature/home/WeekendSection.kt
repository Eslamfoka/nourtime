package com.nourtime.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.data.settings.WeekendRules
import com.nourtime.app.feature.setup.TimeBudgetEditor
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * Different limits on weekend days (Phase 4a), on the child's Settings and the parent's phone:
 * which days, and their own budget, lock length and bedtime.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekendSection(weekend: WeekendRules, onChange: (WeekendRules) -> Unit) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val week = (0L until 7L).map { firstDay.plus(it) }

    NourCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.weekend_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.weekend_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NourSwitch(weekend.enabled) { onChange(weekend.copy(enabled = it)) }
        }
        if (weekend.enabled) {
            Text(stringResource(R.string.weekend_days), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                week.forEach { day ->
                    val selected = day in weekend.days
                    FilterChip(
                        selected = selected,
                        // At least one day stays selected; turning the switch off is how to stop.
                        onClick = {
                            val days = if (selected) weekend.days - day else weekend.days + day
                            if (days.isNotEmpty()) onChange(weekend.copy(days = days))
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
    }
    if (weekend.enabled) {
        Text(
            stringResource(R.string.weekend_section, weekDaysText(week.filter { it in weekend.days }, locale)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        TimeBudgetEditor(
            weekend.budgetMinutes,
            weekend.lockPeriodHours,
            onBudgetMinutes = { onChange(weekend.copy(budgetMinutes = it)) },
            onLockPeriodHours = { onChange(weekend.copy(lockPeriodHours = it)) },
        )
        BedtimeCard(
            weekend.bedtime,
            title = stringResource(R.string.weekend_bedtime_title),
            hint = stringResource(R.string.weekend_bedtime_hint),
        ) { onChange(weekend.copy(bedtime = it)) }
    }
}

private fun weekDaysText(days: List<DayOfWeek>, locale: java.util.Locale): String =
    days.joinToString(if (locale.language == "ar") "، " else ", ") { it.getDisplayName(TextStyle.FULL, locale) }
