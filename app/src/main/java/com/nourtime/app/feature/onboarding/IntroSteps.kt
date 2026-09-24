package com.nourtime.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.IconBadge
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.core.designsystem.component.NourStar
import com.nourtime.app.core.permissions.NourPermission

@Composable
fun WelcomeStep(onStart: () -> Unit) {
    StepLayout(
        progress = null,
        onBack = null,
        actions = { NourPrimaryButton(stringResource(R.string.welcome_cta), onStart) },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NourStar(Modifier.size(200.dp))
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        FeatureRow(Icons.Rounded.Timer, stringResource(R.string.welcome_point_budget))
        FeatureRow(Icons.Rounded.LockClock, stringResource(R.string.welcome_point_lock))
        FeatureRow(Icons.Rounded.VerifiedUser, stringResource(R.string.welcome_point_private))
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconBadge(icon, size = 44.dp)
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** Google Play "Prominent Disclosure": shown before any permission is requested; needs an explicit "I agree". */
@Composable
fun DisclosureStep(progress: Pair<Int, Int>?, onBack: (() -> Unit)?, onAccept: () -> Unit) {
    StepLayout(
        progress = progress,
        onBack = onBack,
        actions = {
            NourPrimaryButton(stringResource(R.string.disclosure_accept), onAccept)
            if (onBack != null) NourSecondaryButton(stringResource(R.string.disclosure_decline), onBack)
        },
    ) {
        Text(stringResource(R.string.disclosure_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.disclosure_intro), style = MaterialTheme.typography.bodyLarge)
        NourPermission.entries.forEach { permission ->
            val ui = permission.ui
            NourCard {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconBadge(ui.icon, size = 44.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(ui.title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(ui.short),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.disclosure_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun FinishedStep(onFinish: () -> Unit) {
    StepLayout(
        progress = null,
        onBack = null,
        actions = { NourPrimaryButton(stringResource(R.string.finished_cta), onFinish) },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            NourStar(Modifier.size(220.dp))
            Text(
                stringResource(R.string.finished_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.finished_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
