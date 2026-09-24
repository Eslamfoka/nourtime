package com.nourtime.app.feature.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nourtime.app.R
import com.nourtime.app.core.blocking.BlockDecision
import com.nourtime.app.core.blocking.ParentPass
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.data.security.SecurityRepository
import com.nourtime.app.feature.pin.AnswerCheckController
import com.nourtime.app.feature.pin.PinCheckController
import com.nourtime.app.feature.pin.PinPanel
import com.nourtime.app.feature.pin.SecurityAnswerPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ParentStage { CHILD, PIN, ANSWER }

/**
 * The parent's way through a lock screen (brief §3):
 * - PIN. During a lock period the PIN alone can't open limited apps: the security answer follows.
 * - Whole-device lock: after the PIN the parent may unlock "the phone only" without the answer;
 *   limited apps then stay closed.
 * Success grants a short [ParentPass]; the coordinator then hides the overlay.
 */
class OverlayParentFlow(
    scope: CoroutineScope,
    security: SecurityRepository,
    clock: DeviceClock,
    private val pass: ParentPass,
) {
    var decision: () -> BlockDecision? = { null }

    private val _stage = MutableStateFlow(ParentStage.CHILD)
    val stage: StateFlow<ParentStage> = _stage.asStateFlow()

    val pin = PinCheckController(scope, security, clock) { onPinCorrect() }
    val answer = AnswerCheckController(scope, security, clock) {
        _stage.value = ParentStage.CHILD
        pass.grantFull()
    }

    fun openParent() {
        pin.reset()
        _stage.value = ParentStage.PIN
    }

    fun back() {
        _stage.value = ParentStage.CHILD
    }

    fun phoneOnly() {
        _stage.value = ParentStage.CHILD
        pass.grantDevice()
    }

    private fun onPinCorrect() {
        val d = decision()
        if (d != null && (d.needsSecurityAnswer || d.wholeDevice)) {
            _stage.value = ParentStage.ANSWER
        } else {
            _stage.value = ParentStage.CHILD
            pass.grantFull()
        }
    }
}

@Composable
fun LockOverlayContent(
    state: LockScreenState,
    stage: ParentStage,
    parentFlow: OverlayParentFlow,
    onChildOk: () -> Unit,
) {
    when (stage) {
        ParentStage.CHILD -> TimeUpScreen(
            state = state,
            onOk = if (state.decision.wholeDevice) null else onChildOk,
            onParents = parentFlow::openParent,
        )
        ParentStage.PIN -> ParentPanelFrame(onBack = parentFlow::back) {
            val pin by parentFlow.pin.state.collectAsStateWithLifecycle()
            PinPanel(
                state = pin,
                title = stringResource(R.string.pin_unlock_title),
                body = stringResource(R.string.lock_parent_pin_body),
                onDigit = parentFlow.pin::onDigit,
                onDelete = parentFlow.pin::onDelete,
            )
        }
        ParentStage.ANSWER -> ParentPanelFrame(onBack = parentFlow::back) {
            val answer by parentFlow.answer.state.collectAsStateWithLifecycle()
            SecurityAnswerPanel(
                state = answer,
                onAnswerChange = parentFlow.answer::onAnswerChange,
                onSubmit = parentFlow.answer::submit,
                secondaryText = if (state.decision.wholeDevice) stringResource(R.string.lock_phone_only) else null,
                onSecondary = if (state.decision.wholeDevice) parentFlow::phoneOnly else null,
            )
        }
    }
}
