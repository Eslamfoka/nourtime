package com.nourtime.app.remote.parent

import com.nourtime.app.data.protection.ProtectionAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The server's data-only pushes (firebase/functions/src/send.ts) as the parent's phone reads them. */
class PushMessageTest {

    private val base = mapOf("deviceId" to "d1", "deviceName" to "Galaxy A10")

    @Test
    fun `reads an ask`() {
        assertEquals(PushMessage.Ask("d1", "Galaxy A10"), pushMessageOf(base + ("type" to "ASK")))
    }

    @Test
    fun `reads alerts`() {
        assertEquals(PushMessage.Alert("d1", "Galaxy A10", ProtectionAlert.USB_DEBUGGING), pushMessageOf(base + ("type" to "USB_DEBUGGING")))
        assertEquals(
            PushMessage.Alert("d1", "Galaxy A10", ProtectionAlert.PROTECTION_DEGRADED),
            pushMessageOf(base + ("type" to "PROTECTION_DEGRADED")),
        )
    }

    @Test
    fun `reads a waiting PIN reset with its time`() {
        assertEquals(
            PushMessage.PinResetPending("d1", "Galaxy A10", 1_800_000_000_000),
            pushMessageOf(base + ("type" to "PIN_RESET_PENDING") + ("pendingUntil" to "1800000000000")),
        )
    }

    @Test
    fun `reads a phone that stopped checking in, with when it last did`() {
        assertEquals(
            PushMessage.Quiet("d1", "Galaxy A10", 1_800_000_000_000),
            pushMessageOf(base + ("type" to "QUIET") + ("lastSeenAt" to "1800000000000")),
        )
    }

    @Test
    fun `unknown types, a missing device or a bad time are ignored`() {
        assertNull(pushMessageOf(base + ("type" to "WIPE")))
        assertNull(pushMessageOf(mapOf("type" to "ASK")))
        assertNull(pushMessageOf(base + ("type" to "PIN_RESET_PENDING") + ("pendingUntil" to "soon")))
        assertNull(pushMessageOf(base + ("type" to "QUIET")))
    }

    @Test
    fun `only pushes for the parent signed in on this phone are shown`() {
        val push = base + ("type" to "ASK") + ("ownerUid" to "p1")
        assert(pushIsFor(push, signedInUid = "p1"))
        assert(!pushIsFor(push, signedInUid = "p2"))
        assert(!pushIsFor(push, signedInUid = null))
        assert(!pushIsFor(base + ("type" to "ASK"), signedInUid = "p1"))
    }

    @Test
    fun `each phone and type gets its own notification, replaced rather than stacked`() {
        val a = PushMessage.Ask("d1", "x").notificationId
        assertEquals(a, PushMessage.Ask("d1", "y").notificationId)
        assert(a != PushMessage.Ask("d2", "x").notificationId)
        assert(a != PushMessage.Alert("d1", "x", ProtectionAlert.OTHER_USER).notificationId)
        assert(
            PushMessage.Alert("d1", "x", ProtectionAlert.OTHER_USER).notificationId !=
                PushMessage.Alert("d1", "x", ProtectionAlert.USB_DEBUGGING).notificationId,
        )
    }
}
