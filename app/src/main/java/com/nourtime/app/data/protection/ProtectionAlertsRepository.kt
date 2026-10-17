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
            pinResetAtMs = prefs[PIN_RESET_AT],
            notificationsOff = prefs[NOTIFICATIONS_OFF] ?: false,
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

    /**
     * The phone switched to another user at [nowElapsed] of boot [bootCount] ([nowWallMs]: trusted
     * wall time, for after a restart), with [lockLeftMs] of a lock period left; see [takeOtherUserAway].
     */
    suspend fun recordOtherUserSince(nowElapsed: Long, bootCount: Int, nowWallMs: Long, lockLeftMs: Long) {
        store.edit {
            it[OTHER_USER_SINCE] = nowElapsed
            it[OTHER_USER_SINCE_BOOT] = bootCount
            it[OTHER_USER_SINCE_WALL] = nowWallMs
            it[OTHER_USER_LOCK_LEFT] = lockLeftMs
        }
    }

    /**
     * Back in this user: how long the phone was in another one, once, then forgotten; null when it
     * wasn't. Measured in elapsed time, or after a restart (which restarts elapsed time, and is an easy
     * way out of a guest session) in trusted wall time, at most [MAX_AWAY_AFTER_RESTART_MS].
     */
    suspend fun takeOtherUserAway(nowElapsed: Long, bootCount: Int, nowWallMs: Long): OtherUserAway? {
        var away: OtherUserAway? = null
        store.edit { prefs ->
            val since = prefs[OTHER_USER_SINCE] ?: return@edit
            val ms = if (prefs[OTHER_USER_SINCE_BOOT] == bootCount) {
                nowElapsed - since
            } else {
                (nowWallMs - (prefs[OTHER_USER_SINCE_WALL] ?: nowWallMs)).coerceAtMost(MAX_AWAY_AFTER_RESTART_MS)
            }
            away = OtherUserAway(ms.coerceAtLeast(0), prefs[OTHER_USER_LOCK_LEFT] ?: 0)
            prefs.remove(OTHER_USER_SINCE)
            prefs.remove(OTHER_USER_SINCE_BOOT)
            prefs.remove(OTHER_USER_SINCE_WALL)
            prefs.remove(OTHER_USER_LOCK_LEFT)
        }
        return away
    }

    suspend fun recordPinReset(nowMs: Long) {
        store.edit { it[PIN_RESET_AT] = nowMs }
    }

    suspend fun setNotificationsOff(off: Boolean) {
        store.edit { it[NOTIFICATIONS_OFF] = off }
    }

    suspend fun setUsbDebugging(on: Boolean) {
        store.edit { it[USB_DEBUGGING] = on }
    }

    companion object {
        /** A restart hides how long the phone was away; wall time counts, but no more than this. */
        const val MAX_AWAY_AFTER_RESTART_MS = 24 * 60 * 60_000L

        private val LAST_BOOT = intPreferencesKey("protection_last_boot")
        private val MISSED_RESTART_AT = longPreferencesKey("protection_missed_restart_at")
        private val OTHER_USER_AT = longPreferencesKey("protection_other_user_at")
        private val OTHER_USER_SINCE = longPreferencesKey("protection_other_user_since_elapsed")
        private val OTHER_USER_SINCE_BOOT = intPreferencesKey("protection_other_user_since_boot")
        private val OTHER_USER_SINCE_WALL = longPreferencesKey("protection_other_user_since_wall")
        private val OTHER_USER_LOCK_LEFT = longPreferencesKey("protection_other_user_lock_left")
        private val USB_DEBUGGING = booleanPreferencesKey("protection_usb_debugging")
        private val PIN_RESET_AT = longPreferencesKey("protection_pin_reset_at")
        private val NOTIFICATIONS_OFF = booleanPreferencesKey("protection_notifications_off")
    }
}

/** Time spent in another user, and the lock time that was left when the phone switched (0 = none). */
data class OtherUserAway(val awayMs: Long, val lockLeftAtStartMs: Long)
