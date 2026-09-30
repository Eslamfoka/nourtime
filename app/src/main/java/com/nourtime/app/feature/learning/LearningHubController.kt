package com.nourtime.app.feature.learning

import com.nourtime.app.core.learning.ColoringLevels
import com.nourtime.app.core.learning.ColoringRound
import com.nourtime.app.core.learning.ConnectLevels
import com.nourtime.app.core.learning.ConnectRound
import com.nourtime.app.core.learning.GameId
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LettersLevels
import com.nourtime.app.core.learning.LevelProgress
import com.nourtime.app.core.learning.MathLevels
import com.nourtime.app.core.learning.NumeralStyle
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

    /** Number Connect. [celebrating]: the drawing is complete and shown for a moment. */
    data class Connecting(val game: GameId, val level: Int, val round: ConnectRound, val celebrating: Boolean = false) : HubScreen

    /** Coloring Match. [lastWrong]: the last fill didn't match the reference. */
    data class Coloring(
        val game: GameId,
        val level: Int,
        val round: ColoringRound,
        val lastWrong: Boolean = false,
        val celebrating: Boolean = false,
    ) : HubScreen

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
val PLAYABLE_GAMES = listOf(GameId.MATH, GameId.LETTERS, GameId.CONNECT, GameId.COLORING)

fun levelCount(game: GameId): Int = when (game) {
    GameId.MATH -> MathLevels.all.size
    GameId.LETTERS -> LettersLevels.all.size
    GameId.CONNECT -> ConnectLevels.all.size
    GameId.COLORING -> ColoringLevels.all.size
}

/**
 * Runs the Learning Hub. Used by the lock overlay ([rewards] true: finished levels bank minutes and
 * [onUseMinutes] turns them into a break) and by the parent's preview ([rewards] false: all levels
 * open, nothing saved).
 */
class LearningHubController(
    private val scope: CoroutineScope,
    private val repo: LearningRepository,
    val age: AgeGroup?,
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
        // The preview shows the tutorial on the first level and changes nothing the child has done.
        val tutorial = if (rewards) s != null && game !in s.tutorialsSeen else level == 0
        advancing?.cancel()
        _screen.value = when (game) {
            GameId.MATH -> HubScreen.Playing(game, level, Round(MathLevels.questions(level, random), tutorial = tutorial))
            GameId.LETTERS -> HubScreen.Playing(game, level, Round(LettersLevels.questions(level, lettersLanguage(s), random), tutorial = tutorial))
            GameId.CONNECT -> HubScreen.Connecting(game, level, ConnectRound(ConnectLevels.all[level], tutorial = tutorial))
            GameId.COLORING -> HubScreen.Coloring(
                game,
                level,
                ColoringRound(ColoringLevels.all[level], ColoringLevels.palette(level, random), tutorial = tutorial),
            )
        }
    }

    // --- Number Connect ---

    /** The finger reached [dot] while drawing. */
    fun connectReach(dot: Int) {
        val c = _screen.value as? HubScreen.Connecting ?: return
        if (c.celebrating) return
        val next = c.round.reach(dot)
        if (next == c.round) return
        if (c.round.tutorial && c.round.drawn == 0) markTutorial(c.game)
        _screen.value = c.copy(round = next, celebrating = next.done)
        if (next.done) celebrateThenFinish(c.game, c.level, next.stars)
    }

    /** The finger let go near [dot] (null: nowhere near one). */
    fun connectRelease(dot: Int?) {
        val c = _screen.value as? HubScreen.Connecting ?: return
        if (!c.celebrating) _screen.value = c.copy(round = c.round.release(dot))
    }

    // --- Coloring Match ---

    fun colorSelect(color: Long) {
        val c = _screen.value as? HubScreen.Coloring ?: return
        if (!c.celebrating) _screen.value = c.copy(round = c.round.select(color), lastWrong = false)
    }

    /** Returns true when the fill was right. */
    fun colorFill(region: Int): Boolean {
        val c = _screen.value as? HubScreen.Coloring ?: return false
        if (c.celebrating) return false
        val (next, outcome) = c.round.fill(region)
        if (outcome == ColoringRound.Outcome.IGNORED) return false
        if (c.round.tutorial && outcome == ColoringRound.Outcome.RIGHT) markTutorial(c.game)
        _screen.value = c.copy(round = next, lastWrong = outcome == ColoringRound.Outcome.WRONG, celebrating = next.done)
        if (next.done) celebrateThenFinish(c.game, c.level, next.stars)
        return outcome == ColoringRound.Outcome.RIGHT
    }

    private fun markTutorial(game: GameId) {
        if (rewards) scope.launch { repo.markTutorialSeen(game) }
    }

    private fun celebrateThenFinish(game: GameId, level: Int, stars: Int) {
        advancing = scope.launch {
            delay(DRAWING_DONE_MS)
            finish(game, level, stars)
        }
    }

    /** The child tapped choice [index]. */
    fun pick(index: Int): Round.Outcome {
        val playing = _screen.value as? HubScreen.Playing ?: return Round.Outcome.IGNORED
        if (playing.celebrating != null) return Round.Outcome.IGNORED
        val (next, outcome) = playing.round.pick(index)
        when (outcome) {
            Round.Outcome.WRONG -> _screen.value = playing.copy(round = next)
            Round.Outcome.CORRECT -> {
                if (playing.round.isTutorialQuestion) markTutorial(playing.game)
                _screen.value = playing.copy(celebrating = index)
                advancing = scope.launch {
                    delay(CELEBRATE_MS)
                    if (next.done) finish(playing.game, playing.level, next.stars) else _screen.value = playing.copy(round = next, celebrating = null)
                }
            }
            Round.Outcome.IGNORED -> Unit
        }
        return outcome
    }

    private suspend fun finish(game: GameId, level: Int, stars: Int) {
        val count = levelCount(game)
        val outcome = if (rewards) repo.finishLevel(game, level, stars, count, age, today(), rewards = true) else null
        _screen.value = HubScreen.Done(
            game = game,
            level = level,
            stars = stars,
            earnedMinutes = outcome?.earnedMinutes ?: 0,
            dailyMaxReached = outcome?.dailyMaxReached ?: false,
            hasNext = level + 1 < count,
        )
    }

    /** One step back; false when already at the menu (the caller closes the hub). */
    fun back(): Boolean {
        advancing?.cancel()
        _screen.value = when (val s = _screen.value) {
            HubScreen.Menu -> return false
            is HubScreen.Levels -> HubScreen.Menu
            is HubScreen.Playing -> HubScreen.Levels(s.game)
            is HubScreen.Connecting -> HubScreen.Levels(s.game)
            is HubScreen.Coloring -> HubScreen.Levels(s.game)
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

        /** A finished drawing stays on screen this long before the stars. */
        const val DRAWING_DONE_MS = 1_600L
    }
}
