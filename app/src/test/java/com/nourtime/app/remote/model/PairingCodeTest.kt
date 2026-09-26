package com.nourtime.app.remote.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PairingCodeTest {

    @Test
    fun `generated codes are always six digits`() {
        repeat(1000) { seed ->
            val code = PairingCode.generate(Random(seed))
            assertTrue(code, code.length == 6 && code.all { it in '0'..'9' })
        }
    }

    @Test
    fun `the QR holds a nourtime link`() {
        assertEquals("nourtime://pair?c=012345", PairingCode.uri("012345"))
    }

    @Test
    fun `parses the QR link`() {
        assertEquals("012345", PairingCode.parse("nourtime://pair?c=012345"))
    }

    @Test
    fun `parses typed codes with spaces or dashes`() {
        assertEquals("123456", PairingCode.parse(" 123 456 "))
        assertEquals("123456", PairingCode.parse("123-456"))
    }

    @Test
    fun `parses Arabic-Indic digits`() {
        assertEquals("123456", PairingCode.parse("١٢٣٤٥٦"))
    }

    @Test
    fun `rejects anything else`() {
        assertNull(PairingCode.parse("12345"))
        assertNull(PairingCode.parse("1234567"))
        assertNull(PairingCode.parse("abcdef"))
        assertNull(PairingCode.parse("https://example.com/pair?c=123456"))
        assertNull(PairingCode.parse(""))
    }
}
