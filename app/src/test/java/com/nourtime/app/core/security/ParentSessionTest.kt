package com.nourtime.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentSessionTest {

    private val session = ParentSession(graceMs = 60_000)

    @Test
    fun `starts locked`() {
        assertFalse(session.isUnlocked.value)
    }

    @Test
    fun `short trip to settings keeps the session`() {
        session.unlock()
        session.onBackground(1_000)
        session.onForeground(50_000)
        assertTrue(session.isUnlocked.value)
    }

    @Test
    fun `turning the screen off locks the session even within the grace time`() {
        // Seen on the emulator: a minute after the parent locked the screen, Nour Time opened without the PIN.
        session.unlock()
        session.onBackground(1_000)
        session.onScreenOff()
        session.onForeground(5_000)
        assertFalse(session.isUnlocked.value)
    }

    @Test
    fun `long absence locks again`() {
        session.unlock()
        session.onBackground(1_000)
        session.onForeground(70_000)
        assertFalse(session.isUnlocked.value)
    }

    @Test
    fun `foreground without background is ignored`() {
        session.unlock()
        session.onForeground(1_000_000)
        assertTrue(session.isUnlocked.value)
    }

    @Test
    fun `background time from a previous session doesn't count after unlocking again`() {
        session.unlock()
        session.onBackground(1_000)
        session.lock()
        session.unlock()
        session.onForeground(1_000_000)
        assertTrue(session.isUnlocked.value)
    }

    @Test
    fun `during setup a long trip to settings keeps the session`() {
        // Allowing Accessibility's "restricted settings" can take the parent several minutes.
        session.setInSetup(true)
        session.unlock()
        session.onBackground(1_000)
        session.onForeground(20 * 60_000)
        assertTrue(session.isUnlocked.value)
    }

    @Test
    fun `during setup the screen turning off doesn't lock`() {
        // The screen times out while the parent reads the steps in Settings.
        session.setInSetup(true)
        session.unlock()
        session.onBackground(1_000)
        session.onScreenOff()
        session.onForeground(5_000)
        assertTrue(session.isUnlocked.value)
    }

    @Test
    fun `a setup left alone for long locks again`() {
        session.setInSetup(true)
        session.unlock()
        session.onBackground(1_000)
        session.onForeground(1_000 + ParentSession.SETUP_GRACE_MS + 1)
        assertFalse(session.isUnlocked.value)
    }

    @Test
    fun `after setup the normal rules are back`() {
        session.setInSetup(true)
        session.setInSetup(false)
        session.unlock()
        session.onBackground(1_000)
        session.onScreenOff()
        session.onForeground(5_000)
        assertFalse(session.isUnlocked.value)
    }
}
