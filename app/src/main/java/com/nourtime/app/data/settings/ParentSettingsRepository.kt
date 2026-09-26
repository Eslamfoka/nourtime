package com.nourtime.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Used to address the child correctly in Arabic (masculine/feminine messages). */
enum class ChildGender { BOY, GIRL }

/** Drives the "Time's up" template (brief §4). */
enum class AgeGroup { AGES_3_6, AGES_7_9, AGES_10_12 }

/** What gets locked when time is up (brief §3). */
enum class LockType { SELECTED_APPS, WHOLE_DEVICE }

/** Optional bedtime (brief §5): minutes of the day; may cross midnight. */
data class Bedtime(val enabled: Boolean = false, val startMinute: Int = 21 * 60, val endMinute: Int = 7 * 60)

data class ParentSettings(
    val gender: ChildGender? = null,
    val ageGroup: AgeGroup? = null,
    val budgetMinutes: Int = TimeLimits.DEFAULT_BUDGET_MINUTES,
    val lockPeriodHours: Int = TimeLimits.DEFAULT_LOCK_HOURS,
    /** Package names of the apps that share the time budget. */
    val limitedApps: Set<String> = emptySet(),
    /** Usable during lock periods and bedtime, and never counted (Phase 1.5). Never also limited. */
    val allowedDuringLock: Set<String> = emptySet(),
    /** Minute of the day for the optional daily refill, or null when off. */
    val dailyResetMinute: Int? = null,
    val lockType: LockType = LockType.SELECTED_APPS,
    /** Block the phone's Settings and uninstall screens for the child (brief §3). */
    val protectSystemSettings: Boolean = true,
    /** Sound on the child's "Time's up" screen (brief §12). */
    val soundEnabled: Boolean = true,
    val bedtime: Bedtime = Bedtime(),
)

object TimeLimits {
    const val MIN_BUDGET_MINUTES = 5
    const val MAX_BUDGET_MINUTES = 240
    const val BUDGET_STEP_MINUTES = 5
    const val DEFAULT_BUDGET_MINUTES = 60

    const val MIN_LOCK_HOURS = 1
    const val MAX_LOCK_HOURS = 24
    const val DEFAULT_LOCK_HOURS = 6

    /** Clamps to the allowed range and snaps to 5-minute steps. */
    fun budget(minutes: Int): Int {
        val clamped = minutes.coerceIn(MIN_BUDGET_MINUTES, MAX_BUDGET_MINUTES)
        val snapped = ((clamped + BUDGET_STEP_MINUTES / 2) / BUDGET_STEP_MINUTES) * BUDGET_STEP_MINUTES
        return snapped.coerceIn(MIN_BUDGET_MINUTES, MAX_BUDGET_MINUTES)
    }

    fun lockPeriod(hours: Int): Int = hours.coerceIn(MIN_LOCK_HOURS, MAX_LOCK_HOURS)
}

