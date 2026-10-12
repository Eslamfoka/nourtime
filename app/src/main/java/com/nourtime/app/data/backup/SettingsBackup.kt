package com.nourtime.app.data.backup

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * A second copy of the settings file. A damaged settings file used to be replaced by an empty one,
 * losing the PIN and the app mode, so protection was off until the parent set the phone up again;
 * now it comes back from this copy (at most [MIRROR_EVERY_MS] old).
 */
class SettingsBackup(private val backup: DataStore<Preferences>) {

    suspend fun save(prefs: Preferences) {
        backup.updateData { prefs }
    }

    /** The last copy, or nothing if it is missing or damaged too. */
    suspend fun restore(): Preferences = try {
        backup.data.first().also { runCatching { Log.w(TAG, "restored ${it.asMap().size} settings from the backup") } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        runCatching { Log.e(TAG, "couldn't read the settings backup", e) }
        emptyPreferences()
    }

    /**
     * Keeps the copy current: at once, then at most every [MIRROR_EVERY_MS] (the timer saves every few
     * seconds, and copying each of those would double the writes).
     */
    suspend fun mirror(main: DataStore<Preferences>) {
        main.data.conflate().collect { prefs ->
            save(prefs)
            delay(MIRROR_EVERY_MS)
        }
    }

    companion object {
        const val MIRROR_EVERY_MS = 30_000L
        private const val TAG = "SettingsBackup"

        fun mainStore(backup: SettingsBackup, scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { e ->
                    runCatching { Log.w(TAG, "settings file damaged, restoring the backup", e) }
                    // Not a suspend callback; it runs once, on DataStore's own I/O thread.
                    runBlocking { backup.restore() }
                },
                scope = scope,
                produceFile = produceFile,
            )
    }
}
