package com.nourtime.app.remote.parent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaimCheckTest {

    private val created = 1_000_000L

    @Test
    fun `an open code can be claimed`() {
        assertNull(ClaimCheck.before(exists = true, createdAtMs = created, claimedBy = null, myUid = "me", nowMs = created + 60_000))
    }

    @Test
    fun `an unknown code`() {
        assertEquals(ClaimResult.NOT_FOUND, ClaimCheck.before(exists = false, createdAtMs = null, claimedBy = null, myUid = "me", nowMs = created))
    }

    @Test
    fun `an expired code`() {
        assertEquals(ClaimResult.EXPIRED, ClaimCheck.before(true, created, null, "me", nowMs = created + 10 * 60_000))
    }

    @Test
    fun `a code someone else claimed`() {
        assertEquals(ClaimResult.ALREADY_CLAIMED, ClaimCheck.before(true, created, "someone-else", "me", nowMs = created + 1_000))
    }

    @Test
    fun `my own earlier claim just waits for the child`() {
        assertEquals(ClaimResult.WAITING_FOR_CHILD, ClaimCheck.before(true, created, "me", "me", nowMs = created + 1_000))
    }
}