@Singleton
class ParentSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<ParentSettings> = store.data.map { prefs ->
        ParentSettings(
            gender = prefs[GENDER]?.let { name -> ChildGender.entries.firstOrNull { it.name == name } },
            ageGroup = prefs[AGE_GROUP]?.let { name -> AgeGroup.entries.firstOrNull { it.name == name } },
            budgetMinutes = prefs[BUDGET]?.let(TimeLimits::budget) ?: TimeLimits.DEFAULT_BUDGET_MINUTES,
            lockPeriodHours = prefs[LOCK_HOURS]?.let(TimeLimits::lockPeriod) ?: TimeLimits.DEFAULT_LOCK_HOURS,
            limitedApps = prefs[LIMITED_APPS].orEmpty(),
            allowedDuringLock = prefs[ALLOWED_DURING_LOCK].orEmpty(),
            dailyResetMinute = prefs[DAILY_RESET]?.takeIf { it in 0 until MINUTES_PER_DAY },
            lockType = prefs[LOCK_TYPE]?.let { name -> LockType.entries.firstOrNull { it.name == name } } ?: LockType.SELECTED_APPS,
            protectSystemSettings = prefs[PROTECT_SETTINGS] ?: true,
            soundEnabled = prefs[SOUND] ?: true,
            bedtime = Bedtime(
                enabled = prefs[BEDTIME_ON] ?: false,
                startMinute = prefs[BEDTIME_START]?.takeIf { it in 0 until MINUTES_PER_DAY } ?: Bedtime().startMinute,
                endMinute = prefs[BEDTIME_END]?.takeIf { it in 0 until MINUTES_PER_DAY } ?: Bedtime().endMinute,
            ),
        )
    }.distinctUntilChanged()

    suspend fun setGender(gender: ChildGender) = store.edit { it[GENDER] = gender.name }

    suspend fun setAgeGroup(ageGroup: AgeGroup) = store.edit { it[AGE_GROUP] = ageGroup.name }

    suspend fun setBudgetMinutes(minutes: Int) = store.edit { it[BUDGET] = TimeLimits.budget(minutes) }

    suspend fun setLockPeriodHours(hours: Int) = store.edit { it[LOCK_HOURS] = TimeLimits.lockPeriod(hours) }

    suspend fun setDailyResetMinute(minute: Int?) = store.edit {
        if (minute == null) it.remove(DAILY_RESET) else it[DAILY_RESET] = minute.coerceIn(0, MINUTES_PER_DAY - 1)
    }

    suspend fun setLockType(type: LockType) = store.edit { it[LOCK_TYPE] = type.name }

    suspend fun setProtectSystemSettings(on: Boolean) = store.edit { it[PROTECT_SETTINGS] = on }

    suspend fun setSoundEnabled(on: Boolean) = store.edit { it[SOUND] = on }

    suspend fun setBedtime(bedtime: Bedtime) = store.edit {
        it[BEDTIME_ON] = bedtime.enabled
        it[BEDTIME_START] = bedtime.startMinute.coerceIn(0, MINUTES_PER_DAY - 1)
        it[BEDTIME_END] = bedtime.endMinute.coerceIn(0, MINUTES_PER_DAY - 1)
    }

    /** Limiting an app takes it off the allowed-during-lock list. */
    suspend fun setAppLimited(packageName: String, limited: Boolean) = store.edit { prefs ->
        val current = prefs[LIMITED_APPS].orEmpty()
        prefs[LIMITED_APPS] = if (limited) current + packageName else current - packageName
        if (limited) prefs[ALLOWED_DURING_LOCK] = prefs[ALLOWED_DURING_LOCK].orEmpty() - packageName
    }

    /** Allowing an app during the lock takes it off the limited list, so its time is free. */
    suspend fun setAppAllowedDuringLock(packageName: String, allowed: Boolean) = store.edit { prefs ->
        val current = prefs[ALLOWED_DURING_LOCK].orEmpty()
        prefs[ALLOWED_DURING_LOCK] = if (allowed) current + packageName else current - packageName
        if (allowed) prefs[LIMITED_APPS] = prefs[LIMITED_APPS].orEmpty() - packageName
    }

    private companion object {
        val GENDER = stringPreferencesKey("child_gender")
        val AGE_GROUP = stringPreferencesKey("child_age_group")
        val BUDGET = intPreferencesKey("budget_minutes")
        val LOCK_HOURS = intPreferencesKey("lock_period_hours")
        val LIMITED_APPS = stringSetPreferencesKey("limited_apps")
        val ALLOWED_DURING_LOCK = stringSetPreferencesKey("allowed_during_lock")
        val DAILY_RESET = intPreferencesKey("daily_reset_minute")
        val LOCK_TYPE = stringPreferencesKey("lock_type")
        val PROTECT_SETTINGS = booleanPreferencesKey("protect_system_settings")
        val SOUND = booleanPreferencesKey("child_sound")
        val BEDTIME_ON = booleanPreferencesKey("bedtime_on")
        val BEDTIME_START = intPreferencesKey("bedtime_start")
        val BEDTIME_END = intPreferencesKey("bedtime_end")
        const val MINUTES_PER_DAY = 24 * 60
    }
}
