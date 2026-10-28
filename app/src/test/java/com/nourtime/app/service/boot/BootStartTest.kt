package com.nourtime.app.service.boot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

// A settings read error inside the boot receiver's coroutine used to crash the process (and with it the
// Accessibility service) and leave protection off until Nour Time was opened.
class BootStartTest {

    private val logged = mutableListOf<Throwable>()
    private val log: (String, Throwable) -> Unit = { _, e -> logged += e }

    @Test
    fun `a set-up phone starts protection`() = runTest {
        assertTrue(BootStart.shouldStart({ true }, log))
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `a phone not set up starts nothing`() = runTest {
        assertFalse(BootStart.shouldStart({ false }, log))
    }

    @Test
    fun `an unreadable setting starts protection and is logged, not thrown`() = runTest {
        val error = IOException("store damaged")
        assertTrue(BootStart.shouldStart({ throw error }, log))
        assertEquals(listOf<Throwable>(error), logged)
    }

    @Test(expected = CancellationException::class)
    fun `a cancellation isn't taken for a read error`() = runTest {
        BootStart.shouldStart({ throw CancellationException("stopped") }, log)
    }
}
