package com.nourtime.app.core.security

import com.nourtime.app.core.security.PinCreationState.Error
import com.nourtime.app.core.security.PinCreationState.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinCreationTest {

    private fun PinCreationState.type(digits: String) = digits.fold(this) { s, d -> PinCreation.onDigit(s, d) }

    @Test
    fun `enter then confirm completes`() {
        val entered = PinCreationState().type("4827")
        assertEquals(Stage.CONFIRM, entered.stage)
        assertEquals("", entered.input)

        val done = entered.type("4827")
        assertEquals("4827", done.completedPin)
    }

    @Test
    fun `mismatch starts over with an error`() {
        val state = PinCreationState().type("4827").type("4828")
        assertEquals(Stage.ENTER, state.stage)
        assertEquals(Error.MISMATCH, state.error)
        assertNull(state.firstPin)
        assertEquals(1, state.rejections)
    }

    @Test
    fun `simple pin is rejected at the first stage`() {
        val state = PinCreationState().type("1234")
        assertEquals(Stage.ENTER, state.stage)
        assertEquals(Error.TOO_SIMPLE, state.error)
        assertEquals("", state.input)
    }

    @Test
    fun `typing clears the previous error`() {
        val state = PinCreationState().type("1111").type("4")
        assertNull(state.error)
        assertEquals("4", state.input)
    }

    @Test
    fun `delete removes the last digit`() {
        val state = PinCreation.onDelete(PinCreationState().type("48"))
        assertEquals("4", state.input)
        assertEquals("", PinCreation.onDelete(PinCreationState()).input)
    }

    @Test
    fun `input is ignored once completed`() {
        val done = PinCreationState().type("4827").type("4827")
        assertEquals(done, PinCreation.onDigit(done, '1'))
        assertEquals(done, PinCreation.onDelete(done))
    }
}
