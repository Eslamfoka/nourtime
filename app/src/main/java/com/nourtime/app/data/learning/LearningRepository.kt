package com.nourtime.app.data.learning

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nourtime.app.core.learning.GameId
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LearningSettings
import com.nourtime.app.core.learning.LevelProgress
import com.nourtime.app.core.learning.NumeralStyle
import com.nourtime.app.core.learning.RewardPolicy
import com.nourtime.app.data.settings.AgeGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What the Learning Hub shows: the child's progress and minutes, plus the parent's limits. */
data class LearningState(
    val settings: LearningSettings = LearningSettings(),
    val progress: Map<GameId, LevelProgress> = emptyMap(),
    val tutorialsSeen: Set<GameId> = emptySet(),
    /** Null until chosen: then it follows the language. */
    val numerals: NumeralStyle? = null,
    val lettersLanguage: LearnLanguage? = null,
    /** Earned minutes not used yet. */
    val bankMinutes: Int = 0,
    val earnedDay: LocalDate? = null,
    val earnedMinutes: Int = 0,
) {
    fun earnedOn(today: LocalDate): Int = if (earnedDay == today) earnedMinutes else 0
}

/** Result of a finished level. */
data class LevelOutcome(val earnedMinutes: Int, val progress: LevelProgress, val dailyMaxReached: Boolean)

/** Learning Hub state on this phone (DataStore). */
@Singleton
class LearningRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val state: Flow<LearningState> = store.data.map { it.toState() }.distinctUntilChanged()

    val settings: Flow<LearningSettings> = state.map { it.settings }.distinctUntilChanged()

    suspend fun setEnabled(on: Boolean) = store.edit { it[ENABLED] = on }

    suspend fun setMinutesPerLevel(minutes: Int) = store.edit { it[MINUTES_PER_LEVEL] = minutes.coerceIn(1, 60) }

    suspend fun setDailyMax(minutes: Int) = store.edit { it[DAILY_MAX] = minutes.coerceIn(0, 240) }

    suspend fun setNumerals(style: NumeralStyle) = store.edit { it[NUMERALS] = style.name }

    suspend fun setLettersLanguage(language: LearnLanguage) = store.edit { it[LETTERS_LANGUAGE] = language.name }

    suspend fun markTutorialSeen(game: GameId) = store.edit { it[TUTORIALS] = it[TUTORIALS].orEmpty() + game.name }

    /** Progress for [game], starting at a level that suits [age] the first time. */
    fun progressOf(state: LearningState, game: GameId, age: AgeGroup?): LevelProgress =
        state.progress[game] ?: LevelProgress.start(game, age)

    /**
     * Records a finished level and banks the minutes it earns (none in a parent's preview, [rewards]
     * false). Atomic, so two quick finishes can't both slip past the daily maximum.
     */
    suspend fun finishLevel(
        game: GameId,
        level: Int,
        stars: Int,
        levelCount: Int,
        age: AgeGroup?,
        today: LocalDate,
        rewards: Boolean,
    ): LevelOutcome {
        var outcome: LevelOutcome? = null
        store.edit { prefs ->
            val s = prefs.toState()
            val progress = progressOf(s, game, age).finished(level, stars, levelCount)
            prefs[progressKey(game)] = progress.encode()
            val earnedToday = s.earnedOn(today)
            val earned = if (rewards) RewardPolicy.earn(s.settings, stars, earnedToday) else 0
            if (earned > 0) {
                prefs[BANK] = s.bankMinutes + earned
                prefs[EARNED_DAY] = today.toEpochDay()
                prefs[EARNED_MINUTES] = earnedToday + earned
            }
            val maxReached = rewards && s.settings.enabled && earnedToday + earned >= s.settings.dailyMaxMinutes
            outcome = LevelOutcome(earned, progress, maxReached)
        }
        return outcome!!
    }

    /** Empties the bank and returns what was in it. */
    suspend fun takeBank(): Int {
        var taken = 0
        store.edit { prefs ->
            taken = prefs[BANK] ?: 0
            prefs[BANK] = 0
        }
        return taken
    }

    private fun Preferences.toState(): LearningState {
        val progress = GameId.entries.mapNotNull { game -> LevelProgress.decode(this[progressKey(game)])?.let { game to it } }.toMap()
        return LearningState(
            settings = LearningSettings(
                enabled = this[ENABLED] ?: true,
                minutesPerLevel = this[MINUTES_PER_LEVEL] ?: LearningSettings.DEFAULT_MINUTES_PER_LEVEL,
                dailyMaxMinutes = this[DAILY_MAX] ?: LearningSettings.DEFAULT_DAILY_MAX,
            ),
            progress = progress,
            tutorialsSeen = this[TUTORIALS].orEmpty().mapNotNull { name -> GameId.entries.firstOrNull { it.name == name } }.toSet(),
            numerals = this[NUMERALS]?.let { name -> NumeralStyle.entries.firstOrNull { it.name == name } },
            lettersLanguage = this[LETTERS_LANGUAGE]?.let { name -> LearnLanguage.entries.firstOrNull { it.name == name } },
            bankMinutes = this[BANK] ?: 0,
            earnedDay = this[EARNED_DAY]?.let(LocalDate::ofEpochDay),
            earnedMinutes = this[EARNED_MINUTES] ?: 0,
        )
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("learn_enabled")
        val MINUTES_PER_LEVEL = intPreferencesKey("learn_minutes_per_level")
        val DAILY_MAX = intPreferencesKey("learn_daily_max")
        val TUTORIALS = stringSetPreferencesKey("learn_tutorials_seen")
        val NUMERALS = stringPreferencesKey("learn_numerals")
        val LETTERS_LANGUAGE = stringPreferencesKey("learn_letters_language")
        val BANK = intPreferencesKey("learn_bank_minutes")
        val EARNED_DAY = longPreferencesKey("learn_earned_day")
        val EARNED_MINUTES = intPreferencesKey("learn_earned_minutes")

        fun progressKey(game: GameId) = stringPreferencesKey("learn_progress_${game.name.lowercase()}")
    }
}
