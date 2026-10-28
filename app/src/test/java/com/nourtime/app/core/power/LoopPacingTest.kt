package com.nourtime.app.core.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The loops slow down only while the screen is off; with it on, blocking and counting stay at a second.
class LoopPacingTest {

    @Test
    fun `screen on keeps every check at a second`() {
        assertEquals(1_000L, LoopPacing.blockTickMs(screenInteractive = true))
        assertEquals(1_000L, LoopPacing.screenSyncMs(screenInteractive = true))
        assertEquals(1_000L, LoopPacing.timerTickMs(screenUsable = true))
    }

    @Test
    fun `screen off slows the block check and the timer to the idle tick`() {
        assertEquals(30_000L, LoopPacing.blockTickMs(screenInteractive = false))
        assertEquals(30_000L, LoopPacing.timerTickMs(screenUsable = false))
    }

    @Test
    fun `screen off still re-reads the screen every few seconds`() {
        assertEquals(5_000L, LoopPacing.screenSyncMs(screenInteractive = false))
    }

    @Test
    fun `usage stats are polled only without Accessibility and on a usable screen`() {
        assertTrue(LoopPacing.usagePollNeeded(accessibilityConnected = false, screenUsable = true))
        assertFalse(LoopPacing.usagePollNeeded(accessibilityConnected = true, screenUsable = true))
        assertFalse(LoopPacing.usagePollNeeded(accessibilityConnected = false, screenUsable = false))
        assertFalse(LoopPacing.usagePollNeeded(accessibilityConnected = true, screenUsable = false))
    }
}
