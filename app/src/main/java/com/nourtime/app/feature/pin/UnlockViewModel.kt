package com.nourtime.app.feature.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nourtime.app.core.security.ParentSession
import com.nourtime.app.core.security.PinRules
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.data.security.PinCheckResult
import com.nourtime.app.data.security.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnlockUiState(
    val entered: Int = 0,
    val checking: Boolean = false,
    val attemptsLeft: Int? = null,
    val lockoutRemainingMs: Long = 0,
    val rejections: Int = 0,
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val security: SecurityRepository,
    private val session: ParentSession,
    private val clock: DeviceClock,
) : ViewModel() {

    private val _state = MutableStateFlow(UnlockUiState())
    val state: StateFlow<UnlockUiState> = _state.asStateFlow()

    private var input = ""
    private var countdown: Job? = null

    init {
        viewModelScope.launch { startCountdown(security.lockoutRemaining()) }
    }

    fun onDigit(digit: Char) {
        val s = _state.value
        if (s.checking || s.lockoutRemainingMs > 0 || input.length >= PinRules.LENGTH) return
        input += digit
        _state.update { it.copy(entered = input.length) }
        if (input.length == PinRules.LENGTH) verify(input)
    }

    fun onDelete() {
        if (_state.value.checking || input.isEmpty()) return
        input = input.dropLast(1)
        _state.update { it.copy(entered = input.length) }
    }

    private fun verify(pin: String) {
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val result = security.verifyPin(pin)
            input = ""
            when (result) {
                PinCheckResult.Success, PinCheckResult.NoPin -> {
                    _state.value = UnlockUiState()
                    session.unlock()
                }
                is PinCheckResult.Wrong -> _state.update {
                    it.copy(entered = 0, checking = false, attemptsLeft = result.attemptsBeforeLockout, rejections = it.rejections + 1)
                }
                is PinCheckResult.LockedOut -> {
                    _state.update { it.copy(entered = 0, checking = false, attemptsLeft = null, rejections = it.rejections + 1) }
                    startCountdown(result.remainingMs)
                }
            }
        }
    }

    private fun startCountdown(remainingMs: Long) {
        countdown?.cancel()
        if (remainingMs <= 0) {
            _state.update { it.copy(lockoutRemainingMs = 0) }
            return
        }
        val end = clock.elapsedRealtime() + remainingMs
        countdown = viewModelScope.launch {
            while (true) {
                val left = end - clock.elapsedRealtime()
                _state.update { it.copy(lockoutRemainingMs = left.coerceAtLeast(0)) }
                if (left <= 0) break
                delay(minOf(left, 250))
            }
        }
    }
}
