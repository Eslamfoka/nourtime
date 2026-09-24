package com.nourtime.app.feature.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourStar
import com.nourtime.app.core.designsystem.component.NourFace
import com.nourtime.app.core.designsystem.component.StatusPill
import com.nourtime.app.core.permissions.NourPermission
import com.nourtime.app.core.permissions.PermissionChecker
import com.nourtime.app.core.ui.startFirstAvailable
import com.nourtime.app.feature.onboarding.ui
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val permissions: PermissionChecker,
) : ViewModel() {
    private val _status = MutableStateFlow(permissions.statusOfAll())
    val status: StateFlow<Map<NourPermission, Boolean>> = _status.asStateFlow()

    fun refresh() {
        _status.value = permissions.statusOfAll()
    }

    fun settingsIntents(permission: NourPermission) = permissions.settingsIntents(permission)
}

/** Placeholder home for step 1: confirms setup and keeps an eye on permissions. Replaced in step 2. */
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NourCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NourStar(Modifier.size(88.dp), face = NourFace.CLOCK)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_ready_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.home_ready_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text(stringResource(R.string.home_permissions_title), style = MaterialTheme.typography.titleLarge)
        if (status.values.any { !it }) {
            Text(
                stringResource(R.string.home_permissions_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NourCard {
            NourPermission.entries.forEach { permission ->
                val granted = status[permission] == true
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(permission.ui.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(permission.ui.title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (granted) {
                        StatusPill(true, stringResource(R.string.status_allowed), "")
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (!context.startFirstAvailable(viewModel.settingsIntents(permission))) {
                                    Toast.makeText(context, R.string.cannot_open_settings, Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        ) { Text(stringResource(R.string.action_fix)) }
                    }
                }
            }
        }
    }
}
