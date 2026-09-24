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
}
