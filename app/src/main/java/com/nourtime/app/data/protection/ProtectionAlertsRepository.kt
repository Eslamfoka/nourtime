package com.nourtime.app.data.protection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The child's phone's record of [ProtectionAlerts], and the last boot Nour Time ran in. */
@Singleton
class ProtectionAlertsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val alerts: Flow<ProtectionAlerts> = store.data.map { prefs ->
        ProtectionAlerts(
            missedRestartAtMs = prefs[MISSED_RESTART_AT],
            otherUserAtMs = prefs[OTHER_USER_AT],
            usbDebugging = prefs[USB_DEBUGGING] ?: false,
        )
    }.distinctUntilChanged()

    /** Remembers [bootCount]; true when a restart was missed since the last one (also recorded). */
    suspend fun recordBoot(bootCount: Int, nowMs: Long): Boolean {
        var missed = false
        store.edit { prefs ->
            missed = RestartCheck.missedBoots(prefs[LAST_BOOT], bootCount) > 0
            if (missed) prefs[MISSED_RESTART_AT] = nowMs
            prefs[LAST_BOOT] = bootCount
        }
        return missed
    }

    suspend fun recordOtherUser(nowMs: Long) {
        store.edit { it[OTHER_USER_AT] = nowMs }
    }

    suspend fun setUsbDebugging(on: Boolean) {
        store.edit { it[USB_DEBUGGING] = on }
    }

    private companion object {
        val LAST_BOOT = intPreferencesKey("protection_last_boot")
        val MISSED_RESTART_AT = longPreferencesKey("protection_missed_restart_at")
        val OTHER_USER_AT = longPreferencesKey("protection_other_user_at")
        val USB_DEBUGGING = booleanPreferencesKey("protection_usb_debugging")
    }
}
