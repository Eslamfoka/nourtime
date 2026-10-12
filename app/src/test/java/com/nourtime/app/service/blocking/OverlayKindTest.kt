package com.nourtime.app.service.blocking

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayKindTest {

    @Test
    fun `with Accessibility the regular overlay is the fallback`() {
        // Seen on the emulator: after Accessibility was switched off and on, Android refused the
        // accessibility overlay ("token null is not valid") for about a minute and nothing was covered.
        assertEquals(listOf(OverlayKind.ACCESSIBILITY, OverlayKind.APP), OverlayKind.attempts(accessibilityAvailable = true))
    }

    @Test
    fun `without Accessibility only the regular overlay is tried`() {
        assertEquals(listOf(OverlayKind.APP), OverlayKind.attempts(accessibilityAvailable = false))
    }
}
