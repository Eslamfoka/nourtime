package com.nourtime.app.core.time

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** A wall-clock reading pinned to elapsedRealtime on one boot. */
data class TimeAnchor(val wallMs: Long, val elapsedMs: Long, val bootCount: Int, val lastSeenWallMs: Long)

object TrustedTime {

    /**
     * Wall time that a manual clock change can't move.
     * - Automatic (network) time on: the system clock is trusted.
     * - Otherwise, time advances from the anchor with elapsedRealtime, ignoring clock edits.
     * - After a reboot the gap can't be measured, so the system clock is used but never earlier
     *   than the last time we saw: setting the clock back and rebooting doesn't rewind time.
     */
    fun now(anchor: TimeAnchor?, systemWallMs: Long, elapsedMs: Long, bootCount: Int, autoTime: Boolean): TimeAnchor {
        val wall = when {
            autoTime || anchor == null -> systemWallMs
            anchor.bootCount == bootCount && elapsedMs >= anchor.elapsedMs -> anchor.wallMs + (elapsedMs - anchor.elapsedMs)
            else -> maxOf(systemWallMs, anchor.lastSeenWallMs)
        }
        if (anchor != null && !autoTime && anchor.bootCount == bootCount && elapsedMs >= anchor.elapsedMs) {
            return anchor.copy(lastSeenWallMs = wall)
        }
        return TimeAnchor(wall, elapsedMs, bootCount, wall)
    }
}

/** Trusted local time for bedtime, the daily schedule and the daily reset. */
@Singleton
class TrustedClock @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DataStore<Preferences>,
    private val clock: DeviceClock,
) {
    private val mutex = Mutex()
    private var anchor: TimeAnchor? = null
    private var loaded = false
    private var lastSavedElapsed = Long.MIN_VALUE

    suspend fun now(): ZonedDateTime = mutex.withLock {
        if (!loaded) {
            anchor = load()
            loaded = true
        }
        val previous = anchor
        val next = TrustedTime.now(previous, System.currentTimeMillis(), clock.elapsedRealtime(), clock.bootCount(), autoTimeEnabled())
        anchor = next
        // Saved on a new boot and then once a minute; with automatic time on, the anchor moves every call.
        if (previous == null || next.bootCount != previous.bootCount || next.elapsedMs - lastSavedElapsed > SAVE_EVERY_MS) {
            save(next)
            lastSavedElapsed = next.elapsedMs
        }
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(next.lastSeenWallMs), ZoneId.systemDefault())
    }

    /** The parent confirmed the device time (by unlocking with the PIN): trust the system clock again. */
    suspend fun trustSystemClock() = mutex.withLock {
        val wall = System.currentTimeMillis()
        val fresh = TimeAnchor(wall, clock.elapsedRealtime(), clock.bootCount(), wall)
        anchor = fresh
        loaded = true
        save(fresh)
    }

    private fun autoTimeEnabled(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 1) == 1

    private suspend fun load(): TimeAnchor? {
        val p = store.data.first()
        return TimeAnchor(
            wallMs = p[WALL] ?: return null,
            elapsedMs = p[ELAPSED] ?: return null,
            bootCount = p[BOOT] ?: return null,
            lastSeenWallMs = p[LAST_SEEN] ?: return null,
        )
    }

    private suspend fun save(a: TimeAnchor) {
        store.edit {
            it[WALL] = a.wallMs
            it[ELAPSED] = a.elapsedMs
            it[BOOT] = a.bootCount
            it[LAST_SEEN] = a.lastSeenWallMs
        }
    }

    private companion object {
        const val SAVE_EVERY_MS = 60_000L
        val WALL = longPreferencesKey("trusted_wall")
        val ELAPSED = longPreferencesKey("trusted_elapsed")
        val BOOT = intPreferencesKey("trusted_boot")
        val LAST_SEEN = longPreferencesKey("trusted_last_seen")
    }
}
