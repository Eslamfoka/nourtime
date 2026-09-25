package com.nourtime.app.feature.pin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.CenteredScrollColumn
import com.nourtime.app.core.designsystem.component.NourPose
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourStar
import com.nourtime.app.core.designsystem.component.NourTextButton
import com.nourtime.app.core.ui.formatCountdown

@Composable
fun UnlockRoute(viewModel: UnlockViewModel = hiltViewModel()) {
    val askAnswer by viewModel.askAnswer.collectAsStateWithLifecycle()
    val pin by viewModel.pin.state.collectAsStateWithLifecycle()
    val answer by viewModel.answer.state.collectAsStateWithLifecycle()

    BackHandler(enabled = askAnswer) { viewModel.cancelAnswer() }
    CenteredScrollColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        if (askAnswer) {
            SecurityAnswerPanel(
                state = answer,
                onAnswerChange = viewModel.answer::onAnswerChange,
                onSubmit = viewModel.answer::submit,
                secondaryText = stringResource(R.string.action_cancel),
                onSecondary = viewModel::cancelAnswer,
            )
        } else {
            PinPanel(
                state = pin,
                title = stringResource(R.string.pin_unlock_title),
                body = stringResource(R.string.pin_unlock_body),
                onDigit = viewModel.pin::onDigit,
                onDelete = viewModel.pin::onDelete,
            )
        }
    }
}

@Composable
fun PinPanel(
    state: PinUiState,
    title: String,
    body: String,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attemptsLeft = state.attemptsLeft
    val message = when {
        state.lockoutRemainingMs > 0 -> stringResource(R.string.pin_locked_out, formatCountdown(state.lockoutRemainingMs))
        attemptsLeft == null -> null
        attemptsLeft > 0 -> stringResource(R.string.pin_wrong_attempts_left, attemptsLeft)
        else -> stringResource(R.string.pin_wrong)
    }
    PinEntryLayout(
        title = title,
        body = body,
        message = message,
        filled = state.entered,
        shakeKey = state.rejections,
        onDigit = onDigit,
        onDelete = onDelete,
        enabled = state.lockoutRemainingMs == 0L && !state.checking,
        modifier = modifier,
    )
}

/** Asks the parent's own security question (brief §3 layer 2). */
@Composable
fun SecurityAnswerPanel(
    state: AnswerUiState,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val locked = state.lockoutRemainingMs > 0
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NourStar(Modifier.size(72.dp), pose = NourPose.CLOCK, animated = false)
        Text(stringResource(R.string.answer_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.answer_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(state.question, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = state.answer,
            onValueChange = onAnswerChange,
            label = { Text(stringResource(R.string.sq_answer_label)) },
            singleLine = true,
            enabled = !locked,
            isError = state.wrong,
            supportingText = when {
                locked -> ({ Text(stringResource(R.string.pin_locked_out, formatCountdown(state.lockoutRemainingMs))) })
                state.wrong -> ({ Text(stringResource(R.string.answer_wrong)) })
                else -> null
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        NourPrimaryButton(
            stringResource(R.string.answer_submit),
            onSubmit,
            enabled = !locked && !state.checking && state.answer.isNotBlank(),
        )
        if (secondaryText != null && onSecondary != null) NourTextButton(secondaryText, onSecondary)
    }
}
