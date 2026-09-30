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

/** A game's levels: [unlocked] is the highest playable level, [stars] the best result per level. */
data class LevelProgress(val unlocked: Int = 0, val stars: Map<Int, Int> = emptyMap()) {

    /** After finishing [level] with [earned] stars: best stars kept, the next level opens. */
    fun finished(level: Int, earned: Int, levelCount: Int): LevelProgress = LevelProgress(
        unlocked = maxOf(unlocked, minOf(level + 1, levelCount - 1)),
        stars = stars + (level to maxOf(stars[level] ?: 0, earned)),
    )

    fun encode(): String = "$unlocked|" + stars.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        fun decode(text: String?): LevelProgress? {
            if (text.isNullOrBlank()) return null
            val parts = text.split('|')
            val unlocked = parts[0].toIntOrNull() ?: return null
            val stars = parts.getOrNull(1).orEmpty().split(',').filter { it.isNotBlank() }.mapNotNull {
                val (k, v) = it.split(':').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
                val level = k.toIntOrNull() ?: return@mapNotNull null
                val s = v.toIntOrNull()?.coerceIn(0, 3) ?: return@mapNotNull null
                level to s
            }.toMap()
            return LevelProgress(unlocked.coerceAtLeast(0), stars)
        }

        /** Older children skip the easiest levels. */
        fun start(game: GameId, age: AgeGroup?): LevelProgress = LevelProgress(
            unlocked = when (game) {
                GameId.MATH -> when (age) {
                    AgeGroup.AGES_7_9 -> 2
                    AgeGroup.AGES_10_12 -> 4
                    else -> 0
                }
                GameId.LETTERS -> if (age == AgeGroup.AGES_10_12) 2 else 0
                else -> 0
            },
        )
    }
}
