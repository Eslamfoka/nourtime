package com.nourtime.app.feature.learning

import com.nourtime.app.core.learning.ClockLevel
import com.nourtime.app.core.learning.ColoringPack
import com.nourtime.app.core.learning.ColoringRound
import com.nourtime.app.core.learning.DotShape
import com.nourtime.app.core.learning.GamePack
import com.nourtime.app.core.learning.Level
import com.nourtime.app.core.learning.LettersLevel
import com.nourtime.app.core.learning.MathLevel
import com.nourtime.app.core.learning.MemoryLevel
import com.nourtime.app.core.learning.MemoryRound
import com.nourtime.app.core.learning.ConnectRound
import com.nourtime.app.core.learning.GameId
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LevelProgress
import com.nourtime.app.core.learning.ListenLevel
import com.nourtime.app.core.learning.NumeralStyle
import com.nourtime.app.core.learning.PatternLevel
import com.nourtime.app.core.learning.Round
import com.nourtime.app.core.learning.TraceLetter
import com.nourtime.app.core.learning.TraceRound
import com.nourtime.app.core.learning.LetterTile
import com.nourtime.app.core.learning.WordLevel
import com.nourtime.app.core.learning.WordRound
import com.nourtime.app.core.learning.SortLevel
import com.nourtime.app.core.learning.SortRound
import com.nourtime.app.core.learning.ShopLevel
import com.nourtime.app.core.learning.ShopRound
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

    /** Letter Tracing. [celebrating]: the letter is complete and shown for a moment. */
    data class Tracing(val game: GameId, val level: Int, val round: TraceRound, val celebrating: Boolean = false) : HubScreen

    /** Memory Match. [celebrating]: all pairs found, shown for a moment. */
    data class Memory(val game: GameId, val level: Int, val round: MemoryRound, val celebrating: Boolean = false) : HubScreen

    /** Word Builder. [celebrating]: the word just finished, shown for a moment before the next. */
    data class Words(val game: GameId, val level: Int, val round: WordRound, val celebrating: String? = null, val lastWrong: Boolean = false) : HubScreen

    /** Sorting. [lastWrong]: the last drop was the wrong group. */
    data class Sorting(val game: GameId, val level: Int, val round: SortRound, val lastWrong: Boolean = false, val celebrating: Boolean = false) : HubScreen

    /** Little Shop. [lastOver]: the last coin went over the amount; [paidFor]: the thing just bought. */
    data class Shop(val game: GameId, val level: Int, val round: ShopRound, val lastOver: Boolean = false, val paidFor: Int? = null) : HubScreen

    data class Done(
        val game: GameId,
        val level: Int,
        val stars: Int,
        val earnedMinutes: Int,
        val dailyMaxReached: Boolean,
        val hasNext: Boolean,
    ) : HubScreen
}

