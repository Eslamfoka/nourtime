package com.nourtime.app.feature.learning

import com.nourtime.app.core.learning.ColoringPack
import com.nourtime.app.core.learning.ColoringPalette
import com.nourtime.app.core.learning.ColoringRound
import com.nourtime.app.core.learning.DotShape
import com.nourtime.app.core.learning.GamePack
import com.nourtime.app.core.learning.Level
import com.nourtime.app.core.learning.LettersGame
import com.nourtime.app.core.learning.LettersLevel
import com.nourtime.app.core.learning.MathGame
import com.nourtime.app.core.learning.MathLevel
import com.nourtime.app.core.learning.ConnectRound
import com.nourtime.app.core.learning.GameId
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LevelProgress
import com.nourtime.app.core.learning.NumeralStyle
import com.nourtime.app.core.learning.Round
import com.nourtime.app.data.learning.LearningContentRepository
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

/** The level packs of the four games, as loaded (null until loaded). */
data class HubContent(
    val math: GamePack<MathLevel>? = null,
    val letters: GamePack<LettersLevel>? = null,
    val connect: GamePack<DotShape>? = null,
    val coloring: ColoringPack? = null,
    /** Color names in the app language, spoken when a coloring color is picked. */
    val colorNames: Map<Long, String> = emptyMap(),
) {
    fun levels(game: GameId): GamePack<out Level>? = when (game) {
        GameId.MATH -> math
        GameId.LETTERS -> letters
        GameId.CONNECT -> connect
        GameId.COLORING -> coloring?.pack
    }
}

/**
 * Runs the Learning Hub. Used by the lock overlay ([rewards] true: finished levels bank minutes and
 * [onUseMinutes] turns them into a break) and by the parent's preview ([rewards] false: all levels
 * open, nothing saved).
 */
class LearningHubController(
    private val scope: CoroutineScope,
    private val repo: LearningRepository,
    private val content: LearningContentRepository,
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

    private val _today = MutableStateFlow<LocalDate?>(null)

    /** Today by the trusted clock, for "minutes earned today"; null until read. */
    val todayDate: StateFlow<LocalDate?> = _today.asStateFlow()

    private val _content = MutableStateFlow(HubContent())

    /** The games' levels, loaded from the content packs when the hub opens. */
    val hubContent: StateFlow<HubContent> = _content.asStateFlow()

    init {
        scope.launch { _today.value = today() }
        scope.launch {
            _content.value = HubContent(
                math = content.math(),
                letters = content.letterLevels(),
                connect = content.connect(),
                coloring = content.coloring(),
                colorNames = content.letters(appLanguage).colors.associate { it.argb to it.name },
            )
        }
    }

    /** An illustration's bytes from the content packs. */
    suspend fun image(id: String): ByteArray? = content.image(id)

    fun numerals(s: LearningState?): NumeralStyle =
        s?.numerals ?: if (appLanguage == LearnLanguage.ARABIC) NumeralStyle.EASTERN else NumeralStyle.WESTERN

    fun lettersLanguage(s: LearningState?): LearnLanguage = s?.lettersLanguage ?: appLanguage

    private fun progress(s: LearningState?, game: GameId) = s?.progress?.get(game) ?: LevelProgress()

    /** In the parent's preview every level is open. */
    fun playable(s: LearningState?, game: GameId, index: Int): Boolean {
        val pack = _content.value.levels(game) ?: return false
        return if (!rewards) index in pack.levels.indices else progress(s, game).playable(pack, index, age)
    }

    /** The level the child is at now (highlighted); null in the preview. */
    fun current(s: LearningState?, game: GameId): Int? {
        val pack = _content.value.levels(game) ?: return null
        return if (!rewards) null else progress(s, game).current(pack, age)
    }

    fun stars(s: LearningState?, game: GameId, index: Int): Int {
        val id = _content.value.levels(game)?.levels?.getOrNull(index)?.id ?: return 0
        return progress(s, game).starsOf(id)
    }

    fun openGame(game: GameId) {
        if (game in PLAYABLE_GAMES) _screen.value = HubScreen.Levels(game)
    }

    fun setNumerals(style: NumeralStyle) = scope.launch { repo.setNumerals(style) }

    fun setLettersLanguage(language: LearnLanguage) = scope.launch { repo.setLettersLanguage(language) }

    /** Starts level [level] (its position in the game's pack). */
    fun play(game: GameId, level: Int) {
        val s = state.value
        if (!playable(s, game, level)) return
        val c = _content.value
        // The preview shows the tutorial on the first level and changes nothing the child has done.
        val tutorial = if (rewards) s != null && game !in s.tutorialsSeen else level == 0
        advancing?.cancel()
        advancing = scope.launch {
            _screen.value = when (game) {
                GameId.MATH -> HubScreen.Playing(game, level, Round(MathGame.questions(c.math!!.levels[level], random), tutorial = tutorial))
                GameId.LETTERS -> {
                    val questions = LettersGame.questions(c.letters!!.levels[level], content.letters(lettersLanguage(s)), random)
                    // A language without content yet: stay on the level screen.
                    if (questions.isEmpty()) return@launch
                    HubScreen.Playing(game, level, Round(questions, tutorial = tutorial))
                }
                GameId.CONNECT -> HubScreen.Connecting(game, level, ConnectRound(c.connect!!.levels[level], tutorial = tutorial))
                GameId.COLORING -> c.coloring!!.let { pack ->
                    val picture = pack.pack.levels[level]
                    HubScreen.Coloring(game, level, ColoringRound(picture, ColoringPalette.of(picture, pack.distractors, random), tutorial = tutorial))
                }
            }
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
        val pack = _content.value.levels(game) ?: return
        val count = pack.levels.size
        val outcome = if (rewards) repo.finishLevel(game, pack.levels[level].id, stars, today(), rewards = true) else null
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
