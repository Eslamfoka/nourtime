package com.nourtime.app.feature.parent

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Notifications are off" covers the whole app and the pushes' own channel (a parent can mute just that one). */
class ParentNotificationsTest {

    @Test
    fun `on when the app and the channel are on, or the channel doesn't exist yet`() {
        assertTrue(ParentNotifications.pushesOn(appEnabled = true, channelImportance = NotificationManager.IMPORTANCE_HIGH))
        assertTrue(ParentNotifications.pushesOn(appEnabled = true, channelImportance = null))
    }

    @Test
    fun `off when the app's notifications or just the channel are off`() {
        assertFalse(ParentNotifications.pushesOn(appEnabled = false, channelImportance = NotificationManager.IMPORTANCE_HIGH))
        assertFalse(ParentNotifications.pushesOn(appEnabled = true, channelImportance = NotificationManager.IMPORTANCE_NONE))
    }
}
