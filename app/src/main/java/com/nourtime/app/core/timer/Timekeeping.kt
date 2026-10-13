package com.nourtime.app.core.timer

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Qualifier

/**
 * The timer's and the trusted clock's own settings file ("nour_timer"). They change while the child
 * uses the phone; kept apart from the settings file with the PIN, that file (and its backup copy) is
 * only written when a setting really changes.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TimekeepingStore

/** When the timer state is written: on every change that matters, and while time is counted. */
object TimerSaves {
    /** At most this much use is lost if the process dies (it favours the child, never the other way). */
    const val WHILE_COUNTING_EVERY_MS = 15_000L

    /**
     * Outside counting nothing needs saving as time passes: after a restart, [TimeRules.advance] measures
     * from the saved elapsed time, and the budget, lock length and resets follow from the settings.
     */
    fun needed(first: Boolean, previous: TimerState, next: TimerState, wasInUse: Boolean, inUse: Boolean, sinceSaveMs: Long): Boolean =
        first || next.phase != previous.phase || inUse != wasInUse || next.lastResetDay != previous.lastResetDay ||
            (inUse && sinceSaveMs >= WHILE_COUNTING_EVERY_MS)
}

/**
 * Moves the timer and trusted-clock keys out of the main settings file, once (installs from before
 * the timekeeping file). Nothing is moved when the timekeeping file already has a state.
 */
class TimekeepingMigration(private val main: DataStore<Preferences>) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().isEmpty() && legacyKeys(main.data.first()).isNotEmpty()

    override suspend fun migrate(currentData: Preferences): Preferences {
        val old = main.data.first()
        val moved = currentData.toMutablePreferences()
        legacyKeys(old).forEach { key ->
            @Suppress("UNCHECKED_CAST")
            moved[key as Preferences.Key<Any>] = old[key]!!
        }
        return moved.toPreferences()
    }

    override suspend fun cleanUp() {
        main.edit { prefs -> legacyKeys(prefs).forEach { prefs.remove(it) } }
    }

    private fun legacyKeys(prefs: Preferences): List<Preferences.Key<*>> =
        prefs.asMap().keys.filter { key -> PREFIXES.any { key.name.startsWith(it) } }

    companion object {
        private val PREFIXES = listOf("timer_", "trusted_")

        /** A damaged timekeeping file starts over (a fresh budget) rather than blocking the app. */
        fun onCorruption(): Preferences = emptyPreferences()
    }
}