/** The level packs of the games, as loaded (null until loaded). */
data class HubContent(
    val math: GamePack<MathLevel>? = null,
    val letters: GamePack<LettersLevel>? = null,
    val connect: GamePack<DotShape>? = null,
    val coloring: ColoringPack? = null,
    val listen: GamePack<ListenLevel>? = null,
    val patterns: GamePack<PatternLevel>? = null,
    val clock: GamePack<ClockLevel>? = null,
    val memory: GamePack<MemoryLevel>? = null,
    val words: GamePack<WordLevel>? = null,
    val sorting: GamePack<SortLevel>? = null,
    val shop: GamePack<ShopLevel>? = null,
    /** Letter Tracing has one pack per language. */
    val tracing: Map<LearnLanguage, GamePack<TraceLetter>> = emptyMap(),
    /** Color names in the app language, spoken when a coloring color is picked. */
    val colorNames: Map<Long, String> = emptyMap(),
) {
    /** The levels of [game]; [language] is the words language (only Letter Tracing depends on it). */
    fun levels(game: GameId, language: LearnLanguage): GamePack<out Level>? = GameRegistry.of(game).levels(this, language)
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
                listen = content.listen(),
                patterns = content.patterns(),
                clock = content.clock(),
                memory = content.memory(),
                words = content.words(),
                sorting = content.sorting(),
                shop = content.shop(),
                tracing = LearnLanguage.entries.associateWith { content.tracing(it) },
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

    /** The levels of [game] as the child sees them (in the chosen words language). */
    fun levels(s: LearningState?, game: GameId): GamePack<out Level>? = _content.value.levels(game, lettersLanguage(s))

    /** In the parent's preview every level is open. */
    fun playable(s: LearningState?, game: GameId, index: Int): Boolean {
        val pack = levels(s, game) ?: return false
        return if (!rewards) index in pack.levels.indices else progress(s, game).playable(pack, index, age)
    }

    /** The level the child is at now (highlighted); null in the preview. */
    fun current(s: LearningState?, game: GameId): Int? {
        val pack = levels(s, game) ?: return null
        return if (!rewards) null else progress(s, game).current(pack, age)
    }

    fun stars(s: LearningState?, game: GameId, index: Int): Int {
        val id = levels(s, game)?.levels?.getOrNull(index)?.id ?: return 0
        return progress(s, game).starsOf(id)
    }

    fun openGame(game: GameId) {
        _screen.value = HubScreen.Levels(game)
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
            // Null: nothing to play in this level (e.g. no words in that language yet), stay here.
            val start = GameStart(c, content, lettersLanguage(s), random)
            _screen.value = GameRegistry.of(game).start(start, level, tutorial) ?: return@launch
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

    // --- Little Shop ---

    /** A coin of [value] goes on the counter. */
    fun shopAdd(value: Int): ShopRound.Outcome {
        val sh = _screen.value as? HubScreen.Shop ?: return ShopRound.Outcome.IGNORED
        if (sh.paidFor != null) return ShopRound.Outcome.IGNORED
        val (next, outcome) = sh.round.add(value)
        when (outcome) {
            ShopRound.Outcome.IGNORED -> Unit
            ShopRound.Outcome.ADDED, ShopRound.Outcome.OVER -> {
                if (sh.round.tutorial && sh.round.index == 0 && outcome == ShopRound.Outcome.ADDED) markTutorial(sh.game)
                _screen.value = sh.copy(round = next, lastOver = outcome == ShopRound.Outcome.OVER)
            }
            ShopRound.Outcome.PAID, ShopRound.Outcome.DONE -> {
                // The bought thing stays a moment before the next one (or the stars).
                _screen.value = sh.copy(round = next, lastOver = false, paidFor = sh.round.index)
                advancing = scope.launch {
                    delay(WORD_DONE_MS)
                    if (outcome == ShopRound.Outcome.DONE) {
                        finish(sh.game, sh.level, next.stars)
                    } else {
                        val now = _screen.value as? HubScreen.Shop ?: return@launch
                        _screen.value = now.copy(paidFor = null)
                    }
                }
            }
        }
        return outcome
    }

    /** Coin [i] on the counter goes back to the purse. */
    fun shopRemove(i: Int) {
        val sh = _screen.value as? HubScreen.Shop ?: return
        if (sh.paidFor == null) _screen.value = sh.copy(round = sh.round.remove(i), lastOver = false)
    }

    // --- Sorting ---

    /** The child dropped the current thing into group [bin]. */
    fun sortDrop(bin: Int): SortRound.Outcome {
        val st = _screen.value as? HubScreen.Sorting ?: return SortRound.Outcome.IGNORED
        if (st.celebrating) return SortRound.Outcome.IGNORED
        val (next, outcome) = st.round.drop(bin)
        if (outcome == SortRound.Outcome.IGNORED) return outcome
        if (st.round.tutorial && st.round.index == 0 && outcome != SortRound.Outcome.WRONG) markTutorial(st.game)
        _screen.value = st.copy(round = next, lastWrong = outcome == SortRound.Outcome.WRONG, celebrating = next.done)
        if (next.done) celebrateThenFinish(st.game, st.level, next.stars)
        return outcome
    }

    // --- Word Builder ---

    /** The child put [tile] on the word (by tapping it or dragging it up). */
    fun wordPlace(tile: LetterTile): WordRound.Outcome {
        val w = _screen.value as? HubScreen.Words ?: return WordRound.Outcome.IGNORED
        if (w.celebrating != null) return WordRound.Outcome.IGNORED
        val word = w.round.current?.word?.word
        val (next, outcome) = w.round.place(tile)
        when (outcome) {
            WordRound.Outcome.IGNORED -> Unit
            WordRound.Outcome.WRONG -> _screen.value = w.copy(round = next, lastWrong = true)
            WordRound.Outcome.PLACED -> {
                if (w.round.tutorial && w.round.index == 0 && w.round.placed.isEmpty()) markTutorial(w.game)
                _screen.value = w.copy(round = next, lastWrong = false)
            }
            WordRound.Outcome.WORD_DONE, WordRound.Outcome.DONE -> {
                // The finished word stays on screen a moment (and is read out) before the next one.
                _screen.value = w.copy(round = next, celebrating = word, lastWrong = false)
                advancing = scope.launch {
                    delay(WORD_DONE_MS)
                    if (outcome == WordRound.Outcome.DONE) {
                        finish(w.game, w.level, next.stars)
                    } else {
                        val now = _screen.value as? HubScreen.Words ?: return@launch
                        _screen.value = now.copy(celebrating = null)
                    }
                }
            }
        }
        return outcome
    }

    // --- Memory Match ---

    /** The child turned card [i]; a mismatched pair turns back by itself after a moment. */
    fun memoryTurn(i: Int): MemoryRound.Outcome {
        val m = _screen.value as? HubScreen.Memory ?: return MemoryRound.Outcome.IGNORED
        if (m.celebrating) return MemoryRound.Outcome.IGNORED
        val (next, outcome) = m.round.turn(i)
        if (outcome == MemoryRound.Outcome.IGNORED) return outcome
        if (m.round.tutorial && outcome == MemoryRound.Outcome.MATCH) markTutorial(m.game)
        _screen.value = m.copy(round = next, celebrating = next.done)
        when (outcome) {
            MemoryRound.Outcome.MISMATCH -> advancing = scope.launch {
                delay(MISMATCH_MS)
                val now = _screen.value as? HubScreen.Memory ?: return@launch
                _screen.value = now.copy(round = now.round.hide())
            }
            MemoryRound.Outcome.DONE -> celebrateThenFinish(m.game, m.level, next.stars)
            else -> Unit
        }
        return outcome
    }

    // --- Letter Tracing ---

    /** True when a finger going down at ([x], [y]) (0..1 of the canvas) may trace on. */
    fun traceCanStart(x: Float, y: Float): Boolean {
        val t = _screen.value as? HubScreen.Tracing ?: return false
        return !t.celebrating && t.round.canStart(x, y, young)
    }

    /** The finger moved while tracing; returns what happened (STRAYED ends the drag). */
    fun traceMove(x: Float, y: Float): TraceRound.Outcome = trace { it.move(x, y, young) }

    /** A tap, for the dots of a letter. */
    fun traceTap(x: Float, y: Float): TraceRound.Outcome = trace { it.tap(x, y, young) }

    private fun trace(step: (TraceRound) -> Pair<TraceRound, TraceRound.Outcome>): TraceRound.Outcome {
        val t = _screen.value as? HubScreen.Tracing ?: return TraceRound.Outcome.IGNORED
        if (t.celebrating) return TraceRound.Outcome.IGNORED
        val (next, outcome) = step(t.round)
        if (outcome == TraceRound.Outcome.IGNORED) return outcome
        if (t.round.tutorial && outcome != TraceRound.Outcome.STRAYED && t.round.stroke == 0 && t.round.reached == 0) markTutorial(t.game)
        _screen.value = t.copy(round = next, celebrating = next.done)
        if (next.done) celebrateThenFinish(t.game, t.level, next.stars)
        return outcome
    }

    /** Small children get a wider line to stay on. */
    private val young: Boolean get() = age == null || age == AgeGroup.AGES_3_6

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
        val pack = levels(state.value, game) ?: return
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
            is HubScreen.Tracing -> HubScreen.Levels(s.game)
            is HubScreen.Memory -> HubScreen.Levels(s.game)
            is HubScreen.Words -> HubScreen.Levels(s.game)
            is HubScreen.Sorting -> HubScreen.Levels(s.game)
            is HubScreen.Shop -> HubScreen.Levels(s.game)
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

        /** Two cards that don't match stay up this long, so the child can remember them. */
        const val MISMATCH_MS = 1_200L

        /** A finished word stays this long, with its picture, before the next word. */
        const val WORD_DONE_MS = 1_800L

        /** A finished drawing stays on screen this long before the stars. */
        const val DRAWING_DONE_MS = 1_600L
    }
}
