package com.nourtime.app.remote.child

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 2026-10-28 review: a record naming another device refused the phone every new code for good. */
class PairingRecoveryTest {

    private class Refused : Exception("PERMISSION_DENIED")

    private val isRefused: (Throwable) -> Boolean = { it is Refused }

    @Test
    fun `no stale device when the record names this phone's device or nothing`() {
        assertNull(PairingRecovery.staleDevice("device-1", "device-1"))
        assertNull(PairingRecovery.staleDevice(null, "device-1"))
        assertNull(PairingRecovery.staleDevice("", "device-1"))
    }

    @Test
    fun `the record naming another device names the stale one`() {
        assertEquals("old-device", PairingRecovery.staleDevice("old-device", "device-1"))
    }

    @Test
    fun `a first success needs no recovery`() = runTest {
        var recovered = 0
        val result = PairingRecovery.retryOnceAfter(isRefused, { recovered++ }) { "ok" }
        assertEquals("ok", result)
        assertEquals(0, recovered)
    }

    @Test
    fun `a refusal clears the old record and tries once more`() = runTest {
        var recovered = 0
        var attempts = 0
        val result = PairingRecovery.retryOnceAfter(isRefused, { recovered++ }) {
            attempts++
            if (recovered == 0) throw Refused() else "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, recovered)
        assertEquals(2, attempts)
    }

    @Test
    fun `refused again after the recovery is a clear pairing error`() = runTest {
        var attempts = 0
        val refused = Refused()
        try {
            PairingRecovery.retryOnceAfter(isRefused, {}) {
                attempts++
                throw refused
            }
            fail("expected PairingRefusedException")
        } catch (e: PairingRefusedException) {
            assertSame(refused, e.cause)
        }
        assertEquals(2, attempts)
    }

    @Test
    fun `other failures pass through without a recovery`() = runTest {
        var recovered = false
        val offline = IllegalStateException("offline")
        try {
            PairingRecovery.retryOnceAfter(isRefused, { recovered = true }) { throw offline }
            fail("expected the original failure")
        } catch (e: IllegalStateException) {
            assertSame(offline, e)
        }
        assertTrue(!recovered)
    }
}
