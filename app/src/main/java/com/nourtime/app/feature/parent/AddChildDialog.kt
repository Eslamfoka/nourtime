package com.nourtime.app.feature.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.CenteredScrollColumn
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.core.designsystem.component.NourTextButton
import com.nourtime.app.remote.model.PairingCode
import com.nourtime.app.remote.parent.ClaimResult
import com.nourtime.app.remote.parent.ParentDevices
import com.nourtime.app.remote.parent.ParentUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AddChildState {
    data object Idle : AddChildState
    data object Working : AddChildState
    data class Done(val result: ClaimResult) : AddChildState
    data object BadCode : AddChildState
}

@HiltViewModel
class AddChildViewModel @Inject constructor(
    private val remote: ParentDevices,
) : ViewModel() {
    private val _state = MutableStateFlow<AddChildState>(AddChildState.Idle)
    val state: StateFlow<AddChildState> = _state.asStateFlow()

    /** [input] is a scanned QR link or a typed code. */
    fun claim(input: String, user: ParentUser) {
        val code = PairingCode.parse(input)
        if (code == null) {
            _state.value = AddChildState.BadCode
            return
        }
        _state.value = AddChildState.Working
        viewModelScope.launch { _state.value = AddChildState.Done(remote.claim(code, user)) }
    }

    fun reset() {
        _state.value = AddChildState.Idle
    }
}

/** Add a child's phone: scan its QR code or type its 6-digit code, then confirm on the child's phone. */
@Composable
fun AddChildDialog(user: ParentUser, onClose: () -> Unit, viewModel: AddChildViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var typed by remember { mutableStateOf("") }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let { viewModel.claim(it, user) } }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CenteredScrollColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.parent_add_child), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    when (val s = state) {
                        AddChildState.Idle, AddChildState.BadCode -> {
                            Text(
                                stringResource(R.string.add_child_body),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            NourPrimaryButton(stringResource(R.string.add_child_scan), ::scan)
                            Text(stringResource(R.string.add_child_or_type), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = typed,
                                onValueChange = { typed = it.take(12) },
                                label = { Text(stringResource(R.string.add_child_code_label)) },
                                singleLine = true,
                                isError = s == AddChildState.BadCode,
                                supportingText = if (s == AddChildState.BadCode) ({ Text(stringResource(R.string.add_child_bad_code)) }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { viewModel.claim(typed, user) }),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            NourSecondaryButton(stringResource(R.string.add_child_connect), { viewModel.claim(typed, user) })
                            NourTextButton(stringResource(R.string.action_cancel), onClose)
                        }
                        AddChildState.Working -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                            Text(stringResource(R.string.add_child_confirm_on_child), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        }
                        is AddChildState.Done -> {
                            Text(stringResource(messageFor(s.result)), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            if (s.result == ClaimResult.PAIRED) {
                                NourPrimaryButton(stringResource(R.string.action_done), onClose)
                            } else {
                                NourPrimaryButton(stringResource(R.string.pair_try_again), viewModel::reset)
                                NourTextButton(stringResource(R.string.action_cancel), onClose)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun messageFor(result: ClaimResult): Int = when (result) {
    ClaimResult.PAIRED -> R.string.add_child_paired
    ClaimResult.NOT_FOUND -> R.string.add_child_not_found
    ClaimResult.EXPIRED -> R.string.add_child_expired
    ClaimResult.ALREADY_CLAIMED -> R.string.add_child_taken
    ClaimResult.WAITING_FOR_CHILD -> R.string.add_child_timeout
    ClaimResult.REFUSED -> R.string.add_child_refused
    ClaimResult.OFFLINE -> R.string.add_child_offline
    ClaimResult.FAILED -> R.string.pair_error
}
