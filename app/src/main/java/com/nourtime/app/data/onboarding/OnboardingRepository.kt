package com.nourtime.app.data.onboarding

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

/** Onboarding steps in order. The PIN comes first so the rest of setup is already protected. */
enum class OnboardingStep {
    WELCOME,
    DISCLOSURE,
    CREATE_PIN,
    SECURITY_QUESTION,
    PERM_ACCESSIBILITY,
    PERM_USAGE_ACCESS,
    PERM_OVERLAY,
    PERM_DEVICE_ADMIN,
    PERM_NOTIFICATIONS,
    PERM_BATTERY,
    AUTOSTART,
    FINISHED,
}

@Singleton
class OnboardingRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val step: Flow<OnboardingStep> = store.data
        .map { prefs -> prefs[STEP]?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } } ?: OnboardingStep.WELCOME }
        .distinctUntilChanged()

    val isComplete: Flow<Boolean> = store.data.map { it[COMPLETE] ?: false }.distinctUntilChanged()

    suspend fun setStep(step: OnboardingStep) {
        store.edit { it[STEP] = step.name }
    }

    suspend fun markComplete() {
        store.edit { it[COMPLETE] = true }
    }

    private companion object {
        val STEP = stringPreferencesKey("onboarding_step")
        val COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
