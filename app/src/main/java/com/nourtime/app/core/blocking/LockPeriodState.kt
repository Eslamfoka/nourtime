package com.nourtime.app.core.blocking

import com.nourtime.app.core.time.TrustedClock
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.data.settings.DayRules
import com.nourtime.app.data.settings.ParentSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a lock period (time up or bedtime) is running right now. Read straight from the persisted
 * timer state and the bedtime setting, so it is right even the instant the app process starts.
 */
@Singleton
class LockPeriodState @Inject constructor(
    private val engine: TimeEngine,
    private val settings: ParentSettingsRepository,
    private val trustedClock: TrustedClock,
) {
    suspend fun isActive(): Boolean =
        engine.isLocked() || DayRules.activeBedtime(settings.settings.first(), trustedClock.now().toLocalDateTime()) != null
}
