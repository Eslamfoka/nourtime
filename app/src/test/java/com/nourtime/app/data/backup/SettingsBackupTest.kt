package com.nourtime.app.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// Found in the 2026-10-12 audit: a damaged settings file was replaced by an empty one, losing the PIN
// and the app mode, so the phone went back to "Whose phone is this?" and protection was off.
class SettingsBackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val pin = stringPreferencesKey("pin_hash")

    private fun newScope() = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    private val mainFile by lazy { File(tmp.root, "main.preferences_pb") }
    private val backup by lazy {
        SettingsBackup(PreferenceDataStoreFactory.create(scope = newScope()) { File(tmp.root, "backup.preferences_pb") })
    }

    private fun mainStore() = SettingsBackup.mainStore(backup, newScope()) { mainFile }

    @Test
    fun `a damaged settings file comes back from the backup`() = runTest {
        // Only one open store per file: the first one is closed before the phone "restarts".
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val first = SettingsBackup.mainStore(backup, firstScope) { mainFile }
        first.edit { it[pin] = "hash" }
        backup.save(first.data.first())
        firstScope.cancel()

        mainFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        assertEquals("hash", mainStore().data.first()[pin])
    }

    @Test
    fun `without a backup a damaged file starts empty instead of crashing`() = runTest {
        mainFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        assertEquals(null, mainStore().data.first()[pin])
    }

    @Test
    fun `the backup follows the settings`() = runTest {
        val store = mainStore()
        backgroundScope.launch { backup.mirror(store) }
        store.edit { it[pin] = "first" }
        advanceTimeBy(SettingsBackup.MIRROR_EVERY_MS + 1)
        store.edit { it[pin] = "second" }
        advanceTimeBy(SettingsBackup.MIRROR_EVERY_MS + 1)
        // DataStore writes on its own thread; wait for the backup to catch up.
        var saved: String? = null
        repeat(50) {
            saved = backup.restore()[pin]
            if (saved == "second") return@repeat
            Thread.sleep(20)
            advanceTimeBy(SettingsBackup.MIRROR_EVERY_MS + 1)
        }
        assertEquals("second", saved)
    }
}
