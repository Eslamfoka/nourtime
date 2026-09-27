package com.nourtime.app

import com.nourtime.app.data.mode.AppMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {

    @Test
    fun `no mode yet shows the chooser`() {
        assertEquals(AppDestination.MODE_CHOICE, AppDestination.of(mode = null, hasPin = false, complete = false, unlocked = false))
    }

    @Test
    fun `a parent phone goes to the parent screens without a PIN`() {
        assertEquals(AppDestination.PARENT, AppDestination.of(AppMode.PARENT, hasPin = false, complete = false, unlocked = false))
    }

    @Test
    fun `a child phone keeps the Phase 1 flow`() {
        assertEquals(AppDestination.ONBOARDING, AppDestination.of(AppMode.CHILD, hasPin = false, complete = false, unlocked = false))
        assertEquals(AppDestination.UNLOCK, AppDestination.of(AppMode.CHILD, hasPin = true, complete = true, unlocked = false))
        assertEquals(AppDestination.ONBOARDING, AppDestination.of(AppMode.CHILD, hasPin = true, complete = false, unlocked = true))
        assertEquals(AppDestination.HOME, AppDestination.of(AppMode.CHILD, hasPin = true, complete = true, unlocked = true))
    }
}
