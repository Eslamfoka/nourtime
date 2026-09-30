package com.nourtime.app.feature.learning

import com.nourtime.app.core.learning.GameId
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LettersLevels
import com.nourtime.app.core.learning.LevelProgress
import com.nourtime.app.core.learning.MathLevels
import com.nourtime.app.core.learning.NumeralStyle
import com.nourtime.app.core.learning.Question
import com.nourtime.app.core.learning.Round
import com.nourtime.app.data.learning.LearningRepository
import com.nourtime.app.data.learning.LearningState
import com.nourtime.app.data.settings.AgeGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/** Where the child is inside the hub. */
sealed interface HubScreen {
    data object Menu : HubScreen

    data class Levels(val game: GameId) : HubScreen

    /** [celebrating] is the choice just picked correctly, shown for a moment before moving on. */
    data class Playing(val game: GameId, val level: Int, val round: Round, val celebrating: Int? = null) : HubScreen

    data class Done(
        val game: GameId,
        val level: Int,
        val stars: Int,
        val earnedMinutes: Int,
        val dailyMaxReached: Boolean,
        val hasNext: Boolean,
    ) : HubScreen
}

/** Games that are playable today; the others show as "coming soon". */
val PLAYABLE_GAMES = listOf(GameId.MATH, GameId.LETTERS)

fun levelCount(game: GameId): Int = when (game) {
    GameId.MATH -> MathLevels.all.size
    GameId.LETTERS -> LettersLevels.all.size
    else -> 0
}

/**
 * Runs the Learning Hub. Used by the lock overlay ([rewards] true: finished levels bank minutes and
 * [onUseMinutes] turns them into a break) and by the parent's preview ([rewards] false: all levels
 * open, nothing saved).
 */
class LearningHubController(
    private val scope: CoroutineScope,
    private val repo: LearningRepository,
    private val age: AgeGroup?,
    /** The app's language: default for the letters game, the numerals and the math voice. */
    val appLanguage: LearnLanguage,
    val rewards: Boolean,
    private val today: suspend () -> LocalDate,
    private val onUseMinutes: suspend (Int) -> Unit = {},
    private val random: Random = Random.Default,
) {
    val state: StateFlow<LearningState?> = repo.state.stateIn(scope, SharingStarted.Eagerly, null)

    private val _screen = MutableStateFlow<HubScreen>(HubScreen.Menu)
    val screen: StateFlow<HubScreen> = _screen.asStateFlow()

    private var advancing: Job? = null

    fun numerals(s: LearningState?): NumeralStyle =
        s?.numerals ?: if (appLanguage == LearnLanguage.ARABIC) NumeralStyle.EASTERN else NumeralStyle.WESTERN

    fun lettersLanguage(s: LearningState?): LearnLanguage = s?.lettersLanguage ?: appLanguage

    /** In the parent's preview every level is open. */
    fun unlocked(s: LearningState?, game: GameId): Int =
        if (!rewards) levelCount(game) - 1 else (s?.progress?.get(game) ?: LevelProgress.start(game, age)).unlocked

    fun stars(s: LearningState?, game: GameId, level: Int): Int = s?.progress?.get(game)?.stars?.get(level) ?: 0

    fun openGame(game: GameId) {
        if (game in PLAYABLE_GAMES) _screen.value = HubScreen.Levels(game)
    }

    fun setNumerals(style: NumeralStyle) = scope.launch { repo.setNumerals(style) }

    fun setLettersLanguage(language: LearnLanguage) = scope.launch { repo.setLettersLanguage(language) }

    fun play(game: GameId, level: Int) {
        val s = state.value
        if (level > unlocked(s, game)) return
        val questions: List<Question> = when (game) {
            GameId.MATH -> MathLevels.questions(level, random)
            GameId.LETTERS -> LettersLevels.questions(level, lettersLanguage(s), random)
            else -> return
        }
        // The preview shows the tutorial on the first level and changes nothing the child has done.
        val tutorial = if (rewards) s != null && game !in s.tutorialsSeen else level == 0
        advancing?.cancel()
        _screen.value = HubScreen.Playing(game, level, Round(questions, tutorial = tutorial))
    }

    /** The child tapped choice [index]. */
    fun pick(index: Int): Round.Outcome {
        val playing = _screen.value as? HubScreen.Playing ?: return Round.Outcome.IGNORED
        if (playing.celebrating != null) return Round.Outcome.IGNORED
        val (next, outcome) = playing.round.pick(index)
        when (outcome) {
            Round.Outcome.WRONG -> _screen.value = playing.copy(round = next)
            Round.Outcome.CORRECT -> {
                if (playing.round.isTutorialQuestion && rewards) scope.launch { repo.markTutorialSeen(playing.game) }
                _screen.value = playing.copy(celebrating = index)
                advancing = scope.launch {
                    delay(CELEBRATE_MS)
                    if (next.done) finish(playing, next) else _screen.value = playing.copy(round = next, celebrating = null)
                }
            }
            Round.Outcome.IGNORED -> Unit
        }
        return outcome
    }

    private suspend fun finish(playing: HubScreen.Playing, round: Round) {
        val count = levelCount(playing.game)
        val outcome = if (rewards) repo.finishLevel(playing.game, playing.level, round.stars, count, age, today(), rewards = true) else null
        _screen.value = HubScreen.Done(
            game = playing.game,
            level = playing.level,
            stars = round.stars,
            earnedMinutes = outcome?.earnedMinutes ?: 0,
            dailyMaxReached = outcome?.dailyMaxReached ?: false,
            hasNext = playing.level + 1 < count,
        )
    }

    /** One step back; false when already at the menu (the caller closes the hub). */
    fun back(): Boolean {
        advancing?.cancel()
        _screen.value = when (val s = _screen.value) {
            HubScreen.Menu -> return false
            is HubScreen.Levels -> HubScreen.Menu
            is HubScreen.Playing -> HubScreen.Levels(s.game)
            is HubScreen.Done -> HubScreen.Levels(s.game)
        }
        return true
    }

    fun toMenu() {
        advancing?.cancel()
        _screen.value = HubScreen.Menu
    }

    /** Turns the banked minutes into a break now. */
    fun useMinutes() {
        if (!rewards) return
        scope.launch {
            val minutes = repo.takeBank()
            if (minutes > 0) onUseMinutes(minutes)
        }
    }

    private companion object {
        const val CELEBRATE_MS = 900L
    }
}
