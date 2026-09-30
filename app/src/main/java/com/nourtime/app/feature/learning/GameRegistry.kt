package com.nourtime.app.feature.learning

import androidx.compose.ui.graphics.Color
import com.nourtime.app.R
import com.nourtime.app.core.designsystem.theme.NourPalette
import com.nourtime.app.core.learning.ClockGame
import com.nourtime.app.core.learning.ColoringPalette
import com.nourtime.app.core.learning.ColoringRound
import com.nourtime.app.core.learning.ConnectRound
import com.nourtime.app.core.learning.GameId
import com.nourtime.app.core.learning.GamePack
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LettersGame
import com.nourtime.app.core.learning.Level
import com.nourtime.app.core.learning.ListenGame
import com.nourtime.app.core.learning.MathGame
import com.nourtime.app.core.learning.MemoryGame
import com.nourtime.app.core.learning.MemoryRound
import com.nourtime.app.core.learning.PatternGame
import com.nourtime.app.core.learning.Round
import com.nourtime.app.core.learning.TraceRound
import com.nourtime.app.core.learning.WordBuilder
import com.nourtime.app.core.learning.WordRound
import com.nourtime.app.core.learning.SortGame
import com.nourtime.app.core.learning.ShopGame
import com.nourtime.app.data.learning.LearningContentRepository
import kotlin.random.Random

/** The setting a game's level screen offers above the levels. */
internal enum class GameOption { NONE, NUMERALS, LANGUAGE }

/** What a game gets when a level starts. */
internal class GameStart(
    val packs: HubContent,
    val repo: LearningContentRepository,
    /** The language chosen for words (Letters & Words, Listen & Find). */
    val wordsLanguage: LearnLanguage,
    val random: Random,
)

/**
 * One Learning Hub game: its tile, the option on its level screen, its levels and how a level starts.
 * [wordsLanguage]: the questions speak the chosen words language (else the app language).
 * [start] returns null when the level can't be played (e.g. no content in that language yet).
 */
internal class GameSpec(
    val id: GameId,
    val look: GameLook,
    val option: GameOption,
    val wordsLanguage: Boolean,
    val levels: (HubContent, LearnLanguage) -> GamePack<out Level>?,
    val start: suspend GameStart.(level: Int, tutorial: Boolean) -> HubScreen?,
)

/**
 * Every game, in menu order. A new game is one [GameId], one entry here (plus its pack in
 * [HubContent]) and its engine and screen; the menu, level screens, progress and rewards follow.
 */
