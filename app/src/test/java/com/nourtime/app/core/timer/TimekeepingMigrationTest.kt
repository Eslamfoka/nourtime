package com.nourtime.app.core.timer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The timer and trusted-clock state move out of the settings file (with the PIN) once, keeping their values. */
class TimekeepingMigrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val phase = stringPreferencesKey("timer_phase")
    private val remaining = longPreferencesKey("timer_remaining")
    private val trustedBoot = intPreferencesKey("trusted_boot")
    private val pinHash = stringPreferencesKey("pin_hash")

    @Test
    fun `old timer and clock keys move to the timekeeping file and leave the settings file`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val main = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("main.preferences_pb") }
        main.edit {
            it[phase] = "AVAILABLE"
            it[remaining] = 1_234L
            it[trustedBoot] = 7
            it[pinHash] = "hash"
        }
        val timekeeping = PreferenceDataStoreFactory.create(
            migrations = listOf(TimekeepingMigration(main)),
            scope = scope,
        ) { tmp.newFile("timer.preferences_pb") }

        val moved = timekeeping.data.first()
        assertEquals("AVAILABLE", moved[phase])
        assertEquals(1_234L, moved[remaining])
        assertEquals(7, moved[trustedBoot])
        assertNull(moved[pinHash])

        val left = main.data.first()
        assertNull(left[phase])
        assertNull(left[remaining])
        assertNull(left[trustedBoot])
        assertEquals("hash", left[pinHash])
    }

    @Test
    fun `nothing is moved once the timekeeping file has its own state`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val main = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("main.preferences_pb") }
        main.edit { it[remaining] = 1L }
        val own = mutablePreferencesOf(phase to "LOCKED", remaining to 99L)
        assertFalse(TimekeepingMigration(main).shouldMigrate(own))
        assertTrue(TimekeepingMigration(main).shouldMigrate(emptyPreferences()))
    }

    @Test
    fun `a fresh install has nothing to move`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val main = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("main.preferences_pb") }
        main.edit { it[pinHash] = "hash" }
        val timekeeping = PreferenceDataStoreFactory.create(
            migrations = listOf(TimekeepingMigration(main)),
            scope = scope,
        ) { tmp.newFile("timer.preferences_pb") }
        assertEquals(0, timekeeping.data.first().asMap().size)
        assertEquals("hash", main.data.first()[pinHash])
    }
}
