package com.nourtime.app.feature.remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.CenteredScrollColumn
import com.nourtime.app.core.designsystem.component.NourCard
import com.nourtime.app.core.designsystem.component.NourDangerButton
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourSecondaryButton
import com.nourtime.app.core.designsystem.component.NourTextButton
import com.nourtime.app.core.ui.formatCountdown
import com.nourtime.app.remote.child.ChildPairing
import com.nourtime.app.remote.child.DeviceIdentity
import com.nourtime.app.remote.child.PairedOwner
import com.nourtime.app.remote.child.PairingError
import com.nourtime.app.remote.child.PairingState
import com.nourtime.app.remote.child.PairingStates
import com.nourtime.app.remote.model.PairingCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Parent's phone" in the child's Settings (Phase 2): pair, see who controls it, disconnect. */
@HiltViewModel
class ParentPhoneViewModel @Inject constructor(
    identity: DeviceIdentity,
    private val pairing: ChildPairing,
) : ViewModel() {

    val owner: StateFlow<PairedOwner?> = identity.pairedOwner.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow<PairingState?>(null)
    /** Null while no pairing dialog is open. */
    val state: StateFlow<PairingState?> = _state.asStateFlow()

    private var job: Job? = null
    private var code: String? = null
    private var claim: PairedOwner? = null

    fun start() {
        job?.cancel()
        claim = null
        _state.value = PairingState.Starting
        job = viewModelScope.launch {
            val newCode = try {
                pairing.createCode()
            } catch (e: Exception) {
                _state.value = PairingState.Failed(if (e.isOffline()) PairingError.OFFLINE else PairingError.OTHER)
                return@launch
            }
            code = newCode
            val localStart = System.currentTimeMillis()
            val ticks = flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(1_000)
                }
            }
            try {
                combine(pairing.observe(newCode), ticks) { snap, now ->
                    claim = snap?.getString("claimedBy")?.let { PairedOwner(it, snap.getString("claimedEmail"), snap.getString("claimedName")) }
                    PairingStates.of(
                        code = newCode,
                        exists = snap != null,
                        claimedBy = snap?.getString("claimedBy"),
                        claimedName = snap?.getString("claimedName"),
                        claimedEmail = snap?.getString("claimedEmail"),
                        createdAtMs = snap?.getTimestamp("createdAt")?.toDate()?.time,
                        localStartMs = localStart,
                        nowMs = now,
                    )
                }.collect { next ->
                    _state.value = next
                    if (next == PairingState.Expired) {
                        pairing.forget(newCode)
                        job?.cancel()
                    }
                }
            } catch (e: Exception) {
                if (_state.value !is PairingState.Paired) {
                    _state.value = PairingState.Failed(if (e.isOffline()) PairingError.OFFLINE else PairingError.OTHER)
                }
            }
        }
    }

    fun allow() {
        val owner = claim ?: return
        val c = code ?: return
        job?.cancel()
        viewModelScope.launch {
            try {
                pairing.confirm(c, owner)
                _state.value = PairingState.Paired
            } catch (e: Exception) {
                _state.value = PairingState.Failed(if (e.isOffline()) PairingError.OFFLINE else PairingError.OTHER)
            }
        }
    }

    /** Refuse the claim, or close the dialog: the code is removed either way. */
    fun close() {
        job?.cancel()
        val c = code
        val paired = _state.value == PairingState.Paired
        code = null
        _state.value = null
        if (c != null && !paired) viewModelScope.launch { pairing.forget(c) }
    }

    fun disconnect() {
        viewModelScope.launch { pairing.disconnect() }
    }

    private fun Exception.isOffline(): Boolean =
        this is FirebaseNetworkException ||
            (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE)
}

@Composable
fun ParentPhoneSection(viewModel: ParentPhoneViewModel = hiltViewModel()) {
    val owner by viewModel.owner.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDisconnect by remember { mutableStateOf(false) }

    NourCard {
        val current = owner
        if (current == null) {
            Text(
                stringResource(R.string.remote_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NourSecondaryButton(stringResource(R.string.remote_connect), viewModel::start)
        } else {
            Text(
                stringResource(R.string.remote_connected, current.email ?: current.name ?: current.uid),
                style = MaterialTheme.typography.titleSmall,
            )
            NourTextButton(stringResource(R.string.remote_disconnect), { confirmDisconnect = true })
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(stringResource(R.string.remote_disconnect_title)) },
            text = { Text(stringResource(R.string.remote_disconnect_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnect()
                }) { Text(stringResource(R.string.remote_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    state?.let { PairingDialog(it, onAllow = viewModel::allow, onRetry = viewModel::start, onClose = viewModel::close) }
}

@Composable
private fun PairingDialog(state: PairingState, onAllow: () -> Unit, onRetry: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CenteredScrollColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    when (state) {
                        PairingState.Starting -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                            NourTextButton(stringResource(R.string.action_cancel), onClose)
                        }
                        is PairingState.Waiting -> {
                            WaitingContent(state)
                            NourTextButton(stringResource(R.string.action_cancel), onClose)
                        }
                        is PairingState.Claimed -> {
                            Text(stringResource(R.string.pair_claimed_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                            Text(
                                stringResource(R.string.pair_claimed_body, listOfNotNull(state.name, state.email).joinToString(" · ")),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            NourPrimaryButton(stringResource(R.string.pair_allow), onAllow)
                            NourDangerButton(stringResource(R.string.pair_deny), onClose)
                        }
                        PairingState.Paired -> {
                            Text(stringResource(R.string.pair_done), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            NourPrimaryButton(stringResource(R.string.action_done), onClose)
                        }
                        PairingState.Expired -> {
                            Text(stringResource(R.string.pair_expired), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            NourPrimaryButton(stringResource(R.string.pair_new_code), onRetry)
                            NourTextButton(stringResource(R.string.action_cancel), onClose)
                        }
                        is PairingState.Failed -> {
                            Text(
                                stringResource(if (state.reason == PairingError.OFFLINE) R.string.pair_offline else R.string.pair_error),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            NourPrimaryButton(stringResource(R.string.pair_try_again), onRetry)
                            NourTextButton(stringResource(R.string.action_cancel), onClose)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingContent(state: PairingState.Waiting) {
    val sizePx = with(LocalDensity.current) { 240.dp.roundToPx() }
    val qr = remember(state.code, sizePx) { QrCode.image(PairingCode.uri(state.code), sizePx) }
    Text(
        stringResource(R.string.pair_scan),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Image(qr, contentDescription = stringResource(R.string.pair_qr_description), modifier = Modifier.size(240.dp))
    Text(stringResource(R.string.pair_or_type), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        state.code.chunked(3).joinToString(" "),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
    )
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.expiresAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(
        stringResource(R.string.pair_expires, formatCountdown((state.expiresAtMs - now).coerceAtLeast(0))),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.pair_waiting), style = MaterialTheme.typography.titleSmall)
}
