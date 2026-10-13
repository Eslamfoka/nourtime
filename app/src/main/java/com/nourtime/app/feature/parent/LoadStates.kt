package com.nourtime.app.feature.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.core.designsystem.theme.NourTheme
import kotlinx.coroutines.delay

/**
 * A spinner with what is being loaded, for the parent's screens. Offline, loading never ends (the
 * server is waited for), so after [SLOW_AFTER_MS] it also says to check the connection.
 */
@Composable
internal fun LoadingRow(text: String, modifier: Modifier = Modifier) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SLOW_AFTER_MS)
        slow = true
    }
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.secondary, strokeWidth = 3.dp)
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (slow) {
            Text(
                stringResource(R.string.parent_loading_slow),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private const val SLOW_AFTER_MS = 10_000L

/** "Couldn't load" with a retry button, instead of an empty or blank screen. */
@Composable
internal fun LoadFailedCard(text: String, onRetry: () -> Unit) {
    NourCard {
        Row(
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = NourTheme.colors.danger)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
        NourSecondaryButton(stringResource(R.string.pair_try_again), onRetry)
    }
}

/**
 * A short error line under the part of the screen whose action failed. The theme's error color (the
 * deep coral on light screens): the light coral of [NourTheme.colors] is too pale for small text.
 */
@Composable
internal fun ActionErrorText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
