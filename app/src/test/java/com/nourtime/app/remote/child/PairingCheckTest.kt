package com.nourtime.app.remote.child

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review Focus 4: the parent removed this phone while it was offline. */
class PairingCheckTest {

    private val owner = PairedOwner("parent-uid", "p@example.com", "Mom")

    @Test
    fun `still paired while the device names the same owner`() {
        assertTrue(PairingCheck.stillPaired(owner, docExists = true, docOwnerUid = "parent-uid"))
    }

    @Test
    fun `removed when the owner is cleared`() {
        assertFalse(PairingCheck.stillPaired(owner, docExists = true, docOwnerUid = null))
    }

    @Test
    fun `removed when another owner took over`() {
        assertFalse(PairingCheck.stillPaired(owner, docExists = true, docOwnerUid = "someone-else"))
    }

    @Test
    fun `a missing snapshot (not loaded yet) is not a removal`() {
        assertTrue(PairingCheck.stillPaired(owner, docExists = false, docOwnerUid = null))
    }
}
