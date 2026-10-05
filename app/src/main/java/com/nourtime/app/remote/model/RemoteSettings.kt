package com.nourtime.app.remote.model

import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LearningSettings
import com.nourtime.app.core.learning.VoicePack
import com.nourtime.app.data.settings.Bedtime
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.data.settings.ParentSettings
import com.nourtime.app.data.settings.TimeLimits
import com.nourtime.app.data.settings.WeekendRules
import java.time.DayOfWeek
import java.util.Locale

/**
 * The settings both phones can edit, as stored in `devices/{id}.settings` (Phase 2), including the
 * Learning Hub's limits and the voice per language. The child's gender, age group, sound and
 * settings protection stay on the child's phone only.
 */
data class RemoteSettings(
    val budgetMinutes: Int,
    val lockPeriodHours: Int,
    val lockType: LockType,
    val limitedApps: Set<String>,
    val allowedDuringLock: Set<String>,
    val bedtime: Bedtime,
    val dailyResetMinute: Int?,
    val weekend: WeekendRules = WeekendRules(),
    val learning: LearningSettings = LearningSettings(),
    /** Every language's voice (defaults filled in), so both phones compare equal. */
    val voices: Map<LearnLanguage, VoicePack> = completeVoices(emptyMap()),
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
        "weekend" to mapOf(
            "enabled" to weekend.enabled,
            "days" to weekend.days.map { it.name }.sorted(),
            "budgetMinutes" to weekend.budgetMinutes,
            "lockPeriodHours" to weekend.lockPeriodHours,
            "bedtimeEnabled" to weekend.bedtime.enabled,
            "bedtimeStart" to weekend.bedtime.startMinute,
            "bedtimeEnd" to weekend.bedtime.endMinute,
        ),
        "learning" to mapOf(
            "enabled" to learning.enabled,
            "minutesPerLevel" to learning.minutesPerLevel,
            "dailyMaxMinutes" to learning.dailyMaxMinutes,
            "voices" to voices.entries.associate { (lang, pack) -> lang.tag to pack.name },
        ),
        "rev" to rev,
        "by" to by,
    )

    /** A compact string for the local sync state (package names never contain the separators). */
    fun encode(): String = listOf(
        budgetMinutes.toString(),
        lockPeriodHours.toString(),
        lockType.name,
        limitedApps.sorted().joinToString(LIST_SEP),
        allowedDuringLock.sorted().joinToString(LIST_SEP),
        bedtime.enabled.toString(),
        bedtime.startMinute.toString(),
        bedtime.endMinute.toString(),
        dailyResetMinute?.toString().orEmpty(),
        weekend.enabled.toString(),
        weekend.days.map { it.name }.sorted().joinToString(LIST_SEP),
        weekend.budgetMinutes.toString(),
        weekend.lockPeriodHours.toString(),
        weekend.bedtime.enabled.toString(),
        weekend.bedtime.startMinute.toString(),
        weekend.bedtime.endMinute.toString(),
        learning.enabled.toString(),
        learning.minutesPerLevel.toString(),
        learning.dailyMaxMinutes.toString(),
        voices.entries.sortedBy { it.key.name }.joinToString(LIST_SEP) { (lang, pack) -> "${lang.tag}=${pack.name}" },
    ).joinToString(FIELD_SEP)

    /** Limits or frees an app; limiting takes it off the allowed list (never both). */
    fun withLimited(packageName: String, limited: Boolean): RemoteSettings =
        if (limited) copy(limitedApps = limitedApps + packageName, allowedDuringLock = allowedDuringLock - packageName)
        else copy(limitedApps = limitedApps - packageName)

    /** Allows an app during the lock, or not; allowing takes it off the limited list. */
    fun withAllowed(packageName: String, allowed: Boolean): RemoteSettings =
        if (allowed) copy(allowedDuringLock = allowedDuringLock + packageName, limitedApps = limitedApps - packageName)
        else copy(allowedDuringLock = allowedDuringLock - packageName)

    /** [base] with these values; the child-only settings are kept. */
    fun applyTo(base: ParentSettings): ParentSettings = base.copy(
        budgetMinutes = budgetMinutes,
        lockPeriodHours = lockPeriodHours,
        lockType = lockType,
        limitedApps = limitedApps,
        allowedDuringLock = allowedDuringLock,
        bedtime = bedtime,
        dailyResetMinute = dailyResetMinute,
        weekend = weekend,
    )

    companion object {
        internal const val FIELD_SEP = ""
        private const val LIST_SEP = ","
        /** Before Phase 4a the snapshot had no weekend fields; it still decodes (weekend off). */
        private const val FIELDS_BEFORE_WEEKEND = 9
        /** Before the Learning Hub was synced (2026-10-05): its settings read as the defaults. */
        private const val FIELDS_BEFORE_LEARNING = 16
        private const val FIELDS = 20

        fun decode(text: String?): RemoteSettings? {
            val f = text?.split(FIELD_SEP)?.takeIf { it.size in setOf(FIELDS, FIELDS_BEFORE_LEARNING, FIELDS_BEFORE_WEEKEND) } ?: return null
            fun set(s: String) = if (s.isEmpty()) emptySet() else s.split(LIST_SEP).toSet()
            return runCatching {
                RemoteSettings(
                    budgetMinutes = f[0].toInt(),
                    lockPeriodHours = f[1].toInt(),
                    lockType = LockType.valueOf(f[2]),
                    limitedApps = set(f[3]),
                    allowedDuringLock = set(f[4]),
                    bedtime = Bedtime(f[5].toBooleanStrict(), f[6].toInt(), f[7].toInt()),
                    dailyResetMinute = f[8].ifEmpty { null }?.toInt(),
                    weekend = if (f.size == FIELDS_BEFORE_WEEKEND) {
                        WeekendRules()
                    } else {
                        WeekendRules(
                            enabled = f[9].toBooleanStrict(),
                            days = set(f[10]).map { DayOfWeek.valueOf(it) }.toSet(),
                            budgetMinutes = f[11].toInt(),
                            lockPeriodHours = f[12].toInt(),
                            bedtime = Bedtime(f[13].toBooleanStrict(), f[14].toInt(), f[15].toInt()),
                        )
                    },
                    learning = if (f.size == FIELDS) {
                        learningOf(f[16].toBooleanStrict(), f[17].toInt(), f[18].toInt())
                    } else {
                        LearningSettings()
                    },
                    voices = completeVoices(
                        if (f.size == FIELDS) voicesOf(set(f[19]).associate { it.substringBefore('=') to it.substringAfter('=') }) else emptyMap(),
                    ),
                )
            }.getOrNull()
        }

        const val BY_CHILD = "child"
        const val BY_PARENT = "parent"
        private const val MINUTES_PER_DAY = 24 * 60

        fun of(
            s: ParentSettings,
            learning: LearningSettings = LearningSettings(),
            voices: Map<LearnLanguage, VoicePack> = emptyMap(),
        ) = RemoteSettings(
            budgetMinutes = s.budgetMinutes,
            lockPeriodHours = s.lockPeriodHours,
            lockType = s.lockType,
            limitedApps = s.limitedApps,
            allowedDuringLock = s.allowedDuringLock,
            bedtime = s.bedtime,
            dailyResetMinute = s.dailyResetMinute,
            weekend = s.weekend,
            learning = learningOf(learning.enabled, learning.minutesPerLevel, learning.dailyMaxMinutes),
            voices = completeVoices(voices),
        )

        fun completeVoices(chosen: Map<LearnLanguage, VoicePack>): Map<LearnLanguage, VoicePack> =
            LearnLanguage.entries.associateWith { VoicePack.chosen(chosen, it) }

        /** Clamped like the local editors. */
        private fun learningOf(enabled: Boolean, perLevel: Int, dailyMax: Int) = LearningSettings(
            enabled = enabled,
            minutesPerLevel = perLevel.coerceIn(LearningSettings.MINUTES_PER_LEVEL_RANGE),
            dailyMaxMinutes = dailyMax.coerceIn(LearningSettings.DAILY_MAX_RANGE),
        )

        /** Language tag to pack name; unknown names, or a pack of another language, are dropped. */
        private fun voicesOf(m: Map<*, *>): Map<LearnLanguage, VoicePack> = m.entries.mapNotNull { (tag, name) ->
            val lang = LearnLanguage.entries.firstOrNull { it.tag == tag } ?: return@mapNotNull null
            VoicePack.entries.firstOrNull { it.name == name && it.language == lang }?.let { lang to it }
        }.toMap()

        /** Missing (written before the Learning Hub was synced) means the defaults. */
        private fun learningFrom(l: Map<*, *>?): LearningSettings {
            @Suppress("UNCHECKED_CAST")
            val m = (l as? Map<String, Any?>).orEmpty()
            val defaults = LearningSettings()
            return learningOf(
                enabled = m["enabled"] as? Boolean ?: defaults.enabled,
                perLevel = m.int("minutesPerLevel") ?: defaults.minutesPerLevel,
                dailyMax = m.int("dailyMaxMinutes") ?: defaults.dailyMaxMinutes,
            )
        }

        /** Parses and clamps a Firestore map; null when a required value is missing or unknown. */
        fun fromMap(m: Map<String, Any?>?): RemoteSettings? {
            if (m == null) return null
            val budget = m.int("budgetMinutes") ?: return null
            val lock = m.int("lockPeriodHours") ?: return null
            val lockType = (m["lockType"] as? String)?.let { name -> LockType.entries.firstOrNull { it.name == name } } ?: return null
            val limited = m.strings("limitedApps")
            val defaults = Bedtime()
            val bedtime = Bedtime(
                enabled = m["bedtimeEnabled"] as? Boolean ?: false,
                startMinute = m.int("bedtimeStart")?.takeIf { it in 0 until MINUTES_PER_DAY } ?: defaults.startMinute,
                endMinute = m.int("bedtimeEnd")?.takeIf { it in 0 until MINUTES_PER_DAY } ?: defaults.endMinute,
            )
            val clampedBudget = TimeLimits.budget(budget)
            val clampedLock = TimeLimits.lockPeriod(lock)
            return RemoteSettings(
                budgetMinutes = clampedBudget,
                lockPeriodHours = clampedLock,
                lockType = lockType,
                limitedApps = limited,
                allowedDuringLock = m.strings("allowedDuringLock") - limited,
                bedtime = bedtime,
                dailyResetMinute = m.int("dailyResetMinute")?.takeIf { it in 0 until MINUTES_PER_DAY },
                weekend = weekendFrom(m["weekend"] as? Map<*, *>, clampedBudget, clampedLock, bedtime),
                learning = learningFrom(m["learning"] as? Map<*, *>),
                voices = completeVoices(voicesOf(((m["learning"] as? Map<*, *>)?.get("voices") as? Map<*, *>).orEmpty())),
            )
        }

        /** Missing (written before Phase 4a) means off, with values copied from the normal ones. */
        private fun weekendFrom(w: Map<*, *>?, budget: Int, lockHours: Int, bedtime: Bedtime): WeekendRules {
            @Suppress("UNCHECKED_CAST")
            val m = (w as? Map<String, Any?>).orEmpty()
            val days = (m["days"] as? List<*>)?.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }?.toSet()
            return WeekendRules(
                enabled = m["enabled"] as? Boolean ?: false,
                days = days ?: WeekendRules.defaultDays(Locale.getDefault().language),
                budgetMinutes = m.int("budgetMinutes")?.let(TimeLimits::budget) ?: budget,
                lockPeriodHours = m.int("lockPeriodHours")?.let(TimeLimits::lockPeriod) ?: lockHours,
                bedtime = Bedtime(
                    enabled = m["bedtimeEnabled"] as? Boolean ?: bedtime.enabled,
                    startMinute = m.int("bedtimeStart")?.takeIf { it in 0 until MINUTES_PER_DAY } ?: bedtime.startMinute,
                    endMinute = m.int("bedtimeEnd")?.takeIf { it in 0 until MINUTES_PER_DAY } ?: bedtime.endMinute,
                ),
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
        remoteBy == RemoteSettings.BY_PARENT && remoteRev > lastSyncedRev -> SyncAction.APPLY_REMOTE
        // Same revision, different content: both phones wrote rev N+1 while one was offline.
        remoteBy == RemoteSettings.BY_PARENT && remoteRev == lastSyncedRev && remote != lastSynced -> SyncAction.APPLY_REMOTE
        local != lastSynced -> SyncAction.UPLOAD
        else -> SyncAction.NOTHING
    }

    /** Checked again inside the upload transaction, against the server's current revision. */
    fun uploadAllowed(lastSyncedRev: Long, remoteRev: Long, remoteBy: String?): Boolean =
        !(remoteBy == RemoteSettings.BY_PARENT && remoteRev > lastSyncedRev)

    fun nextRev(lastSyncedRev: Long, remoteRev: Long): Long = maxOf(lastSyncedRev, remoteRev) + 1
}
