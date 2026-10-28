package com.nourtime.app.feature.language

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.locale.AppLanguage
import com.nourtime.app.core.locale.AppLocales

/** Arabic, English, or the phone's language (standard per-app language; works on older phones too). */
@Composable
fun LanguageCard() {
    NourCard {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        LanguageChips()
    }
}

/**
 * The same choice for the first screens a new user sees (whose phone, Welcome), so a parent whose phone
 * is in another language can set up in their own. The globe says "Language" to TalkBack before the chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactLanguagePicker(modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Language,
            contentDescription = stringResource(R.string.settings_language),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        LanguageChipItems()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChips() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LanguageChipItems()
    }
}

/**
 * One chip per [AppLanguage]. Choosing one goes through [AppLocales.set]: Android 13+ recreates the
 * screen itself, older phones are recreated by it; either way the step and what was entered are kept
 * (saved settings and view models), and [com.nourtime.app.MainActivity] lays the new language out in its
 * own direction.
 */
@Composable
private fun LanguageChipItems() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val current = remember { AppLocales.current(context) }
    listOf(
        AppLanguage.SYSTEM to AnnotatedString(stringResource(R.string.language_system)),
        AppLanguage.ARABIC to inLanguage(stringResource(R.string.language_arabic), AppLanguage.ARABIC),
        AppLanguage.ENGLISH to inLanguage(stringResource(R.string.language_english), AppLanguage.ENGLISH),
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

/** "العربية" and "English" are always written in their own language; TalkBack reads them in it too. */
private fun inLanguage(label: String, language: AppLanguage): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(localeList = LocaleList(language.tag))) { append(label) }
}
