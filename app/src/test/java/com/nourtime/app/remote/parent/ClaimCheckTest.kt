package com.nourtime.app.remote.parent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A refused blind claim: the code is readable only when it's already ours (firestore.rules). */
class ClaimCheckTest {

    @Test
    fun `my own earlier claim just goes on to wait for the child`() {
        assertNull(ClaimCheck.afterRefusal(claimedBy = "me", myUid = "me"))
    }

    @Test
    fun `an unreadable code is no use, whatever the reason`() {
        assertEquals(ClaimResult.NOT_FOUND, ClaimCheck.afterRefusal(claimedBy = null, myUid = "me"))
    }

    @Test
    fun `a claimed code is gone when the server says it's missing, or stops letting us read it`() {
        assertTrue(ClaimCheck.goneOnServer(exists = false, fromCache = false, denied = false))
        // Deleted by the child: the rules then deny the listener (2026-10-17 review).
        assertTrue(ClaimCheck.goneOnServer(exists = null, fromCache = null, denied = true))
    }

    @Test
    fun `a code still there, or missing only from the cache, isn't gone`() {
        assertFalse(ClaimCheck.goneOnServer(exists = true, fromCache = false, denied = false))
        assertFalse(ClaimCheck.goneOnServer(exists = false, fromCache = true, denied = false))
        // Another listener error (e.g. offline) isn't a refusal.
        assertFalse(ClaimCheck.goneOnServer(exists = null, fromCache = null, denied = false))
    }

    @Test
    fun `someone else's claim is no use`() {
        assertEquals(ClaimResult.NOT_FOUND, ClaimCheck.afterRefusal(claimedBy = "someone-else", myUid = "me"))
    }
}
