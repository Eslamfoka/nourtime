package com.nourtime.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRulesTest {

    @Test
    fun `accepts exactly four digits`() {
        assertTrue(PinRules.isValidFormat("4827"))
        assertFalse(PinRules.isValidFormat("482"))
        assertFalse(PinRules.isValidFormat("48271"))
        assertFalse(PinRules.isValidFormat("48a7"))
    }

    @Test
    fun `flags repeated digits and straight runs`() {
        listOf("0000", "7777", "1234", "6789", "4321", "9876", "0123").forEach {
            assertTrue(it, PinRules.isTooSimple(it))
        }
    }

    @Test
    fun `allows ordinary pins`() {
        listOf("4827", "1235", "2580", "1122", "9012").forEach {
            assertFalse(it, PinRules.isTooSimple(it))
        }
    }
}
