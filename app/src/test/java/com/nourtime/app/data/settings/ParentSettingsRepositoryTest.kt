package com.nourtime.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

class ParentSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repo by lazy {
        ParentSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb") })
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `defaults match the brief`() = runTest {
        val s = repo.settings.first()
        assertEquals(60, s.budgetMinutes)
        assertEquals(6, s.lockPeriodHours)
        assertEquals(null, s.gender)
        assertEquals(emptySet<String>(), s.limitedApps)
    }

    @Test
    fun `stores profile and time settings`() = runTest {
        repo.setGender(ChildGender.GIRL)
        repo.setAgeGroup(AgeGroup.AGES_3_6)
        repo.setBudgetMinutes(45)
        repo.setLockPeriodHours(8)
        assertEquals(
            ParentSettings(ChildGender.GIRL, AgeGroup.AGES_3_6, 45, 8, emptySet()),
            repo.settings.first(),
        )
    }

    @Test
    fun `toggles limited apps`() = runTest {
        repo.setAppLimited("com.google.android.youtube", true)
        repo.setAppLimited("com.zhiliaoapp.musically", true)
        repo.setAppLimited("com.google.android.youtube", false)
        assertEquals(setOf("com.zhiliaoapp.musically"), repo.settings.first().limitedApps)
    }

    @Test
    fun `time values are clamped and snapped`() {
        assertEquals(5, TimeLimits.budget(0))
        assertEquals(240, TimeLimits.budget(1000))
        assertEquals(45, TimeLimits.budget(44))
        assertEquals(40, TimeLimits.budget(41))
        assertEquals(1, TimeLimits.lockPeriod(0))
        assertEquals(24, TimeLimits.lockPeriod(30))
    }
}
