package com.nourtime.app.feature.home

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.data.settings.Bedtime
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.feature.setup.ChoiceCard
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "7:00 AM" / "07:00" in the user's locale. */
fun formatMinuteOfDay(minute: Int): String =
    LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

fun showTimePicker(context: Context, initialMinute: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(h * 60 + m) },
        initialMinute / 60,
        initialMinute % 60,
        DateFormat.is24HourFormat(context),
    ).show()
}

@Composable
fun NourSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = NourTheme.colors.success,
            checkedThumbColor = MaterialTheme.colorScheme.surface,
            checkedBorderColor = NourTheme.colors.success,
        ),
    )
}

@Composable
internal fun ToggleCard(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    NourCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NourSwitch(checked, onChange)
        }
    }
}

/** Lock type (brief §3): only the selected apps, or the whole phone. */
@Composable
fun LockTypeEditor(lockType: LockType, onChange: (LockType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(
            selected = lockType == LockType.SELECTED_APPS,
            onClick = { onChange(LockType.SELECTED_APPS) },
            title = stringResource(R.string.lock_type_apps),
            subtitle = stringResource(R.string.lock_type_apps_desc),
            modifier = Modifier.fillMaxWidth(),
        )
        ChoiceCard(
            selected = lockType == LockType.WHOLE_DEVICE,
            onClick = { onChange(LockType.WHOLE_DEVICE) },
            title = stringResource(R.string.lock_type_device),
            subtitle = stringResource(R.string.lock_type_device_desc),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Optional bedtime (brief §5). */
@Composable
internal fun BedtimeCard(
    bedtime: Bedtime,
    title: String = stringResource(R.string.bedtime_title),
    hint: String = stringResource(R.string.bedtime_hint),
    onChange: (Bedtime) -> Unit,
) {
    val context = LocalContext.current
    NourCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NourSwitch(bedtime.enabled) { onChange(bedtime.copy(enabled = it)) }
        }
        if (bedtime.enabled) {
            Text(
                stringResource(R.string.bedtime_window, formatMinuteOfDay(bedtime.startMinute), formatMinuteOfDay(bedtime.endMinute)),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showTimePicker(context, bedtime.startMinute) { onChange(bedtime.copy(startMinute = it)) } },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.bedtime_start)) }
                OutlinedButton(
                    onClick = { showTimePicker(context, bedtime.endMinute) { onChange(bedtime.copy(endMinute = it)) } },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.bedtime_end)) }
            }
        }
    }
}
