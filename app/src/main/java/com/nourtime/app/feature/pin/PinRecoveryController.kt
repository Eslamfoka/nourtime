package com.nourtime.app.feature.pin

import com.nourtime.app.core.security.PinCreation
import com.nourtime.app.core.security.PinCreationState
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.data.security.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PinRecoveryStage { ANSWER, NEW_PIN, DONE }

/**
 * "Forgot PIN?": the parent answers their security question (three tries, then one a day), then
 * chooses a new PIN twice. The new PIN starts working a day later, or once the parent's phone approves
 * it; nothing opens now, so a child who guessed the answer gains nothing (2026-10-13 audit, H1).
 */
class PinRecoveryController(
    private val scope: CoroutineScope,
    private val security: SecurityRepository,
    clock: DeviceClock,
    /** Tells the parent: a child who knows the answer could otherwise take over unnoticed. */
    private val onPinReset: suspend () -> Unit = {},
) {
    private val _stage = MutableStateFlow(PinRecoveryStage.ANSWER)
    val stage: StateFlow<PinRecoveryStage> = _stage.asStateFlow()

    private val _pin = MutableStateFlow(PinCreationState())
    val pin: StateFlow<PinCreationState> = _pin.asStateFlow()

    /** When the new PIN starts working (wall-clock ms), once it is saved. */
    private val _activeAt = MutableStateFlow<Long?>(null)
    val activeAt: StateFlow<Long?> = _activeAt.asStateFlow()

    val answer = AnswerCheckController(scope, security, clock) {
        _pin.value = PinCreationState()
        _stage.value = PinRecoveryStage.NEW_PIN
    }

    fun onPinDigit(digit: Char) {
        if (_stage.value != PinRecoveryStage.NEW_PIN) return
        val updated = PinCreation.onDigit(_pin.value, digit)
        _pin.value = updated
        val newPin = updated.completedPin ?: return
        scope.launch {
            _activeAt.value = security.setPinAfterRecovery(newPin)
            runCatching { onPinReset() }
            _stage.value = PinRecoveryStage.DONE
        }
    }

    fun onPinDelete() {
        if (_stage.value == PinRecoveryStage.NEW_PIN) _pin.value = PinCreation.onDelete(_pin.value)
    }
}
