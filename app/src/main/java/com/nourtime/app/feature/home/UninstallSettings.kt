package com.nourtime.app.feature.home

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nourtime.app.R
import com.nourtime.app.core.blocking.ParentPass
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.data.security.SecurityRepository
import com.nourtime.app.feature.pin.AnswerCheckController
import com.nourtime.app.feature.pin.SecurityAnswerPanel
import com.nourtime.app.service.admin.NourDeviceAdminReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class UninstallOutcome { READY, FAILED }

/**
 * Parent-initiated uninstall (Phase 1.5, task 3). The security answer is asked even outside a lock
 * period. Nour Time then removes its own Device admin (Android refuses to uninstall an active
 * admin) and grants the parent pass, so the system uninstall dialog isn't covered. If the parent
 * cancels that dialog, Home shows Device admin as needing attention, with a button to turn it back on.
 */
@HiltViewModel
class UninstallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    security: SecurityRepository,
    clock: DeviceClock,
    private val pass: ParentPass,
) : ViewModel() {

    val answer = AnswerCheckController(viewModelScope, security, clock) { removeProtection() }

    private val _outcome = MutableStateFlow<UninstallOutcome?>(null)
    val outcome: StateFlow<UninstallOutcome?> = _outcome.asStateFlow()

    private fun removeProtection() {
        viewModelScope.launch {
            pass.grantFull()
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            val admin = ComponentName(context, NourDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) runCatching { dpm.removeActiveAdmin(admin) }
            // Removal finishes asynchronously; the uninstall dialog would refuse until it has.
            withTimeoutOrNull(ADMIN_REMOVAL_TIMEOUT_MS) {
                while (dpm.isAdminActive(admin)) delay(100)
            }
            _outcome.value = if (dpm.isAdminActive(admin)) UninstallOutcome.FAILED else UninstallOutcome.READY
        }
    }

    fun consumeOutcome() {
        _outcome.value = null
    }

    private companion object {
        const val ADMIN_REMOVAL_TIMEOUT_MS = 5_000L
    }
}

@Composable
internal fun UninstallDialog(onClose: () -> Unit, viewModel: UninstallViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    LaunchedEffect(outcome) {
        when (outcome ?: return@LaunchedEffect) {
            UninstallOutcome.READY -> runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            UninstallOutcome.FAILED -> Toast.makeText(context, R.string.uninstall_failed, Toast.LENGTH_LONG).show()
        }
        viewModel.consumeOutcome()
        onClose()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                val answer by viewModel.answer.state.collectAsStateWithLifecycle()
                SecurityAnswerPanel(
                    state = answer,
                    onAnswerChange = viewModel.answer::onAnswerChange,
                    onSubmit = viewModel.answer::submit,
                    secondaryText = stringResource(R.string.action_cancel),
                    onSecondary = onClose,
                    title = stringResource(R.string.uninstall_title),
                    body = stringResource(R.string.uninstall_body),
                )
            }
        }
    }
}
