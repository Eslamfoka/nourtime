package com.nourtime.app.core.learning

import com.nourtime.app.data.settings.AgeGroup

/** The parent's Learning Hub settings. [dailyMaxMinutes] 0 = learning without rewards. */
data class LearningSettings(
    val enabled: Boolean = true,
    val minutesPerLevel: Int = DEFAULT_MINUTES_PER_LEVEL,
    val dailyMaxMinutes: Int = DEFAULT_DAILY_MAX,
) {
    companion object {
        const val DEFAULT_MINUTES_PER_LEVEL = 5
        const val DEFAULT_DAILY_MAX = 15
        val MINUTES_PER_LEVEL_OPTIONS = listOf(2, 5, 10)
        val DAILY_MAX_OPTIONS = listOf(0, 10, 15, 30, 60)
    }
}

object RewardPolicy {
    /** Minutes a finished level earns, given what was already earned today. */
    fun earn(settings: LearningSettings, stars: Int, earnedToday: Int): Int {
        if (!settings.enabled || stars < Stars.FOR_REWARD) return 0
        return minOf(settings.minutesPerLevel, (settings.dailyMaxMinutes - earnedToday).coerceAtLeast(0))
    }
}

/**
 * A game's progress: the best stars per level **id**. Ids (not positions) keep progress right when new
 * levels are added to a content pack, anywhere in its list.
 */
data class LevelProgress(val stars: Map<String, Int> = emptyMap()) {

    fun finished(levelId: String, earned: Int): LevelProgress =
        LevelProgress(stars + (levelId to maxOf(stars[levelId] ?: 0, earned.coerceIn(0, 3))))

    fun starsOf(levelId: String): Int = stars[levelId] ?: 0

    /**
     * Levels up to the start level are open; after that, each level opens when the one before is done.
     * A level the child has finished stays open, even if a new level is later added before it.
     */
    fun playable(pack: GamePack<*>, index: Int, age: AgeGroup?): Boolean {
        if (index !in pack.levels.indices) return false
        return index <= pack.startIndex(age) || pack.levels[index].id in stars || pack.levels[index - 1].id in stars
    }

    /** The furthest playable level: where the child is now. */
    fun current(pack: GamePack<*>, age: AgeGroup?): Int =
        pack.levels.indices.lastOrNull { playable(pack, it, age) } ?: 0

    fun encode(): String = stars.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        fun decode(text: String?): LevelProgress = LevelProgress(
            text.orEmpty().split(',').mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size != 2 || !LevelIds.valid(parts[0])) return@mapNotNull null
                val s = parts[1].toIntOrNull()?.coerceIn(0, 3) ?: return@mapNotNull null
                parts[0] to s
            }.toMap(),
        )
    }
}
