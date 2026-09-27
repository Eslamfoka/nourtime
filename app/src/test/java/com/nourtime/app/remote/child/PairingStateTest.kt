package com.nourtime.app.remote.child

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingStateTest {

    private val created = 1_000_000L
    private val code = "123456"

    private fun of(exists: Boolean = true, claimedBy: String? = null, now: Long = created + 60_000) =
        PairingStates.of(code, exists, claimedBy, claimedName = "Mom", claimedEmail = "mom@example.com", localStartMs = created, nowMs = now)

    @Test
    fun `waits for the parent while the code is valid`() {
        assertEquals(PairingState.Waiting(code, expiresAtMs = created + PairingStates.VALID_MS), of())
    }

    @Test
    fun `a claim asks the child's phone to confirm`() {
        assertEquals(PairingState.Claimed(code, "Mom", "mom@example.com"), of(claimedBy = "parent-uid"))
    }

    @Test
    fun `the code expires after 10 minutes`() {
        assertEquals(PairingState.Expired, of(now = created + PairingStates.VALID_MS))
    }

    @Test
    fun `a claim that arrives after the expiry still counts`() {
        // The server accepted it while the code was valid; the snapshot just came late.
        assertEquals(PairingState.Claimed(code, "Mom", "mom@example.com"), of(claimedBy = "parent-uid", now = created + PairingStates.VALID_MS + 5_000))
    }

    @Test
    fun `a deleted pairing has ended`() {
        assertEquals(PairingState.Expired, of(exists = false))
    }

    @Test
    fun `the countdown runs on this phone's own clock from when it started`() {
        // Review I4: server time isn't compared with a phone clock that may be off; the rules enforce the real expiry.
        assertEquals(PairingState.Waiting(code, expiresAtMs = created + PairingStates.VALID_MS), of(now = created))
    }
}
