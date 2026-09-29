package com.nourtime.app.feature.language

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.locale.AppLanguage
import com.nourtime.app.core.locale.AppLocales

/** Arabic, English, or the phone's language (standard per-app language; works on older phones too). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageCard() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val current = remember { AppLocales.current(context) }
    NourCard {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AppLanguage.SYSTEM to stringResource(R.string.language_system),
                AppLanguage.ARABIC to stringResource(R.string.language_arabic),
                AppLanguage.ENGLISH to stringResource(R.string.language_english),
            ).forEach { (language, label) ->
                FilterChip(
                    selected = language == current,
                    onClick = { if (language != current) AppLocales.set(activity, language) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}
