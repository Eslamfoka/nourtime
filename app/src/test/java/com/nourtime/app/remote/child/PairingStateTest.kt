package com.nourtime.app.remote.child

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingStateTest {

    private val created = 1_000_000L
    private val code = "123456"

    private fun of(exists: Boolean = true, claimedBy: String? = null, now: Long = created + 60_000, createdAt: Long? = created) =
        PairingStates.of(code, exists, claimedBy, claimedName = "Mom", claimedEmail = "mom@example.com", createdAtMs = createdAt, localStartMs = created, nowMs = now)

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
    fun `before the server time arrives the phone's own start time is used`() {
        assertEquals(PairingState.Waiting(code, expiresAtMs = created + PairingStates.VALID_MS), of(createdAt = null))
    }
}
