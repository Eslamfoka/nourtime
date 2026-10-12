package com.nourtime.app.service.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceStartTest {

    @Test
    fun `a plain start is used when Android allows it`() {
        // A plain start has no deadline; startForegroundService() crashed a slow phone that didn't reach
        // startForeground() within Android's limit.
        val calls = mutableListOf<String>()
        ServiceStart.start(plain = { calls += "plain" }, foreground = { calls += "foreground" })
        assertEquals(listOf("plain"), calls)
    }

    @Test
    fun `a foreground start is used when the app is in the background`() {
        // Android 8+ refuses plain starts from the background (e.g. at boot) with IllegalStateException.
        val calls = mutableListOf<String>()
        ServiceStart.start(
            plain = { calls += "plain"; throw IllegalStateException("Not allowed to start service: app is in background") },
            foreground = { calls += "foreground" },
        )
        assertEquals(listOf("plain", "foreground"), calls)
    }

    @Test
    fun `other failures don't try a foreground start`() {
        val calls = mutableListOf<String>()
        val result = ServiceStart.start(
            plain = { calls += "plain"; throw SecurityException("no") },
            foreground = { calls += "foreground" },
        )
        assertEquals(listOf("plain"), calls)
        assertEquals(true, result.isFailure)
    }
}
