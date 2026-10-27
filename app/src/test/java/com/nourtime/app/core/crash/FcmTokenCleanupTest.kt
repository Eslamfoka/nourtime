package com.nourtime.app.core.crash

import org.junit.Assert.assertEquals
import org.junit.Test

// "Nothing leaves this phone": a phone that never had an FCM token never calls FCM (or Firebase
// Installations) just to delete one; a phone that may have one deletes it once.
class FcmTokenCleanupTest {

    @Test
    fun `a fresh install has no token and never touches FCM while pushes are off`() {
        val mayExist = FcmTokenCleanup.tokenMayExist(stored = null, updatedFromOlderVersion = false)
        assertEquals(false, mayExist)
        assertEquals(FcmAction.NOTHING, FcmTokenCleanup.action(pushes = false, tokenMayExist = mayExist))
    }

    @Test
    fun `a phone updated from an older version deletes the token it may have, once`() {
        val mayExist = FcmTokenCleanup.tokenMayExist(stored = null, updatedFromOlderVersion = true)
        assertEquals(FcmAction.TURN_OFF_AND_DELETE, FcmTokenCleanup.action(pushes = false, tokenMayExist = mayExist))
        // After the delete the flag says none, and later updates don't bring the delete back.
        assertEquals(false, FcmTokenCleanup.tokenMayExist(stored = false, updatedFromOlderVersion = true))
        assertEquals(FcmAction.NOTHING, FcmTokenCleanup.action(pushes = false, tokenMayExist = false))
    }

    @Test
    fun `the saved flag wins over the install times`() {
        assertEquals(true, FcmTokenCleanup.tokenMayExist(stored = true, updatedFromOlderVersion = false))
        assertEquals(false, FcmTokenCleanup.tokenMayExist(stored = false, updatedFromOlderVersion = true))
    }

    @Test
    fun `after pushes were on, turning them off deletes the token`() {
        assertEquals(FcmAction.TURN_OFF_AND_DELETE, FcmTokenCleanup.action(pushes = false, tokenMayExist = true))
    }

    @Test
    fun `a signed-in parent's phone turns pushes on, token or not`() {
        assertEquals(FcmAction.TURN_ON, FcmTokenCleanup.action(pushes = true, tokenMayExist = false))
        assertEquals(FcmAction.TURN_ON, FcmTokenCleanup.action(pushes = true, tokenMayExist = true))
    }
}
