package com.nourtime.app.feature.onboarding

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.IconBadge
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.core.designsystem.component.NourTextButton
import com.nourtime.app.core.designsystem.component.StatusPill
import com.nourtime.app.core.permissions.NourPermission
import com.nourtime.app.core.permissions.OemBrand

/** One permission: why it's needed, how to grant it, live status. Continue unlocks once granted. */
@Composable
fun PermissionStep(
    progress: Pair<Int, Int>?,
    onBack: (() -> Unit)?,
    permission: NourPermission,
    granted: Boolean,
    onGrant: () -> Unit,
    onContinue: () -> Unit,
    onSkip: (() -> Unit)?,
    onOpenAppInfo: (() -> Unit)?,
) {
    val ui = permission.ui
    StepLayout(
        progress = progress,
        onBack = onBack,
        actions = {
            if (granted) {
                NourPrimaryButton(stringResource(R.string.action_continue), onContinue)
            } else {
                NourPrimaryButton(stringResource(R.string.action_open_settings), onGrant)
                if (onSkip != null) NourTextButton(stringResource(R.string.action_skip_for_now), onSkip)
            }
        },
    ) {
        StepHeader(ui.icon, stringResource(ui.title))
        StatusPill(
            done = granted,
            doneText = stringResource(R.string.status_allowed),
            pendingText = stringResource(R.string.status_not_allowed),
        )
        Text(stringResource(ui.why), style = MaterialTheme.typography.bodyLarge)
        HowToCard(stringResource(ui.how))
        // Sideloaded apps on Android 13+ can't enable accessibility until "restricted settings" are allowed.
        if (onOpenAppInfo != null && !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NourCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Text(stringResource(R.string.perm_accessibility_restricted), style = MaterialTheme.typography.bodyMedium)
                NourSecondaryButton(stringResource(R.string.perm_open_app_info), onOpenAppInfo)
            }
        }
    }
}

/** OEM autostart / battery limits can't be detected, so the parent confirms it themselves. */
@Composable
fun AutostartStep(
    progress: Pair<Int, Int>?,
    onBack: (() -> Unit)?,
    manufacturer: String,
    brand: OemBrand?,
    onOpen: () -> Unit,
    onDone: () -> Unit,
) {
    StepLayout(
        progress = progress,
        onBack = onBack,
        actions = {
            NourPrimaryButton(stringResource(R.string.action_open_settings), onOpen)
            NourSecondaryButton(stringResource(R.string.autostart_done), onDone)
        },
    ) {
        val samsung = brand == OemBrand.SAMSUNG
        StepHeader(Icons.Rounded.RestartAlt, stringResource(if (samsung) R.string.autostart_title_samsung else R.string.autostart_title))
        Text(
            if (samsung) stringResource(R.string.autostart_why_samsung) else stringResource(R.string.autostart_why, manufacturer),
            style = MaterialTheme.typography.bodyLarge,
        )
        HowToCard(
            stringResource(
                when (brand) {
                    OemBrand.XIAOMI -> R.string.autostart_how_xiaomi
                    OemBrand.OPPO -> R.string.autostart_how_oppo
                    OemBrand.VIVO -> R.string.autostart_how_vivo
                    OemBrand.HONOR_HUAWEI -> R.string.autostart_how_honor_huawei
                    OemBrand.ONEPLUS -> R.string.autostart_how_oneplus
                    OemBrand.SAMSUNG -> R.string.autostart_how_samsung
                    null -> R.string.autostart_how
                },
            ),
        )
    }
}

/**
 * Last check before finishing: the parent opens a limited app and Nour Time confirms it saw it
 * (and comes back to this screen by itself). Skippable, e.g. when no limited app can be opened.
 */
@Composable
fun TestProtectionStep(
    progress: Pair<Int, Int>?,
    onBack: (() -> Unit)?,
    appLabel: String?,
    detectedLabel: String?,
    onOpenApp: () -> Unit,
    onContinue: () -> Unit,
) {
    val detected = detectedLabel != null
    StepLayout(
        progress = progress,
        onBack = onBack,
        actions = {
            when {
                detected -> NourPrimaryButton(stringResource(R.string.action_continue), onContinue)
                appLabel != null -> {
                    NourPrimaryButton(stringResource(R.string.test_open_app, appLabel), onOpenApp)
                    NourTextButton(stringResource(R.string.action_skip_for_now), onContinue)
                }
                else -> NourPrimaryButton(stringResource(R.string.action_continue), onContinue)
            }
        },
    ) {
        StepHeader(Icons.Rounded.VerifiedUser, stringResource(R.string.test_title))
        StatusPill(
            done = detected,
            doneText = stringResource(R.string.test_detected, detectedLabel.orEmpty()),
            pendingText = stringResource(R.string.test_waiting),
        )
        Text(
            stringResource(
                when {
                    detected -> R.string.test_success
                    appLabel != null -> R.string.test_body
                    else -> R.string.test_no_app
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        IconBadge(icon, size = 72.dp)
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun HowToCard(text: String) {
    NourCard(containerColor = MaterialTheme.colorScheme.surface) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        }
    }
}
