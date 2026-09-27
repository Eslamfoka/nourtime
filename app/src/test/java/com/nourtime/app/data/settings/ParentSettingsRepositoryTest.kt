package com.nourtime.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.nourtime.app.remote.model.RemoteSettings
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
            repo.settings.first().copy(weekend = WeekendRules()),
        )
    }

    @Test
    fun `weekend values never set start as a copy of the normal ones`() = runTest {
        repo.setBudgetMinutes(45)
        repo.setLockPeriodHours(8)
        repo.setBedtime(Bedtime(enabled = true, startMinute = 20 * 60, endMinute = 6 * 60))
        val weekend = repo.settings.first().weekend
        assertEquals(false, weekend.enabled)
        assertEquals(45, weekend.budgetMinutes)
        assertEquals(8, weekend.lockPeriodHours)
        assertEquals(Bedtime(enabled = true, startMinute = 20 * 60, endMinute = 6 * 60), weekend.bedtime)
    }

    @Test
    fun `stores the weekend rules`() = runTest {
        val weekend = WeekendRules(
            enabled = true,
            days = setOf(java.time.DayOfWeek.FRIDAY),
            budgetMinutes = 120,
            lockPeriodHours = 3,
            bedtime = Bedtime(enabled = true, startMinute = 23 * 60, endMinute = 9 * 60),
        )
        repo.setWeekend(weekend)
        assertEquals(weekend, repo.settings.first().weekend)
    }

    @Test
    fun `an app is either limited or allowed during the lock, never both`() = runTest {
        repo.setAppLimited("com.google.android.youtube", true)
        repo.setAppAllowedDuringLock("com.google.android.youtube", true)
        var s = repo.settings.first()
        assertEquals(emptySet<String>(), s.limitedApps)
        assertEquals(setOf("com.google.android.youtube"), s.allowedDuringLock)

        repo.setAppLimited("com.google.android.youtube", true)
        s = repo.settings.first()
        assertEquals(setOf("com.google.android.youtube"), s.limitedApps)
        assertEquals(emptySet<String>(), s.allowedDuringLock)
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

    @Test
    fun `settings from the parent's phone keep the child-only ones`() = runTest {
        repo.setSoundEnabled(false)
        repo.setAgeGroup(AgeGroup.AGES_7_9)
        val remote = RemoteSettings.of(ParentSettings(budgetMinutes = 90, limitedApps = setOf("a"), allowedDuringLock = setOf("b")))
        repo.replaceWith(remote)
        val s = repo.settings.first()
        assertEquals(90, s.budgetMinutes)
        assertEquals(setOf("a"), s.limitedApps)
        assertEquals(setOf("b"), s.allowedDuringLock)
        assertEquals(false, s.soundEnabled)
        assertEquals(AgeGroup.AGES_7_9, s.ageGroup)
    }
}
