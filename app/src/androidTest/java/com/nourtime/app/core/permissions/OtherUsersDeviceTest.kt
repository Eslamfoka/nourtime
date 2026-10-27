package com.nourtime.app.core.permissions

import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nourtime.app.testing.Device
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On a real phone: Nour Time (an ordinary app, target API 37) can read the hidden "Multiple users"
 * setting, so the parent sees whether the switch is on. The setting is put back afterwards.
 */
@RunWith(AndroidJUnit4::class)
class OtherUsersDeviceTest {

    private var before: String = "null"

    @Before
    fun setUp() {
        assumeTrue("this phone has no multiple users", UserManager.supportsMultipleUsers())
        before = Device.shell("settings get global ${OtherUsers.USER_SWITCHER_ENABLED}").trim()
    }

    @After
    fun tearDown() {
        if (before == "null") {
            Device.shell("settings delete global ${OtherUsers.USER_SWITCHER_ENABLED}")
        } else {
            Device.shell("settings put global ${OtherUsers.USER_SWITCHER_ENABLED} $before")
        }
    }

    @Test
    fun multipleUsersSwitch_isReadByTheApp() {
        Device.shell("settings put global ${OtherUsers.USER_SWITCHER_ENABLED} 1")
        assertEquals(OtherUsersState.ON, OtherUsers.read(Device.context))
        Device.shell("settings put global ${OtherUsers.USER_SWITCHER_ENABLED} 0")
        assertEquals(OtherUsersState.OFF, OtherUsers.read(Device.context))
        Device.shell("settings delete global ${OtherUsers.USER_SWITCHER_ENABLED}")
        assertEquals(OtherUsersState.UNKNOWN, OtherUsers.read(Device.context))
    }
}
