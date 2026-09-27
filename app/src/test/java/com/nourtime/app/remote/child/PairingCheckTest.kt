package com.nourtime.app.remote.child

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review Focus 4: the parent removed this phone while it was offline. */
class PairingCheckTest {

    private val owner = PairedOwner("parent-uid", "p@example.com", "Mom")

    @Test
    fun `still paired while the device names the same owner`() {
        assertTrue(PairingCheck.stillPaired(owner, docExists = true, fromCache = false, docOwnerUid = "parent-uid"))
    }

    @Test
    fun `removed when the owner is cleared`() {
        assertFalse(PairingCheck.stillPaired(owner, docExists = true, fromCache = false, docOwnerUid = null))
    }

    @Test
    fun `removed when another owner took over`() {
        assertFalse(PairingCheck.stillPaired(owner, docExists = true, fromCache = false, docOwnerUid = "someone-else"))
    }

    @Test
    fun `a missing document in the local cache (not loaded yet) is not a removal`() {
        assertTrue(PairingCheck.stillPaired(owner, docExists = false, fromCache = true, docOwnerUid = null))
    }

    @Test
    fun `a document the server says is gone means the phone is no longer paired`() {
        // Its data was deleted (or lost): staying "Connected" would show a parent who can't see anything.
        assertFalse(PairingCheck.stillPaired(owner, docExists = false, fromCache = false, docOwnerUid = null))
    }
}
