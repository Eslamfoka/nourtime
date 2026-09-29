package com.nourtime.app.feature.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nourtime.app.core.blocking.LockPeriodState
import com.nourtime.app.core.blocking.ParentPass
import com.nourtime.app.core.security.ParentSession
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.TrustedClock
import com.nourtime.app.data.security.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Parent unlock of Nour Time's own settings: the PIN, plus the security answer while a lock period
 * is running (brief §3), so a child who learned the PIN still can't change the rules.
 */
@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val security: SecurityRepository,
    private val clock: DeviceClock,
    private val session: ParentSession,
    private val pass: ParentPass,
    private val lockPeriod: LockPeriodState,
    private val trustedClock: TrustedClock,
) : ViewModel() {

    private val _askAnswer = MutableStateFlow(false)
    val askAnswer: StateFlow<Boolean> = _askAnswer.asStateFlow()

    val pin = PinCheckController(viewModelScope, security, clock) {
        viewModelScope.launch { if (lockPeriod.isActive()) _askAnswer.value = true else unlock() }
    }

    val answer = AnswerCheckController(viewModelScope, security, clock) { unlock() }

    /** "Forgot PIN?" in progress: a fresh flow each time, so an earlier correct answer doesn't carry over. */
    private val _recovery = MutableStateFlow<PinRecoveryController?>(null)
    val recovery: StateFlow<PinRecoveryController?> = _recovery.asStateFlow()

    init {
        // Tapped on a lock overlay, which opened (or brought back) the app for this.
        viewModelScope.launch {
            ForgotPinRequest.requestedAt.filterNotNull().collect { if (ForgotPinRequest.consume()) startRecovery() }
        }
    }

    fun startRecovery() {
        _askAnswer.value = false
        // The security answer was just checked, so this also covers the lock period's second step.
        _recovery.value = PinRecoveryController(viewModelScope, security, clock) { unlock() }
    }

    fun cancelRecovery() {
        _recovery.value = null
        pin.reset()
    }

    fun cancelAnswer() {
        _askAnswer.value = false
        pin.reset()
    }

    private fun unlock() {
        _askAnswer.value = false
        _recovery.value = null
        // The parent is here: let them use Settings (e.g. to fix a permission) and trust the clock again.
        pass.grantFull()
        viewModelScope.launch { trustedClock.trustSystemClock() }
        session.unlock()
    }
}
