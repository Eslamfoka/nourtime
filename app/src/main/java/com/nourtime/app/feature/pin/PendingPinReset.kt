package com.nourtime.app.feature.pin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.nourtime.app.data.security.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A PIN reset from "Forgot PIN?" waiting on this phone. Until 2026-10-28 only a paired parent's phone
 * was told; on a phone without one nothing showed it, so a reset the parent didn't ask for (perhaps the
 * child's) could start working unnoticed. The unlock screen and Home now show it.
 */
class PendingPinReset(private val scope: CoroutineScope, private val security: SecurityRepository) {

    /** When the waiting PIN starts working (wall-clock ms, for display only), or null when none waits. */
    val activeAt: StateFlow<Long?> = security.pendingPinActiveAt
        // Display only: a settings read error hides the banner rather than closing the screen.
        .catch { emit(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Home only, where the parent has already typed the current PIN. */
    fun cancel() {
        scope.launch { security.cancelPendingPin() }
    }
}

/**
 * The warning card for a waiting PIN reset. [announce]: a live region, for the unlock screen, where the
 * reset can be saved (from the lock screen's "Forgot PIN?") while the screen is open. [onCancel] adds a
 * cancel button (Home, where the parent is already unlocked).
 */
@Composable
fun PendingPinResetBanner(
    activeAtMs: Long,
    @StringRes body: Int,
    modifier: Modifier = Modifier,
    announce: Boolean = false,
    onCancel: (() -> Unit)? = null,
) {
    NourCard(modifier, containerColor = NourTheme.colors.danger.copy(alpha = 0.16f)) {
        // Read as one: title, date and what to do.
        Column(
            Modifier.semantics(mergeDescendants = true) { if (announce) liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = NourTheme.colors.danger)
                Text(stringResource(R.string.pin_reset_pending_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(body, pinResetTime(activeAtMs)), style = MaterialTheme.typography.bodyMedium)
        }
        if (onCancel != null) NourSecondaryButton(stringResource(R.string.pin_reset_pending_cancel), onCancel)
    }
}
