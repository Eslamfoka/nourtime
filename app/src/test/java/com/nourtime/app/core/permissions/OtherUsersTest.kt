package com.nourtime.app.core.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherUsersTest {

    @Test
    fun `a phone without multiple users has nothing to recommend`() {
        assertEquals(OtherUsersState.UNSUPPORTED, OtherUsers.state(supportsMultipleUsers = false, switcherSetting = 1))
        assertFalse(OtherUsersState.UNSUPPORTED.recommendTurningOff)
    }

    @Test
    fun `the Multiple users switch decides`() {
        assertEquals(OtherUsersState.ON, OtherUsers.state(supportsMultipleUsers = true, switcherSetting = 1))
        assertEquals(OtherUsersState.OFF, OtherUsers.state(supportsMultipleUsers = true, switcherSetting = 0))
        assertTrue(OtherUsersState.ON.recommendTurningOff)
        assertFalse(OtherUsersState.OFF.recommendTurningOff)
    }

    @Test
    fun `an absent or unreadable switch is unknown and still recommends`() {
        // Absent until the parent first touches it: the phone maker's default, which an app can't see.
        assertEquals(OtherUsersState.UNKNOWN, OtherUsers.state(supportsMultipleUsers = true, switcherSetting = null))
        assertTrue(OtherUsersState.UNKNOWN.recommendTurningOff)
    }
}
