package com.nourtime.app.core.security

/** State for "enter a new PIN, then confirm it". */
data class PinCreationState(
    val stage: Stage = Stage.ENTER,
    val input: String = "",
    val firstPin: String? = null,
    val error: Error? = null,
    val completedPin: String? = null,
    /** Incremented on every rejection so the UI can shake and vibrate. */
    val rejections: Int = 0,
) {
    enum class Stage { ENTER, CONFIRM }
    enum class Error { TOO_SIMPLE, MISMATCH }
}

object PinCreation {

    fun onDigit(state: PinCreationState, digit: Char): PinCreationState {
        if (state.completedPin != null || digit !in '0'..'9' || state.input.length >= PinRules.LENGTH) return state
        val input = state.input + digit
        if (input.length < PinRules.LENGTH) return state.copy(input = input, error = null)

        return when (state.stage) {
            PinCreationState.Stage.ENTER ->
                if (PinRules.isTooSimple(input)) {
                    state.copy(input = "", error = PinCreationState.Error.TOO_SIMPLE, rejections = state.rejections + 1)
                } else {
                    state.copy(stage = PinCreationState.Stage.CONFIRM, input = "", firstPin = input, error = null)
                }

            PinCreationState.Stage.CONFIRM ->
                if (input == state.firstPin) {
                    state.copy(input = input, error = null, completedPin = input)
                } else {
                    PinCreationState(error = PinCreationState.Error.MISMATCH, rejections = state.rejections + 1)
                }
        }
    }

    fun onDelete(state: PinCreationState): PinCreationState =
        if (state.completedPin != null || state.input.isEmpty()) state else state.copy(input = state.input.dropLast(1))
}
