package com.nourtime.app.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattingTest {

    @Test
    fun `formats minutes and seconds, rounding up`() {
        assertEquals("0:30", formatCountdown(30_000, Locale.US))
        assertEquals("0:30", formatCountdown(29_001, Locale.US))
        assertEquals("5:00", formatCountdown(300_000, Locale.US))
        assertEquals("0:00", formatCountdown(-5, Locale.US))
    }

    @Test
    fun `adds hours from one hour up`() {
        assertEquals("1:00:00", formatCountdown(3_600_000, Locale.US))
        assertEquals("6:05:09", formatCountdown((6 * 3600 + 5 * 60 + 9) * 1000L, Locale.US))
    }

    @Test
    fun `countdown parts match what the screen shows`() {
        // 4:59 on the screen is read as 4 minutes 59 seconds, not 4 minutes 58.
        assertEquals(CountdownParts(0, 4, 59), countdownParts(298_001))
        assertEquals(CountdownParts(2, 0, 0), countdownParts(2 * 3_600_000L))
        assertEquals(CountdownParts(6, 5, 9), countdownParts((6 * 3600 + 5 * 60 + 9) * 1000L))
        assertEquals(CountdownParts(0, 0, 0), countdownParts(-5))
    }
}