internal object GameRegistry {
    val all: List<GameSpec> = listOf(
        GameSpec(
            GameId.MATH,
            GameLook("🔢", NourPalette.Mint, R.string.learn_game_math, R.string.learn_game_math_hint),
            GameOption.NUMERALS,
            wordsLanguage = false,
            levels = { c, _ -> c.math },
            start = { level, tutorial ->
                HubScreen.Playing(GameId.MATH, level, Round(MathGame.questions(packs.math!!.levels[level], random), tutorial = tutorial))
            },
        ),
        GameSpec(
            GameId.LETTERS,
            GameLook("🔤", NourPalette.Coral, R.string.learn_game_letters, R.string.learn_game_letters_hint),
            GameOption.LANGUAGE,
            wordsLanguage = true,
            levels = { c, _ -> c.letters },
            start = { level, tutorial ->
                LettersGame.questions(packs.letters!!.levels[level], repo.letters(wordsLanguage), random)
                    .takeIf { it.isNotEmpty() }
                    ?.let { HubScreen.Playing(GameId.LETTERS, level, Round(it, tutorial = tutorial)) }
            },
        ),
        GameSpec(
            GameId.CONNECT,
            GameLook("✏️", Color(0xFF7E8CE0), R.string.learn_game_connect, R.string.learn_game_connect_hint),
            GameOption.NUMERALS,
            wordsLanguage = false,
            levels = { c, _ -> c.connect },
            start = { level, tutorial ->
                HubScreen.Connecting(GameId.CONNECT, level, ConnectRound(packs.connect!!.levels[level], tutorial = tutorial))
            },
        ),
        GameSpec(
            GameId.COLORING,
            GameLook("🎨", NourPalette.GoldDeep, R.string.learn_game_coloring, R.string.learn_game_coloring_hint),
            GameOption.NONE,
            wordsLanguage = false,
            levels = { c, _ -> c.coloring?.pack },
            start = { level, tutorial ->
                val pack = packs.coloring!!
                val picture = pack.pack.levels[level]
                HubScreen.Coloring(GameId.COLORING, level, ColoringRound(picture, ColoringPalette.of(picture, pack.distractors, random), tutorial = tutorial))
            },
        ),
        GameSpec(
            GameId.LISTEN,
            GameLook("👂", Color(0xFF4DB6AC), R.string.learn_game_listen, R.string.learn_game_listen_hint),
            GameOption.LANGUAGE,
            wordsLanguage = true,
            levels = { c, _ -> c.listen },
            start = { level, tutorial ->
                ListenGame.questions(packs.listen!!.levels[level], repo.letters(wordsLanguage), random)
                    .takeIf { it.isNotEmpty() }
                    ?.let { HubScreen.Playing(GameId.LISTEN, level, Round(it, tutorial = tutorial)) }
            },
        ),
        GameSpec(
            GameId.PATTERNS,
            GameLook("🧩", Color(0xFFBA68C8), R.string.learn_game_patterns, R.string.learn_game_patterns_hint),
            GameOption.NUMERALS,
            wordsLanguage = false,
            levels = { c, _ -> c.patterns },
            start = { level, tutorial ->
                PatternGame.questions(packs.patterns!!.levels[level], repo.letters(wordsLanguage), random)
                    .takeIf { it.isNotEmpty() }
                    ?.let { HubScreen.Playing(GameId.PATTERNS, level, Round(it, tutorial = tutorial)) }
            },
        ),
        GameSpec(
            GameId.CLOCK,
            GameLook("🕒", Color(0xFF4FC3F7), R.string.learn_game_clock, R.string.learn_game_clock_hint),
            GameOption.NUMERALS,
            wordsLanguage = false,
            levels = { c, _ -> c.clock },
            start = { level, tutorial ->
                HubScreen.Playing(GameId.CLOCK, level, Round(ClockGame.questions(packs.clock!!.levels[level], random), tutorial = tutorial))
            },
        ),
        GameSpec(
            GameId.MEMORY,
            GameLook("🃏", Color(0xFF9575CD), R.string.learn_game_memory, R.string.learn_game_memory_hint),
            GameOption.LANGUAGE,
            wordsLanguage = true,
            levels = { c, _ -> c.memory },
            start = { level, tutorial ->
                MemoryGame.deal(packs.memory!!.levels[level], repo.letters(wordsLanguage), random)
                    .takeIf { it.isNotEmpty() }
                    ?.let { HubScreen.Memory(GameId.MEMORY, level, MemoryRound(it, tutorial = tutorial)) }
            },
        ),
        GameSpec(
            GameId.WORDS,
            GameLook("🧱", Color(0xFFFFB74D), R.string.learn_game_words, R.string.learn_game_words_hint),
            GameOption.LANGUAGE,
            wordsLanguage = true,
            levels = { c, _ -> c.words },
            start = { level, tutorial ->
                val spec = packs.words!!.levels[level]
                WordBuilder.tasks(spec, repo.letters(wordsLanguage), random)
                    .takeIf { it.isNotEmpty() }
                    ?.let { HubScreen.Words(GameId.WORDS, level, WordRound(it, tutorial = tutorial, hint = spec.hint)) }
            },
        ),
        GameSpec(
            GameId.SORTING,
            GameLook("🗂️", Color(0xFF81C784), R.string.learn_game_sorting, R.string.learn_game_sorting_hint),
            GameOption.LANGUAGE,
            wordsLanguage = true,
            levels = { c, _ -> c.sorting },
            start = { level, tutorial ->
                SortGame.deal(packs.sorting!!.levels[level], repo.letters(wordsLanguage), random)
                    ?.let { HubScreen.Sorting(GameId.SORTING, level, it.copy(tutorial = tutorial)) }
            },
        ),
        GameSpec(
            GameId.SHOP,
            GameLook("🛒", Color(0xFF4DD0E1), R.string.learn_game_shop, R.string.learn_game_shop_hint),
            GameOption.NUMERALS,
            wordsLanguage = true,
            levels = { c, _ -> c.shop },
            start = { level, tutorial ->
                ShopGame.deal(packs.shop!!.levels[level], repo.letters(wordsLanguage), random)
                    ?.let { HubScreen.Shop(GameId.SHOP, level, it.copy(tutorial = tutorial)) }
            },
        ),
        GameSpec(
            GameId.TRACING,
            GameLook("✍️", Color(0xFFFF8A65), R.string.learn_game_tracing, R.string.learn_game_tracing_hint),
            GameOption.LANGUAGE,
            wordsLanguage = true,
            levels = { c, language -> c.tracing[language] },
            start = { level, tutorial ->
                packs.tracing[wordsLanguage]?.levels?.getOrNull(level)?.let { HubScreen.Tracing(GameId.TRACING, level, TraceRound(it, tutorial = tutorial)) }
            },
        ),
    )

    fun of(id: GameId): GameSpec = all.first { it.id == id }
}
