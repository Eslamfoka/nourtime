package com.nourtime.app.core.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.TrustedClock
import com.nourtime.app.data.settings.DayRules
import com.nourtime.app.data.settings.ParentSettings
import com.nourtime.app.data.usage.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

data class TimerStatus(
    val phase: TimerPhase,
    val remainingMs: Long,
    val budgetMs: Long,
    val lockRemainingMs: Long,
    /** Limited apps on screen right now (even while locked, until the lock screen exists). */
    val appsInUse: Set<String>,
    val source: DetectionSource,
) {
    /** Accessibility is off or nothing can see the foreground app. */
    val protectionDegraded: Boolean get() = source != DetectionSource.ACCESSIBILITY

    /** Fail-closed: limited apps are blocked while locked and whenever detection is degraded. */
    val blockLimitedApps: Boolean get() = phase == TimerPhase.LOCKED || protectionDegraded

    val counting: Boolean get() = phase == TimerPhase.AVAILABLE && appsInUse.isNotEmpty()
}

/**
 * Owns the persisted [TimerState]: advances it with [TimeRules] on every detection change and
 * every tick from the timer service, and publishes a [TimerStatus] for the UI and notifications.
 */
@Singleton
class TimeEngine @Inject constructor(
    private val store: DataStore<Preferences>,
    private val clock: DeviceClock,
    private val trustedClock: TrustedClock,
    private val usage: UsageRepository,
) {
    private val mutex = Mutex()
    private var state: TimerState? = null
    private var lastInUse = false
    private var lastAppsInUse: Set<String> = emptySet()
    private var lastSavedElapsed = 0L
    /** Charged time not yet written to the stats database, per app. */
    private val pendingUsage = mutableMapOf<String, Long>()

    private val _status = MutableStateFlow<TimerStatus?>(null)
    val status: StateFlow<TimerStatus?> = _status.asStateFlow()

    /**
     * [appsInUse] are the limited apps on screen from now on; the interval since the previous call is
     * charged with the previous value.
     */
    suspend fun update(appsInUse: Set<String>, source: DetectionSource, settings: ParentSettings) = mutex.withLock {
        val now = clock.elapsedRealtime()
        val boot = clock.bootCount()
        val wall = trustedClock.now()
        // Weekend days can have their own budget and lock length (Phase 4a); a change at midnight moves
        // the time left by the difference, like any budget edit.
        val limits = DayRules.limitsOn(settings, wall.toLocalDate())
        val budgetMs = limits.budgetMinutes * MINUTE
        val lockMs = limits.lockPeriodHours * HOUR
        val previous = state ?: load() ?: TimerState.fresh(budgetMs, lockMs, now, boot)

        val resetAt = settings.dailyResetMinute?.let { LocalTime.of(it / 60, it % 60) }
        var next = TimeRules.advance(previous, now, boot, lastInUse)
        chargeUsage(previous, next)
        next = TimeRules.applySettings(next, budgetMs, lockMs)
        next = TimeRules.applyDailyReset(next, wall.toLocalDate(), wall.toLocalTime(), resetAt)

        val inUse = appsInUse.isNotEmpty() && next.phase == TimerPhase.AVAILABLE
        val mustSave = state == null || next.phase != previous.phase || inUse != lastInUse ||
            next.lastResetDay != previous.lastResetDay || now - lastSavedElapsed >= SAVE_EVERY_MS
        state = next
        lastInUse = inUse
        lastAppsInUse = appsInUse
        if (mustSave) {
            save(next)
            flushUsage(wall.toLocalDate())
            lastSavedElapsed = now
        }
        _status.value = TimerStatus(next.phase, next.remainingMs, next.budgetMs, next.lockRemainingMs, appsInUse, source)
    }

    /** True while the lock period runs; falls back to the saved state before the first update. */
    suspend fun isLocked(): Boolean = mutex.withLock { (state ?: load())?.phase == TimerPhase.LOCKED }

    /** Applies a command from the parent's phone (or the debug tools on Home) and saves it at once. */
    suspend fun apply(command: TimerCommand) = mutex.withLock {
        val current = state ?: load() ?: return@withLock
        val next = TimeRules.apply(current, command)
        state = next
        save(next)
        lastSavedElapsed = clock.elapsedRealtime()
        _status.value = _status.value?.copy(
            phase = next.phase,
            remainingMs = next.remainingMs,
            budgetMs = next.budgetMs,
            lockRemainingMs = next.lockRemainingMs,
        )
    }

    /** Persists the latest state, e.g. when the service stops. */
    suspend fun flush() = mutex.withLock {
        state?.let { save(it) }
        flushUsage(trustedClock.now().toLocalDate())
    }

    /** Attributes budget consumed during the last interval to the foreground-most limited app. */
    private fun chargeUsage(before: TimerState, after: TimerState) {
        if (!lastInUse || before.phase != TimerPhase.AVAILABLE) return
        val used = before.remainingMs - if (after.phase == TimerPhase.AVAILABLE) after.remainingMs else 0
        val app = lastAppsInUse.firstOrNull() ?: return
        if (used > 0) pendingUsage[app] = (pendingUsage[app] ?: 0) + used
    }

    private suspend fun flushUsage(day: LocalDate) {
        if (pendingUsage.isEmpty()) return
        val batch = pendingUsage.toMap()
        pendingUsage.clear()
        batch.forEach { (pkg, ms) -> usage.add(day, pkg, ms) }
    }

    private suspend fun load(): TimerState? {
        val p = store.data.first()
        val phase = p[PHASE]?.let { name -> TimerPhase.entries.firstOrNull { it.name == name } } ?: return null
        return TimerState(
            phase = phase,
            remainingMs = p[REMAINING] ?: 0,
            lockRemainingMs = p[LOCK_REMAINING] ?: 0,
            budgetMs = p[BUDGET] ?: 0,
            lockMs = p[LOCK] ?: 0,
            lastElapsed = p[LAST_ELAPSED] ?: 0,
            bootCount = p[BOOT] ?: 0,
            lastResetDay = p[LAST_RESET_DAY]?.let(LocalDate::ofEpochDay),
            sinceResetMs = p[SINCE_RESET] ?: 0,
        )
    }

    private suspend fun save(s: TimerState) {
        store.edit { it.write(s) }
    }

    private fun MutablePreferences.write(s: TimerState) {
        this[PHASE] = s.phase.name
        this[REMAINING] = s.remainingMs
        this[LOCK_REMAINING] = s.lockRemainingMs
        this[BUDGET] = s.budgetMs
        this[LOCK] = s.lockMs
        this[LAST_ELAPSED] = s.lastElapsed
        this[BOOT] = s.bootCount
        if (s.lastResetDay == null) remove(LAST_RESET_DAY) else this[LAST_RESET_DAY] = s.lastResetDay.toEpochDay()
        this[SINCE_RESET] = s.sinceResetMs
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        /** At most this much use can be lost if the process dies. */
        const val SAVE_EVERY_MS = 5_000L

        val PHASE = stringPreferencesKey("timer_phase")
        val REMAINING = longPreferencesKey("timer_remaining")
        val LOCK_REMAINING = longPreferencesKey("timer_lock_remaining")
        val BUDGET = longPreferencesKey("timer_budget")
        val LOCK = longPreferencesKey("timer_lock")
        val LAST_ELAPSED = longPreferencesKey("timer_last_elapsed")
        val BOOT = intPreferencesKey("timer_boot")
        val LAST_RESET_DAY = longPreferencesKey("timer_last_reset_day")
        val SINCE_RESET = longPreferencesKey("timer_since_reset")
    }
}
