package com.nourtime.app.data.mode

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppModeRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy { PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("m.preferences_pb") } }
    private val repo by lazy { AppModeRepository(store) }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `a fresh install asks`() = runTest {
        assertNull(repo.mode.first())
    }

    @Test
    fun `remembers the choice`() = runTest {
        repo.setMode(AppMode.PARENT)
        assertEquals(AppMode.PARENT, repo.mode.first())
    }

    @Test
    fun `phones already being set up before Phase 2 are child phones`() = runTest {
        store.edit { it[stringPreferencesKey("onboarding_step")] = "CREATE_PIN" }
        assertEquals(AppMode.CHILD, repo.mode.first())
    }

    @Test
    fun `phones set up before Phase 2 are child phones`() = runTest {
        store.edit { it[booleanPreferencesKey("onboarding_complete")] = true }
        assertEquals(AppMode.CHILD, repo.mode.first())
    }

    @Test
    fun `a phone still on the welcome screen asks`() = runTest {
        store.edit { it[stringPreferencesKey("onboarding_step")] = "WELCOME" }
        assertNull(repo.mode.first())
    }
}
