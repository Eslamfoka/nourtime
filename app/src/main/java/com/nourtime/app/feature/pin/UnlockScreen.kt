package com.nourtime.app.feature.pin

import android.text.format.DateUtils
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.component.CenteredScrollColumn
import com.nourtime.app.core.designsystem.component.NourPose
import com.nourtime.app.core.designsystem.component.NourPrimaryButton
import com.nourtime.app.core.designsystem.component.NourStar
import com.nourtime.app.core.designsystem.component.NourTextButton
import com.nourtime.app.core.designsystem.component.nourTextFieldColors
import com.nourtime.app.core.security.PinCreationState
import com.nourtime.app.core.ui.formatCountdown

@Composable
fun UnlockRoute(viewModel: UnlockViewModel = hiltViewModel()) {
    val askAnswer by viewModel.askAnswer.collectAsStateWithLifecycle()
    val pin by viewModel.pin.state.collectAsStateWithLifecycle()
    val answer by viewModel.answer.state.collectAsStateWithLifecycle()
    val recovery by viewModel.recovery.collectAsStateWithLifecycle()
    val pendingResetAt by viewModel.pendingReset.activeAt.collectAsStateWithLifecycle()

    BackHandler(enabled = askAnswer) { viewModel.cancelAnswer() }
    BackHandler(enabled = recovery != null) { viewModel.cancelRecovery() }
    CenteredScrollColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        val flow = recovery
        if (flow != null) {
            PinRecoveryPanel(flow, onCancel = viewModel::cancelRecovery)
        } else if (askAnswer) {
            SecurityAnswerPanel(
                state = answer,
                onAnswerChange = viewModel.answer::onAnswerChange,
                onSubmit = viewModel.answer::submit,
                secondaryText = stringResource(R.string.action_cancel),
                onSecondary = viewModel::cancelAnswer,
            )
        } else {
            pendingResetAt?.let {
                PendingPinResetBanner(
                    it,
                    R.string.pin_reset_pending_unlock_body,
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    announce = true,
                )
            }
            PinPanel(
                state = pin,
                title = stringResource(R.string.pin_unlock_title),
                body = stringResource(R.string.pin_unlock_body),
                onDigit = viewModel.pin::onDigit,
                onDelete = viewModel.pin::onDelete,
                onForgot = viewModel::startRecovery,
            )
        }
    }
}

/** When a new PIN from "Forgot PIN?" starts working, e.g. "Tue 14 Oct, 18:05". */
@Composable
internal fun pinResetTime(atMs: Long): String = DateUtils.formatDateTime(
    LocalContext.current,
    atMs,
    DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL,
)

/**
 * "Forgot PIN?": the security question, then a new PIN twice. The new PIN waits a day (or the parent's
 * phone's approval), so the flow ends on that message instead of opening the settings.
 */
@Composable
private fun PinRecoveryPanel(flow: PinRecoveryController, onCancel: () -> Unit) {
    val stage by flow.stage.collectAsStateWithLifecycle()
    when (stage) {
        PinRecoveryStage.ANSWER -> {
            val answer by flow.answer.state.collectAsStateWithLifecycle()
            SecurityAnswerPanel(
                state = answer,
                onAnswerChange = flow.answer::onAnswerChange,
                onSubmit = flow.answer::submit,
                secondaryText = stringResource(R.string.action_cancel),
                onSecondary = onCancel,
                title = stringResource(R.string.pin_forgot),
                body = stringResource(R.string.pin_recovery_body),
            )
        }
        PinRecoveryStage.DONE -> {
            val activeAt by flow.activeAt.collectAsStateWithLifecycle()
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NourStar(Modifier.size(72.dp), pose = NourPose.CLOCK, animated = false)
                Text(stringResource(R.string.pin_reset_saved_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.pin_reset_saved_body, activeAt?.let { pinResetTime(it) }.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                NourPrimaryButton(stringResource(R.string.action_done), onCancel)
            }
        }
        PinRecoveryStage.NEW_PIN -> {
            val pin by flow.pin.collectAsStateWithLifecycle()
            val confirming = pin.stage == PinCreationState.Stage.CONFIRM
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PinEntryLayout(
                    title = stringResource(if (confirming) R.string.pin_confirm_title else R.string.pin_recovery_new_title),
                    body = stringResource(if (confirming) R.string.pin_confirm_body else R.string.pin_create_body),
                    message = when (pin.error) {
                        PinCreationState.Error.TOO_SIMPLE -> stringResource(R.string.pin_error_too_simple)
                        PinCreationState.Error.MISMATCH -> stringResource(R.string.pin_error_mismatch)
                        null -> null
                    },
                    filled = pin.input.length,
                    shakeKey = pin.rejections,
                    onDigit = flow::onPinDigit,
                    onDelete = flow::onPinDelete,
                    enabled = pin.completedPin == null,
                )
                NourTextButton(stringResource(R.string.action_cancel), onCancel)
            }
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
    onForgot: (() -> Unit)? = null,
) {
    val attemptsLeft = state.attemptsLeft
    val pendingAt = state.notYetActiveAtMs
    val message = when {
        state.lockoutRemainingMs > 0 -> stringResource(R.string.pin_locked_out, formatCountdown(state.lockoutRemainingMs))
        pendingAt != null -> stringResource(R.string.pin_pending_not_yet, pinResetTime(pendingAt))
        attemptsLeft == null -> null
        attemptsLeft > 0 -> stringResource(R.string.pin_wrong_attempts_left, attemptsLeft)
        else -> stringResource(R.string.pin_wrong)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PinEntryLayout(
            title = title,
            body = body,
            message = message,
            filled = state.entered,
            shakeKey = state.rejections,
            onDigit = onDigit,
            onDelete = onDelete,
            enabled = state.lockoutRemainingMs == 0L && !state.checking,
        )
        if (onForgot != null) NourTextButton(stringResource(R.string.pin_forgot), onForgot)
    }
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
    title: String = stringResource(R.string.answer_title),
    body: String = stringResource(R.string.answer_body),
) {
    val locked = state.lockoutRemainingMs > 0
    var showAnswer by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NourStar(Modifier.size(72.dp), pose = NourPose.CLOCK, animated = false)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // The parent typed the question in any language: lay it out by its own text, not the app's.
        Text(
            state.question,
            style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            colors = nourTextFieldColors(),
            value = state.answer,
            onValueChange = onAnswerChange,
            label = { Text(stringResource(R.string.sq_answer_label)) },
            singleLine = true,
            enabled = !locked,
            isError = state.wrong,
            supportingText = when {
                locked -> ({ Text(stringResource(R.string.pin_locked_out, formatCountdown(state.lockoutRemainingMs))) })
                state.wrong || state.attemptsLeft != null -> {
                    val left = state.attemptsLeft?.let { n ->
                        if (n > 0) stringResource(R.string.answer_attempts_left, n) else stringResource(R.string.answer_last_attempt)
                    }
                    val text = listOfNotNull(if (state.wrong) stringResource(R.string.answer_wrong) else null, left).joinToString("\n")
                    ({ Text(text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) })
                }
                else -> null
            },
            visualTransformation = if (showAnswer) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showAnswer = !showAnswer }) {
                    Icon(
                        if (showAnswer) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(if (showAnswer) R.string.sq_hide_answer else R.string.sq_show_answer),
                    )
                }
            },
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
