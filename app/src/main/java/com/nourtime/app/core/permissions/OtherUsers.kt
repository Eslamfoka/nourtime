package com.nourtime.app.core.permissions

import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.Settings
import android.util.Log

/** Whether this phone can switch to a guest or another user, where Nour Time can't limit anything. */
enum class OtherUsersState {
    /** The phone has no multiple users: nothing to recommend. */
    UNSUPPORTED,

    /** "Multiple users" is switched off: no user switcher in the shade or on the lock screen. */
    OFF,

    /** "Multiple users" is on: a guest or another user can be started from the shade or the lock screen. */
    ON,

    /** The phone doesn't say (never switched, or the setting can't be read): the advice is shown anyway. */
    UNKNOWN,
    ;

    /** The parent should look at the phone's Users screen. */
    val recommendTurningOff: Boolean get() = this == ON || this == UNKNOWN
}

/**
 * Guest and other users can't be blocked by an app that isn't the device owner (`DISALLOW_USER_SWITCH`
 * and `DISALLOW_ADD_USER` need one), and Android has no public call that says whether they're offered
 * (`getUserCount` needs a system permission). The "Multiple users" switch in Settings is stored in the
 * global setting `user_switcher_enabled`, which an ordinary app can read (checked on Android 16,
 * OtherUsersDeviceTest). It's absent until the switch is first touched (the phone maker's default
 * applies, which apps can't see), and a phone maker may store it elsewhere or refuse the read: unknown.
 */
object OtherUsers {

    /** [switcherSetting] is `user_switcher_enabled`, or null when it's absent or couldn't be read. */
    fun state(supportsMultipleUsers: Boolean, switcherSetting: Int?): OtherUsersState = when {
        !supportsMultipleUsers -> OtherUsersState.UNSUPPORTED
        switcherSetting == null -> OtherUsersState.UNKNOWN
        switcherSetting == 0 -> OtherUsersState.OFF
        else -> OtherUsersState.ON
    }

    fun read(context: Context): OtherUsersState {
        val supported = UserManager.supportsMultipleUsers()
        val setting = if (supported) {
            runCatching { Settings.Global.getString(context.contentResolver, USER_SWITCHER_ENABLED)?.trim()?.toIntOrNull() }
                .onFailure { Log.w("OtherUsers", "couldn't read $USER_SWITCHER_ENABLED", it) }
                .getOrNull()
        } else {
            null
        }
        return state(supported, setting)
    }

    /** The phone's Users screen (no public constant; most phones have it), else Settings. */
    fun settingsIntents(): List<Intent> = listOf(Intent(USER_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    /** `Settings.Global.USER_SWITCHER_ENABLED`, hidden from the SDK. */
    const val USER_SWITCHER_ENABLED = "user_switcher_enabled"
    private const val USER_SETTINGS = "android.settings.USER_SETTINGS"
}
