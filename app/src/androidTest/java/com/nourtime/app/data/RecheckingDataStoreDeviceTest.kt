package com.nourtime.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nourtime.app.testing.Device
import com.nourtime.app.testing.TestFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * On a real phone: a subscriber that starts while a write lands still gets it. Plain DataStore missed
 * 1 in 300 here on API 37; [RecheckingDataStore] must miss none.
 */
@RunWith(AndroidJUnit4::class)
class RecheckingDataStoreDeviceTest {

    @Test
    fun aSubscriberStartingDuringAWrite_seesIt() = runBlocking {
        val key = intPreferencesKey("v")
        var missed = 0
        repeat(300) { i ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val store = RecheckingDataStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    Device.context.preferencesDataStoreFile(TestFiles.PREFIX + "probe_" + UUID.randomUUID())
                },
            )
            store.edit { it[key] = 1 }
            val seen = java.util.concurrent.atomic.AtomicInteger(-1)
            val watcher = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            watcher.launch { store.data.map { it[key] ?: 0 }.collect { seen.set(it) } }
            store.edit { it[key] = 2 }
            if (!Device.waitFor(2_000) { seen.get() == 2 }) missed++
            watcher.cancel()
            scope.cancel()
        }
        assertEquals(0, missed)
    }
}
