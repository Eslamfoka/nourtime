package com.nourtime.app.feature.pin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.ui.formatCountdown

@Composable
fun UnlockRoute(viewModel: UnlockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val attemptsLeft = state.attemptsLeft
    val message = when {
        state.lockoutRemainingMs > 0 -> stringResource(R.string.pin_locked_out, formatCountdown(state.lockoutRemainingMs))
        attemptsLeft == null -> null
        attemptsLeft > 0 -> stringResource(R.string.pin_wrong_attempts_left, attemptsLeft)
        else -> stringResource(R.string.pin_wrong)
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        PinEntryLayout(
            title = stringResource(R.string.pin_unlock_title),
            body = stringResource(R.string.pin_unlock_body),
            message = message,
            filled = state.entered,
            shakeKey = state.rejections,
            onDigit = viewModel::onDigit,
            onDelete = viewModel::onDelete,
            enabled = state.lockoutRemainingMs == 0L && !state.checking,
        )
    }
}
