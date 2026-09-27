package com.nourtime.app.data.mode

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Which side of Nour Time this phone is (Phase 2): the child's phone, or a parent's phone. */
enum class AppMode { CHILD, PARENT }

@Singleton
class AppModeRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    /** Null until chosen. Phones already being set up before Phase 2 existed are child phones. */
    val mode: Flow<AppMode?> = store.data.map { prefs ->
        prefs[MODE]?.let { name -> AppMode.entries.firstOrNull { it.name == name } }
            ?: AppMode.CHILD.takeIf { prefs[ONBOARDING_COMPLETE] == true || prefs[ONBOARDING_STEP].let { it != null && it != "WELCOME" } }
    }.distinctUntilChanged()

    suspend fun setMode(mode: AppMode) {
        store.edit { it[MODE] = mode.name }
    }

    private companion object {
        val MODE = stringPreferencesKey("app_mode")

        // Owned by OnboardingRepository; read here only to recognise existing child phones.
        val ONBOARDING_STEP = stringPreferencesKey("onboarding_step")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
