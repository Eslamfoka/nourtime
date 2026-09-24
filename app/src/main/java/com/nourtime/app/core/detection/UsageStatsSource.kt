package com.nourtime.app.core.detection

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.nourtime.app.core.permissions.NourPermission
import com.nourtime.app.core.permissions.PermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Fallback foreground detection from usage events, used only while Accessibility is off. */
@Singleton
class UsageStatsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PermissionChecker,
) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    val available: Boolean get() = permissions.isGranted(NourPermission.USAGE_ACCESS)

    /** Last app that came to the foreground within [lookbackMs], or null if none did. */
    fun latestForeground(ignored: Set<String>, lookbackMs: Long = LOOKBACK_MS): String? {
        val end = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(end - lookbackMs, end) }.getOrNull() ?: return null
        val list = mutableListOf<Pair<String, Boolean>>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            list += event.packageName to (event.eventType == RESUMED)
        }
        return ForegroundRules.lastResumed(list, ignored)
    }

    private companion object {
        /** Long enough to find the current app when polling starts. */
        const val LOOKBACK_MS = 60 * 60_000L

        @Suppress("DEPRECATION")
        val RESUMED = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }
}
