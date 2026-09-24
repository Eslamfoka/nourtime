package com.nourtime.app.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class TrustedTimeTest {

    private val hour = 3_600_000L
    private val t0 = 1_790_000_000_000L // some wall time

    @Test
    fun `automatic time trusts the system clock`() {
        val a = TrustedTime.now(null, t0, 1_000, 1, autoTime = true)
        assertEquals(t0 + 5 * hour, TrustedTime.now(a, t0 + 5 * hour, 2_000, 1, autoTime = true).lastSeenWallMs)
    }

    @Test
    fun `manual clock change is ignored on the same boot`() {
        val a = TrustedTime.now(null, t0, 1_000, 1, autoTime = false)
        // One minute later the child moves the clock forward 10 hours.
        val b = TrustedTime.now(a, t0 + 10 * hour, 61_000, 1, autoTime = false)
        assertEquals(t0 + 60_000, b.lastSeenWallMs)
        // ...or back 10 hours.
        val c = TrustedTime.now(b, t0 - 10 * hour, 121_000, 1, autoTime = false)
        assertEquals(t0 + 120_000, c.lastSeenWallMs)
    }

    @Test
    fun `setting the clock back and rebooting doesn't rewind time`() {
        val a = TrustedTime.now(null, t0, 1_000, 1, autoTime = false)
        val b = TrustedTime.now(a, t0, 1_000 + hour, 1, autoTime = false) // saw t0 + 1h
        val afterReboot = TrustedTime.now(b, t0 - 3 * hour, 5_000, 2, autoTime = false)
        assertEquals(t0 + hour, afterReboot.lastSeenWallMs)
        assertEquals(2, afterReboot.bootCount)
    }

    @Test
    fun `after a reboot a later system clock is accepted`() {
        val a = TrustedTime.now(null, t0, 1_000, 1, autoTime = false)
        val afterReboot = TrustedTime.now(a, t0 + 8 * hour, 5_000, 2, autoTime = false)
        assertEquals(t0 + 8 * hour, afterReboot.lastSeenWallMs)
    }
}
