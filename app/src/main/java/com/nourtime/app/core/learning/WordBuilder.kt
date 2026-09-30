package com.nourtime.app.core.learning

import kotlin.random.Random

/**
 * One Word Builder level (from `words/levels.json`): [words] words to spell, each [minLength] to
 * [maxLength] letters, from [categories] (empty = all), with [extraTiles] wrong letters among the
 * tiles. [hint] shows the whole word faded above the one being built (for small children).
 */
data class WordLevel(
    override val id: String,
    val words: Int = 3,
    val minLength: Int = 2,
    val maxLength: Int = 4,
    val categories: Set<String> = emptySet(),
    val extraTiles: Int = 0,
    val hint: Boolean = false,
) : Level

/** A letter tile; [id] tells two tiles with the same letter apart. */
data class LetterTile(val id: Int, val letter: String)

/** A word to spell, its picture, and its shuffled tiles (its letters plus the wrong ones). */
data class WordTask(val word: WordEntry, val letters: List<String>, val tiles: List<LetterTile>)

/**
 * Spelling the words of a level, letter by letter in reading order: a tile with the next letter goes
 * in, any other bounces back and counts as a mistake. Stars from mistakes, like the drawing games.
 */
data class WordRound(
    val tasks: List<WordTask>,
    val index: Int = 0,
    /** Tiles placed in the current word, in order. */
    val placed: List<LetterTile> = emptyList(),
    val mistakes: Int = 0,
    val tutorial: Boolean = false,
    /** Show the whole word faded above the one being built. */
    val hint: Boolean = false,
) {
    enum class Outcome { PLACED, WRONG, WORD_DONE, DONE, IGNORED }

    val done: Boolean get() = index >= tasks.size
    val current: WordTask? get() = tasks.getOrNull(index)

    /** The word so far (the text engine joins Arabic letters as they come). */
    val built: String get() = placed.joinToString("") { it.letter }

    /** The letter the next tile must carry. */
    val next: String? get() = current?.letters?.getOrNull(placed.size)

    val stars: Int get() = Stars.fromMistakes(mistakes)

    fun isPlaced(tile: LetterTile): Boolean = placed.any { it.id == tile.id }

    fun place(tile: LetterTile): Pair<WordRound, Outcome> {
        val task = current ?: return this to Outcome.IGNORED
        if (isPlaced(tile) || tile !in task.tiles) return this to Outcome.IGNORED
        if (tile.letter != next) return copy(mistakes = if (tutorial && index == 0) mistakes else mistakes + 1) to Outcome.WRONG
        val now = placed + tile
        if (now.size < task.letters.size) return copy(placed = now) to Outcome.PLACED
        val after = copy(index = index + 1, placed = emptyList())
        return after to if (after.done) Outcome.DONE else Outcome.WORD_DONE
    }
}

object WordBuilder {
    /** The letters of a word as tiles show them (spaces never; words with spaces aren't used). */
    fun letters(word: String): List<String> = word.map { it.toString() }

    /** Letters that look alike count as one: the alef forms, ة/ه, ى/ي, and upper/lower case. */
    fun family(letter: String): String = when (letter) {
        "أ", "إ", "آ", "ٱ" -> "ا"
        "ة" -> "ه"
        "ى" -> "ي"
        else -> letter.lowercase()
    }

    /** Words of the pack that fit the level. */
    fun candidates(spec: WordLevel, content: LettersLanguagePack): List<WordEntry> =
        content.words.filter { w ->
            ' ' !in w.word && '-' !in w.word &&
                w.word.length in spec.minLength..spec.maxLength &&
                (spec.categories.isEmpty() || w.category in spec.categories)
        }

    /** Deals a level; empty when the language has too few fitting words. */
    fun tasks(spec: WordLevel, content: LettersLanguagePack, random: Random): List<WordTask> {
        val words = candidates(spec, content).shuffled(random).take(spec.words)
        if (words.size < spec.words) return emptyList()
        val alphabet = content.letters.map { it.letter }
        return words.map { w ->
            val letters = letters(w.word)
            // Wrong tiles: letters that aren't in the word, nor a look-alike of one (أ next to ا is a
            // spelling lesson of its own, not a fair wrong tile), so no tile is "almost right".
            val inWord = letters.map(::family).toSet()
            val extras = alphabet.filter { family(it) !in inWord }.shuffled(random).take(spec.extraTiles)
            val tiles = (letters + extras).mapIndexed { i, l -> LetterTile(i, l) }.shuffled(random)
            WordTask(w, letters, tiles)
        }
    }
}
