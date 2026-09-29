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
 * "Forgot PIN?": the parent answers their security question (same escalating lockout as always), then
 * chooses a new PIN twice. The PIN can only change after a correct answer.
 */
class PinRecoveryController(
    private val scope: CoroutineScope,
    private val security: SecurityRepository,
    clock: DeviceClock,
    private val onDone: () -> Unit,
) {
    private val _stage = MutableStateFlow(PinRecoveryStage.ANSWER)
    val stage: StateFlow<PinRecoveryStage> = _stage.asStateFlow()

    private val _pin = MutableStateFlow(PinCreationState())
    val pin: StateFlow<PinCreationState> = _pin.asStateFlow()

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
            security.setPin(newPin)
            _stage.value = PinRecoveryStage.DONE
            onDone()
        }
    }

    fun onPinDelete() {
        if (_stage.value == PinRecoveryStage.NEW_PIN) _pin.value = PinCreation.onDelete(_pin.value)
    }
}
