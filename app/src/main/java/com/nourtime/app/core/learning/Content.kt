package com.nourtime.app.core.learning

import com.nourtime.app.data.settings.AgeGroup

/** Anything a child can pick on a game's level screen. Its [id] is stable across content updates. */
interface Level {
    val id: String
}

object LevelIds {
    private val PATTERN = Regex("[a-z0-9][a-z0-9_-]{0,47}")

    /** Lowercase letters, digits, - and _: safe in stored progress ("id:stars,…") and file names. */
    fun valid(id: String): Boolean = PATTERN.matches(id)
}

/**
 * The levels of one game, in play order, loaded from a content pack. [startAt] lets older children
 * begin further in (age group → level id).
 */
data class GamePack<T : Level>(val levels: List<T>, val startAt: Map<AgeGroup, String> = emptyMap()) {
    fun indexOf(id: String): Int = levels.indexOfFirst { it.id == id }

    fun startIndex(age: AgeGroup?): Int = age?.let { startAt[it] }?.let(::indexOf)?.takeIf { it >= 0 } ?: 0
}

/** A thing children learn a word for (apple, cat, …), shared by every language. */
data class Concept(val id: String, val emoji: String, val category: String, val image: String? = null)

/** A letter with its spoken name and a word (and picture) that starts with it. */
data class LetterEntry(val letter: String, val name: String, val word: String, val emoji: String, val image: String? = null)

data class WordEntry(val concept: String, val word: String, val emoji: String, val category: String, val image: String? = null)

data class ColorEntry(val id: String, val name: String, val argb: Long)

/** Everything language-specific the Letters & Words game needs, for one language. */
data class LettersLanguagePack(
    val language: LearnLanguage,
    val letters: List<LetterEntry>,
    val words: List<WordEntry>,
    val colors: List<ColorEntry>,
) {
    /** What's read aloud for a letter: "A, Apple" / "ألف، أرنب". */
    fun speech(entry: LetterEntry): String =
        if (language == LearnLanguage.ARABIC) "${entry.name}، ${entry.word}" else "${entry.name}, ${entry.word}"
}

/** Coloring Match levels plus the colors that can be offered as wrong choices. */
data class ColoringPack(val pack: GamePack<ColoringPicture>, val distractors: List<Long>)
