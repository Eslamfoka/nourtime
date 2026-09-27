package com.nourtime.app.feature.parent

import android.content.Context
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.CenteredScrollColumn
import com.nourtime.app.core.designsystem.component.IconBadge
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourStar
import com.nourtime.app.core.designsystem.component.NourTextButton
import com.nourtime.app.core.designsystem.theme.NourTheme
import com.nourtime.app.feature.setup.durationText
import com.nourtime.app.remote.parent.ChildDevice
import com.nourtime.app.remote.parent.DeviceSummary
import com.nourtime.app.remote.parent.ParentAuth
import com.nourtime.app.remote.parent.ParentDevices
import com.nourtime.app.remote.parent.ParentUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The parent's phone (Phase 2): Google sign-in, the list of children's phones, add a child. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ParentViewModel @Inject constructor(
    private val auth: ParentAuth,
    private val remote: ParentDevices,
) : ViewModel() {

    val user: StateFlow<ParentUser?> = auth.user

    /** Null while loading or signed out. */
    val devices: StateFlow<List<ChildDevice>?> = auth.user
        .flatMapLatest { u -> if (u == null) flowOf(null) else remote.devices(u.uid).catch { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _signInError = MutableStateFlow(false)
    val signInError: StateFlow<Boolean> = _signInError.asStateFlow()

    val testAccountAvailable: Boolean get() = auth.testAccountAvailable

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _signInError.value = runCatching { auth.signInWithGoogle(activityContext) }.isFailure
        }
    }

    fun signInTestAccount() {
        viewModelScope.launch {
            _signInError.value = runCatching { auth.signInTestAccount("parent@example.com", "Test Parent") }.isFailure
        }
    }

    fun signOut() {
        _selected.value = null
        auth.signOut()
    }

    fun open(deviceId: String?) {
        _selected.value = deviceId
    }
}

@Composable
fun ParentRoute(viewModel: ParentViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val current = user
    when {
        current == null -> SignInScreen(viewModel)
        selected != null -> {
            BackHandler { viewModel.open(null) }
            ChildDeviceScreen(deviceId = selected!!, user = current, onBack = { viewModel.open(null) })
        }
        else -> ParentHomeScreen(current, viewModel)
    }
}

@Composable
private fun SignInScreen(viewModel: ParentViewModel) {
    val context = LocalContext.current
    val error by viewModel.signInError.collectAsStateWithLifecycle()
    CenteredScrollColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NourStar(Modifier.size(160.dp))
            Text(stringResource(R.string.parent_signin_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.parent_signin_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            NourPrimaryButton(stringResource(R.string.parent_signin_google), { viewModel.signIn(context) })
            if (viewModel.testAccountAvailable) {
                NourTextButton(stringResource(R.string.parent_signin_test), viewModel::signInTestAccount)
            }
            if (error) {
                Text(stringResource(R.string.parent_signin_error), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ParentHomeScreen(user: ParentUser, viewModel: ParentViewModel) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val now by rememberNow()
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.parent_home_title), style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                user.email ?: user.name.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            NourTextButton(stringResource(R.string.parent_sign_out), viewModel::signOut)
        }
        val list = devices
        if (list != null && list.isEmpty()) {
            Text(stringResource(R.string.parent_no_children), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        list.orEmpty().forEach { device ->
            DeviceCard(device, now) { viewModel.open(device.id) }
        }
        NourPrimaryButton(stringResource(R.string.parent_add_child), { adding = true })
    }
    if (adding) AddChildDialog(user = user, onClose = { adding = false })
}

@Composable
private fun DeviceCard(device: ChildDevice, now: Long, onClick: () -> Unit) {
    val summary = DeviceSummary.of(device.status, now)
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBadge(Icons.Rounded.PhoneAndroid, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(summaryText(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.degraded()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = NourTheme.colors.danger, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.parent_protection_warning), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

internal fun DeviceSummary.degraded(): Boolean = when (this) {
    is DeviceSummary.Available -> degraded
    is DeviceSummary.Locked -> degraded
    else -> false
}

@Composable
internal fun summaryText(summary: DeviceSummary): String {
    val context = LocalContext.current
    return when (summary) {
        DeviceSummary.Unknown -> stringResource(R.string.parent_summary_unknown)
        is DeviceSummary.Available -> stringResource(R.string.parent_summary_available, durationText(minutesRoundedUp(summary.remainingMs)))
        is DeviceSummary.Locked -> stringResource(R.string.parent_summary_locked, durationText(minutesRoundedUp(summary.lockRemainingMs)))
        is DeviceSummary.NotSeen -> stringResource(
            R.string.parent_summary_not_seen,
            DateUtils.formatDateTime(context, summary.sinceMs, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
        )
    }
}

internal fun minutesRoundedUp(ms: Long): Int = ((ms + 59_999) / 60_000).toInt()

/** Wall-clock time that refreshes every 30 s, for "back in …" countdowns. */
@Composable
internal fun rememberNow(): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.longValue = System.currentTimeMillis()
            delay(30_000)
        }
    }
    return now
}
