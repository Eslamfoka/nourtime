package com.nourtime.app.data.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class UiPreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy { PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("u.preferences_pb") } }
    private val prefs by lazy { UiPreferences(store) }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `the dial is the default`() = runTest {
        assertEquals(TimePickerStyle.DIAL, prefs.timePickerStyle.first())
    }

    @Test
    fun `remembers the parent's choice`() = runTest {
        prefs.setTimePickerStyle(TimePickerStyle.SURPRISE)
        assertEquals(TimePickerStyle.SURPRISE, prefs.timePickerStyle.first())
    }

    @Test
    fun `an unknown saved value falls back to the dial`() = runTest {
        store.edit { it[stringPreferencesKey("ui_time_picker_style")] = "HOLOGRAM" }
        assertEquals(TimePickerStyle.DIAL, prefs.timePickerStyle.first())
    }

    @Test
    fun `surprise picks one of the three real themes`() {
        val picked = (0 until 200).map { TimePickerStyle.SURPRISE.resolve(Random(it)) }.toSet()
        assertEquals(setOf(TimePickerStyle.DIAL, TimePickerStyle.TOKENS, TimePickerStyle.LIQUID), picked)
    }

    @Test
    fun `a chosen theme resolves to itself`() {
        assertEquals(TimePickerStyle.LIQUID, TimePickerStyle.LIQUID.resolve(Random(1)))
    }
}
