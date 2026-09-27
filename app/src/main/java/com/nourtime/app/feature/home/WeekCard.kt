package com.nourtime.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.data.usage.WeekReport
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * The last 7 days of use (Phase 4b), on the child's Home and the parent's phone: a bar per day
 * (today in gold), the week's total, the daily average, the most used app and the change from the
 * week before. [topAppName] is the label of [WeekReport.topApp], resolved by the caller.
 */
@Composable
fun WeekCard(report: WeekReport, topAppName: String?) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val max = report.days.maxOf { it.totalMs }.coerceAtLeast(1)
    NourCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.stats_week), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(usageText(report.totalMs), style = MaterialTheme.typography.titleMedium)
        }
        val chartDescription = report.days.map { day ->
            "${day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} ${usageText(day.totalMs)}"
        }.joinToString(", ")
        Row(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clearAndSetSemantics { contentDescription = chartDescription },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            report.days.forEachIndexed { i, day ->
                val isToday = i == report.days.lastIndex
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val fraction = (day.totalMs.toFloat() / max).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(if (day.totalMs > 0) fraction.coerceAtLeast(0.04f) else 0.02f)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                                    RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                                ),
                        )
                    }
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.stats_week_average, usageText(report.averagePerDayMs)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (report.topApp != null) {
            Text(
                stringResource(R.string.stats_week_top, topAppName ?: report.topApp, usageText(report.topAppMs)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        report.changePercent?.let { change ->
            Text(
                when {
                    change > 0 -> stringResource(R.string.stats_week_more, change)
                    change < 0 -> stringResource(R.string.stats_week_less, abs(change))
                    else -> stringResource(R.string.stats_week_same)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
