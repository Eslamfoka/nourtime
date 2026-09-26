package com.nourtime.app.remote.model

import com.nourtime.app.core.timer.TimerCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteCommandTest {

    @Test
    fun `reads a bonus`() {
        assertEquals(TimerCommand.Bonus(30), remoteCommandOf(mapOf("type" to "BONUS", "minutes" to 30L)))
    }

    @Test
    fun `rejects bonuses outside 1 to 240 minutes`() {
        assertNull(remoteCommandOf(mapOf("type" to "BONUS", "minutes" to 0L)))
        assertNull(remoteCommandOf(mapOf("type" to "BONUS", "minutes" to 241L)))
        assertNull(remoteCommandOf(mapOf("type" to "BONUS")))
    }

    @Test
    fun `reads lock now and end lock`() {
        assertEquals(TimerCommand.LockNow, remoteCommandOf(mapOf("type" to "LOCK_NOW")))
        assertEquals(TimerCommand.EndLock, remoteCommandOf(mapOf("type" to "END_LOCK")))
    }

    @Test
    fun `ignores unknown types`() {
        assertNull(remoteCommandOf(mapOf("type" to "FACTORY_RESET")))
        assertNull(remoteCommandOf(emptyMap()))
    }

    @Test
    fun `writes the map the parent sends`() {
        assertEquals(mapOf("type" to "BONUS", "minutes" to 15), commandMap(TimerCommand.Bonus(15)))
        assertEquals(mapOf("type" to "LOCK_NOW"), commandMap(TimerCommand.LockNow))
        assertEquals(mapOf("type" to "END_LOCK"), commandMap(TimerCommand.EndLock))
    }
}
