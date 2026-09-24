package com.nourtime.app.feature.pin

import com.nourtime.app.core.security.PinRules
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.data.security.AnswerCheckResult
import com.nourtime.app.data.security.PinCheckResult
import com.nourtime.app.data.security.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PinUiState(
    val entered: Int = 0,
    val checking: Boolean = false,
    val attemptsLeft: Int? = null,
    val lockoutRemainingMs: Long = 0,
    val rejections: Int = 0,
)

/** PIN entry with lockout countdown. Shared by the app's unlock screen and the lock overlay. */
class PinCheckController(
    private val scope: CoroutineScope,
    private val security: SecurityRepository,
    private val clock: DeviceClock,
    private val onSuccess: () -> Unit,
) {
    private val _state = MutableStateFlow(PinUiState())
    val state: StateFlow<PinUiState> = _state.asStateFlow()

    private var input = ""
    private var countdown: Job? = null

    init {
        scope.launch { startCountdown(security.lockoutRemaining()) }
    }

    fun reset() {
        input = ""
        _state.update { it.copy(entered = 0, attemptsLeft = null, checking = false) }
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
        scope.launch {
            val result = security.verifyPin(pin)
            input = ""
            when (result) {
                PinCheckResult.Success, PinCheckResult.NoPin -> {
                    _state.value = PinUiState()
                    onSuccess()
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
        countdown = scope.launch {
            runCountdown(clock, remainingMs) { left -> _state.update { it.copy(lockoutRemainingMs = left) } }
        }
    }
}

data class AnswerUiState(
    val question: String = "",
    val answer: String = "",
    val checking: Boolean = false,
    val wrong: Boolean = false,
    val lockoutRemainingMs: Long = 0,
)

/** Security-question check (brief §3 layer 2), with the same escalating lockout as the PIN. */
class AnswerCheckController(
    private val scope: CoroutineScope,
    private val security: SecurityRepository,
    private val clock: DeviceClock,
    private val onSuccess: () -> Unit,
) {
    private val _state = MutableStateFlow(AnswerUiState())
    val state: StateFlow<AnswerUiState> = _state.asStateFlow()
    private var countdown: Job? = null

    init {
        scope.launch {
            _state.update { it.copy(question = security.securityQuestion.first().orEmpty()) }
            startCountdown(security.answerLockoutRemaining())
        }
    }

    fun onAnswerChange(value: String) = _state.update { it.copy(answer = value, wrong = false) }

    fun submit() {
        val s = _state.value
        if (s.checking || s.lockoutRemainingMs > 0 || s.answer.isBlank()) return
        _state.update { it.copy(checking = true) }
        scope.launch {
            when (val result = security.checkAnswer(s.answer)) {
                AnswerCheckResult.Correct -> {
                    _state.update { it.copy(answer = "", checking = false) }
                    onSuccess()
                }
                AnswerCheckResult.Wrong -> _state.update { it.copy(checking = false, wrong = true) }
                is AnswerCheckResult.LockedOut -> {
                    _state.update { it.copy(checking = false, wrong = true) }
                    startCountdown(result.remainingMs)
                }
            }
        }
    }

    private fun startCountdown(remainingMs: Long) {
        countdown?.cancel()
        countdown = scope.launch {
            runCountdown(clock, remainingMs) { left -> _state.update { it.copy(lockoutRemainingMs = left) } }
        }
    }
}

private suspend fun runCountdown(clock: DeviceClock, remainingMs: Long, onTick: (Long) -> Unit) {
    if (remainingMs <= 0) {
        onTick(0)
        return
    }
    val end = clock.elapsedRealtime() + remainingMs
    while (true) {
        val left = (end - clock.elapsedRealtime()).coerceAtLeast(0)
        onTick(left)
        if (left == 0L) break
        delay(minOf(left, 250))
    }
}
