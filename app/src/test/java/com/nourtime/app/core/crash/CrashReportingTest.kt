package com.nourtime.app.core.crash

import com.nourtime.app.data.mode.AppMode
import org.junit.Assert.assertEquals
import org.junit.Test

// Owner's decision (2026-10-12): crash reports only from phones already connected with the parent's
// consent, so "without a connected parent's phone, nothing leaves your child's phone" stays true.
class CrashReportingTest {

    private fun allowed(debug: Boolean = false, mode: AppMode? = AppMode.CHILD, paired: Boolean = false, signedIn: Boolean = false) =
        CrashReporting.allowed(debug, mode, childPaired = paired, parentSignedIn = signedIn)

    @Test
    fun `a child's phone that isn't connected sends nothing`() {
        assertEquals(false, allowed(paired = false))
    }

    @Test
    fun `a connected child's phone reports crashes`() {
        assertEquals(true, allowed(paired = true))
    }

    @Test
    fun `a parent's phone reports crashes once signed in`() {
        assertEquals(false, allowed(mode = AppMode.PARENT, signedIn = false))
        assertEquals(true, allowed(mode = AppMode.PARENT, signedIn = true))
    }

    @Test
    fun `a phone that hasn't chosen its side sends nothing`() {
        assertEquals(false, allowed(mode = null, paired = true, signedIn = true))
    }

    @Test
    fun `debug builds never report`() {
        assertEquals(false, allowed(debug = true, paired = true))
    }
}
