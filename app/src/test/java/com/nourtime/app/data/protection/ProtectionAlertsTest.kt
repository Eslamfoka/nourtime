package com.nourtime.app.data.protection

import org.junit.Assert.assertEquals
import org.junit.Test

// Found in the 2026-10-12 audit: safe mode, another user and USB debugging all switch protection off
// without the parent ever hearing about it.
class ProtectionAlertsTest {

    private val day = 24 * 60 * 60_000L

    @Test
    fun `the first boot Nour Time sees is not a missed restart`() {
        assertEquals(0, RestartCheck.missedBoots(lastSeen = null, current = 7))
    }

    @Test
    fun `the same boot or the next one is fine`() {
        assertEquals(0, RestartCheck.missedBoots(lastSeen = 7, current = 7))
        assertEquals(0, RestartCheck.missedBoots(lastSeen = 7, current = 8))
    }

    @Test
    fun `a boot Nour Time never saw is a missed restart`() {
        // Safe mode: Nour Time doesn't run, so that boot is skipped in its count.
        assertEquals(1, RestartCheck.missedBoots(lastSeen = 7, current = 9))
    }

    @Test
    fun `a count that went back (factory reset, other phone) is not an alert`() {
        assertEquals(0, RestartCheck.missedBoots(lastSeen = 40, current = 3))
    }

    @Test
    fun `recent events and USB debugging are shown`() {
        val a = ProtectionAlerts(missedRestartAtMs = 10 * day, otherUserAtMs = 11 * day, usbDebugging = true)
        assertEquals(
            listOf(ProtectionAlert.USB_DEBUGGING, ProtectionAlert.MISSED_RESTART, ProtectionAlert.OTHER_USER),
            a.visible(nowMs = 12 * day),
        )
    }

    @Test
    fun `events older than a week are no longer shown`() {
        val a = ProtectionAlerts(missedRestartAtMs = 1 * day, otherUserAtMs = 5 * day)
        assertEquals(listOf(ProtectionAlert.OTHER_USER), a.visible(nowMs = 9 * day))
    }

    @Test
    fun `alerts survive the trip to the parent's phone`() {
        val a = ProtectionAlerts(missedRestartAtMs = 123, otherUserAtMs = null, usbDebugging = true)
        assertEquals(a, ProtectionAlerts.fromMap(a.toMap()))
    }

    @Test
    fun `a phone that never uploaded alerts has none`() {
        assertEquals(ProtectionAlerts(), ProtectionAlerts.fromMap(null))
    }
}
