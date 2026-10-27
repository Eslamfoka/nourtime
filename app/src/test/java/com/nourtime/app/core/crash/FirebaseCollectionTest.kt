package com.nourtime.app.core.crash

import com.nourtime.app.data.mode.AppMode
import org.junit.Assert.assertEquals
import org.junit.Test

// "Without a connected parent's phone, nothing leaves your child's phone": Firebase's own start-up
// (crash reports, an FCM token) stays off until a phone is connected, and pushes only on the parent's.
class FirebaseCollectionTest {

    private fun of(debug: Boolean = false, mode: AppMode? = AppMode.CHILD, paired: Boolean = false, signedIn: Boolean = false) =
        FirebaseCollection.of(debug, mode, childPaired = paired, parentSignedIn = signedIn)

    @Test
    fun `a phone that isn't connected turns nothing on`() {
        assertEquals(FirebaseCollection.NONE, of(mode = AppMode.CHILD, paired = false))
        assertEquals(FirebaseCollection.NONE, of(mode = AppMode.PARENT, signedIn = false))
        assertEquals(FirebaseCollection.NONE, of(mode = null, paired = true, signedIn = true))
    }

    @Test
    fun `a paired child's phone reports crashes but never makes a push token`() {
        assertEquals(FirebaseCollection(crashReports = true, pushes = false), of(paired = true))
    }

    @Test
    fun `a signed-in parent's phone reports crashes and receives pushes`() {
        assertEquals(FirebaseCollection(crashReports = true, pushes = true), of(mode = AppMode.PARENT, signedIn = true))
    }

    @Test
    fun `debug builds receive pushes but never report crashes`() {
        assertEquals(FirebaseCollection(crashReports = false, pushes = true), of(debug = true, mode = AppMode.PARENT, signedIn = true))
        assertEquals(FirebaseCollection.NONE, of(debug = true, paired = true))
    }

    @Test
    fun `a child's mode ignores a parent sign-in, and a parent's ignores pairing`() {
        assertEquals(false, of(mode = AppMode.CHILD, signedIn = true).pushes)
        assertEquals(FirebaseCollection.NONE, of(mode = AppMode.PARENT, paired = true))
    }
}
