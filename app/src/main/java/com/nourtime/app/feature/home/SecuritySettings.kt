package com.nourtime.app.feature.home

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nourtime.app.R
import com.nourtime.app.core.security.PinCreation
import com.nourtime.app.core.security.PinCreationState
import com.nourtime.app.core.security.SecurityQuestionValidator
import com.nourtime.app.data.security.SecurityRepository
import com.nourtime.app.feature.onboarding.CreatePinStep
import com.nourtime.app.feature.onboarding.SecurityQuestionForm
import com.nourtime.app.feature.onboarding.SecurityQuestionStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Changing the parent PIN and the security question from Settings (brief §6). */
@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val security: SecurityRepository,
) : ViewModel() {

    private val _pin = MutableStateFlow(PinCreationState())
    val pin: StateFlow<PinCreationState> = _pin.asStateFlow()

    private val _question = MutableStateFlow(SecurityQuestionForm())
    val question: StateFlow<SecurityQuestionForm> = _question.asStateFlow()

    /** Set when a change was saved; the dialog closes and confirms. */
    private val _saved = MutableStateFlow<Int?>(null)
    val saved: StateFlow<Int?> = _saved.asStateFlow()

    fun startPinChange() {
        _pin.value = PinCreationState()
    }

    fun onPinDigit(digit: Char) {
        val updated = PinCreation.onDigit(_pin.value, digit)
        _pin.value = updated
        val pin = updated.completedPin ?: return
        viewModelScope.launch {
            security.setPin(pin)
            _saved.value = R.string.pin_changed
        }
    }

    fun onPinDelete() {
        _pin.value = PinCreation.onDelete(_pin.value)
    }

    fun startQuestionChange() {
        viewModelScope.launch {
            _question.value = SecurityQuestionForm(question = security.securityQuestion.first().orEmpty())
        }
    }

    fun onQuestionChange(value: String) = _question.update { it.copy(question = value, errors = emptySet()) }

    fun onAnswerChange(value: String) = _question.update { it.copy(answer = value, errors = emptySet()) }

    fun onConfirmationChange(value: String) = _question.update { it.copy(confirmation = value, errors = emptySet()) }

    fun saveQuestion() {
        val form = _question.value
        if (form.saving) return
        val errors = SecurityQuestionValidator.validate(form.question, form.answer, form.confirmation)
        if (errors.isNotEmpty()) {
            _question.update { it.copy(errors = errors) }
            return
        }
        _question.update { it.copy(saving = true) }
        viewModelScope.launch {
            security.setSecurityQuestion(form.question, form.answer)
            _question.value = SecurityQuestionForm()
            _saved.value = R.string.question_changed
        }
    }

    fun consumeSaved() {
        _saved.value = null
    }
}

enum class SecurityEdit { PIN, QUESTION }

@Composable
internal fun SecurityEditDialog(edit: SecurityEdit, onClose: () -> Unit, viewModel: SecuritySettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    LaunchedEffect(edit) {
        if (edit == SecurityEdit.PIN) viewModel.startPinChange() else viewModel.startQuestionChange()
    }
    LaunchedEffect(saved) {
        val message = saved ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeSaved()
        onClose()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (edit) {
                SecurityEdit.PIN -> {
                    val pin by viewModel.pin.collectAsStateWithLifecycle()
                    CreatePinStep(
                        progress = null,
                        onBack = onClose,
                        state = pin,
                        onDigit = viewModel::onPinDigit,
                        onDelete = viewModel::onPinDelete,
                        title = R.string.change_pin,
                    )
                }
                SecurityEdit.QUESTION -> {
                    val form by viewModel.question.collectAsStateWithLifecycle()
                    SecurityQuestionStep(
                        progress = null,
                        onBack = onClose,
                        form = form,
                        onQuestionChange = viewModel::onQuestionChange,
                        onAnswerChange = viewModel::onAnswerChange,
                        onConfirmationChange = viewModel::onConfirmationChange,
                        onSave = viewModel::saveQuestion,
                    )
                }
            }
        }
    }
}
