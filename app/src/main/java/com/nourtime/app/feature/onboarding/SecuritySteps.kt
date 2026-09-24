package com.nourtime.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.security.PinCreationState
import com.nourtime.app.core.security.SecurityQuestionValidator
import com.nourtime.app.feature.pin.PinEntryLayout

@Composable
fun CreatePinStep(
    progress: Pair<Int, Int>?,
    onBack: (() -> Unit)?,
    state: PinCreationState,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
) {
    val confirming = state.stage == PinCreationState.Stage.CONFIRM
    StepLayout(progress = progress, onBack = onBack) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PinEntryLayout(
                title = stringResource(if (confirming) R.string.pin_confirm_title else R.string.pin_create_title),
                body = stringResource(if (confirming) R.string.pin_confirm_body else R.string.pin_create_body),
                message = when (state.error) {
                    PinCreationState.Error.TOO_SIMPLE -> stringResource(R.string.pin_error_too_simple)
                    PinCreationState.Error.MISMATCH -> stringResource(R.string.pin_error_mismatch)
                    null -> null
                },
                filled = state.input.length,
                shakeKey = state.rejections,
                onDigit = onDigit,
                onDelete = onDelete,
                enabled = state.completedPin == null,
            )
        }
    }
}

@Composable
fun SecurityQuestionStep(
    progress: Pair<Int, Int>?,
    onBack: (() -> Unit)?,
    form: SecurityQuestionForm,
    onQuestionChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showAnswer by rememberSaveable { mutableStateOf(false) }
    val errors = form.errors
    val transformation = if (showAnswer) VisualTransformation.None else PasswordVisualTransformation()
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = { showAnswer = !showAnswer }) {
            Icon(
                if (showAnswer) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = stringResource(if (showAnswer) R.string.sq_hide_answer else R.string.sq_show_answer),
            )
        }
    }

    StepLayout(
        progress = progress,
        onBack = onBack,
        actions = { NourPrimaryButton(stringResource(R.string.sq_save), onSave, enabled = !form.saving) },
    ) {
        Text(stringResource(R.string.sq_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.sq_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = form.question,
                onValueChange = onQuestionChange,
                label = { Text(stringResource(R.string.sq_question_label)) },
                placeholder = { Text(stringResource(R.string.sq_question_placeholder)) },
                isError = SecurityQuestionValidator.Error.QUESTION_TOO_SHORT in errors,
                supportingText = errorText(SecurityQuestionValidator.Error.QUESTION_TOO_SHORT in errors, R.string.sq_error_question_short),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.answer,
                onValueChange = onAnswerChange,
                label = { Text(stringResource(R.string.sq_answer_label)) },
                singleLine = true,
                visualTransformation = transformation,
                trailingIcon = toggle,
                isError = SecurityQuestionValidator.Error.ANSWER_TOO_SHORT in errors,
                supportingText = errorText(SecurityQuestionValidator.Error.ANSWER_TOO_SHORT in errors, R.string.sq_error_answer_short),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.confirmation,
                onValueChange = onConfirmationChange,
                label = { Text(stringResource(R.string.sq_answer_confirm_label)) },
                singleLine = true,
                visualTransformation = transformation,
                isError = SecurityQuestionValidator.Error.ANSWER_MISMATCH in errors,
                supportingText = errorText(SecurityQuestionValidator.Error.ANSWER_MISMATCH in errors, R.string.sq_error_answer_mismatch),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResource(R.string.sq_answer_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun errorText(show: Boolean, message: Int): (@Composable () -> Unit)? =
    if (show) ({ Text(stringResource(message)) }) else null
