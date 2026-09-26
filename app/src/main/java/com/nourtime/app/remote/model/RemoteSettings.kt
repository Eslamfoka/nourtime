package com.nourtime.app.remote.model

import com.nourtime.app.data.settings.Bedtime
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.data.settings.ParentSettings
import com.nourtime.app.data.settings.TimeLimits

/**
 * The settings both phones can edit, as stored in `devices/{id}.settings` (Phase 2). The child's
 * gender, age group, sound and settings protection stay on the child's phone only.
 */
data class RemoteSettings(
    val budgetMinutes: Int,
    val lockPeriodHours: Int,
    val lockType: LockType,
    val limitedApps: Set<String>,
    val allowedDuringLock: Set<String>,
    val bedtime: Bedtime,
    val dailyResetMinute: Int?,
) {
    fun toMap(rev: Long, by: String): Map<String, Any?> = mapOf(
        "budgetMinutes" to budgetMinutes,
        "lockPeriodHours" to lockPeriodHours,
        "lockType" to lockType.name,
        "limitedApps" to limitedApps.sorted(),
        "allowedDuringLock" to allowedDuringLock.sorted(),
        "bedtimeEnabled" to bedtime.enabled,
        "bedtimeStart" to bedtime.startMinute,
        "bedtimeEnd" to bedtime.endMinute,
        "dailyResetMinute" to dailyResetMinute,
        "rev" to rev,
        "by" to by,
    )

    /** [base] with these values; the child-only settings are kept. */
    fun applyTo(base: ParentSettings): ParentSettings = base.copy(
        budgetMinutes = budgetMinutes,
        lockPeriodHours = lockPeriodHours,
        lockType = lockType,
        limitedApps = limitedApps,
        allowedDuringLock = allowedDuringLock,
        bedtime = bedtime,
        dailyResetMinute = dailyResetMinute,
    )

    companion object {
        const val BY_CHILD = "child"
        const val BY_PARENT = "parent"
        private const val MINUTES_PER_DAY = 24 * 60

        fun of(s: ParentSettings) = RemoteSettings(
            budgetMinutes = s.budgetMinutes,
            lockPeriodHours = s.lockPeriodHours,
            lockType = s.lockType,
            limitedApps = s.limitedApps,
            allowedDuringLock = s.allowedDuringLock,
            bedtime = s.bedtime,
            dailyResetMinute = s.dailyResetMinute,
        )

        /** Parses and clamps a Firestore map; null when a required value is missing or unknown. */
        fun fromMap(m: Map<String, Any?>?): RemoteSettings? {
            if (m == null) return null
            val budget = m.int("budgetMinutes") ?: return null
            val lock = m.int("lockPeriodHours") ?: return null
            val lockType = (m["lockType"] as? String)?.let { name -> LockType.entries.firstOrNull { it.name == name } } ?: return null
            val limited = m.strings("limitedApps")
            val defaults = Bedtime()
            return RemoteSettings(
                budgetMinutes = TimeLimits.budget(budget),
                lockPeriodHours = TimeLimits.lockPeriod(lock),
                lockType = lockType,
                limitedApps = limited,
                allowedDuringLock = m.strings("allowedDuringLock") - limited,
                bedtime = Bedtime(
                    enabled = m["bedtimeEnabled"] as? Boolean ?: false,
                    startMinute = m.int("bedtimeStart")?.takeIf { it in 0 until MINUTES_PER_DAY } ?: defaults.startMinute,
                    endMinute = m.int("bedtimeEnd")?.takeIf { it in 0 until MINUTES_PER_DAY } ?: defaults.endMinute,
                ),
                dailyResetMinute = m.int("dailyResetMinute")?.takeIf { it in 0 until MINUTES_PER_DAY },
            )
        }

        private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

        private fun Map<String, Any?>.strings(key: String): Set<String> =
            (this[key] as? List<*>).orEmpty().filterIsInstance<String>().toSet()
    }
}

enum class SyncAction { UPLOAD, APPLY_REMOTE, NOTHING }

/**
 * Two-way settings sync between the child's phone and Firestore. Every write carries a revision
 * and who wrote it. A parent revision newer than the last one this phone synced wins, even over an
 * offline change here; the child's own writes coming back are never re-applied.
 */
object SettingsSync {

    fun decide(
        local: RemoteSettings,
        lastSynced: RemoteSettings?,
        lastSyncedRev: Long,
        remote: RemoteSettings?,
        remoteRev: Long,
        remoteBy: String?,
    ): SyncAction = when {
        remote == null -> SyncAction.UPLOAD
        remoteRev > lastSyncedRev && remoteBy == RemoteSettings.BY_PARENT -> SyncAction.APPLY_REMOTE
        local != lastSynced -> SyncAction.UPLOAD
        else -> SyncAction.NOTHING
    }

    fun nextRev(lastSyncedRev: Long, remoteRev: Long): Long = maxOf(lastSyncedRev, remoteRev) + 1
}
