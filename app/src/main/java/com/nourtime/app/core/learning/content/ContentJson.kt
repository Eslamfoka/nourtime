package com.nourtime.app.core.learning.content

import com.nourtime.app.core.learning.ClockGame
import com.nourtime.app.core.learning.Concept
import com.nourtime.app.core.learning.LettersGame
import com.nourtime.app.core.learning.ListenGame
import com.nourtime.app.core.learning.MathGame
import com.nourtime.app.core.learning.PatternGame
import kotlinx.serialization.Serializable

// The file formats of the content packs under `assets/learning/`. See docs/content-packs.md.

/** Every pack file carries its format version; the app skips files it can't read. */
sealed interface Versioned {
    val schema: Int
}

@Serializable
data class MathFile(
    override val schema: Int,
    val startAt: Map<String, String> = emptyMap(),
    val levels: List<MathLevelJson>,
) : Versioned

@Serializable
data class MathLevelJson(
    val id: String,
    val ops: List<String>,
    val max: Int = 10,
    val min: Int = 1,
    val minFactor: Int = 1,
    val factor: Int = 10,
    val dots: Boolean = false,
    val choices: Int = 4,
    val questions: Int = MathGame.QUESTIONS,
)

@Serializable
data class LettersLevelsFile(
    override val schema: Int,
    val startAt: Map<String, String> = emptyMap(),
    val levels: List<LettersLevelJson>,
) : Versioned

@Serializable
data class LettersLevelJson(
    val id: String,
    val tasks: List<String>,
    val choices: Int = 3,
    val firstLettersOnly: Boolean = false,
    val categories: List<String> = emptyList(),
    val questions: Int = LettersGame.QUESTIONS,
)

@Serializable
data class ListenFile(
    override val schema: Int,
    val startAt: Map<String, String> = emptyMap(),
    val levels: List<ListenLevelJson>,
) : Versioned

@Serializable
data class ListenLevelJson(
    val id: String,
    val tasks: List<String>,
    val choices: Int = 3,
    val categories: List<String> = emptyList(),
    val firstLettersOnly: Boolean = false,
    val maxNumber: Int = 10,
    val questions: Int = ListenGame.QUESTIONS,
)

@Serializable
data class PatternsFile(
    override val schema: Int,
    val startAt: Map<String, String> = emptyMap(),
    val levels: List<PatternLevelJson>,
) : Versioned

@Serializable
data class PatternLevelJson(
    val id: String,
    val kind: String,
    val rules: List<String>,
    val shown: Int = 4,
    val steps: List<Int> = listOf(1),
    val max: Int = 20,
    val categories: List<String> = emptyList(),
    val choices: Int = 3,
    val questions: Int = PatternGame.QUESTIONS,
)

@Serializable
data class ClockFile(
    override val schema: Int,
    val startAt: Map<String, String> = emptyMap(),
    val levels: List<ClockLevelJson>,
) : Versioned

@Serializable
data class ClockLevelJson(
    val id: String,
    val tasks: List<String>,
    val precision: String,
    val choices: Int = 3,
    val questions: Int = ClockGame.QUESTIONS,
)

@Serializable
data class WordsFile(
    override val schema: Int,
    val startAt: Map<String, String> = emptyMap(),
    val levels: List<WordLevelJson>,
) : Versioned

@Serializable
data class WordLevelJson(
    val id: String,
    val words: Int = 3,
    val minLength: Int = 2,
    val maxLength: Int = 4,
    val categories: List<String> = emptyList(),
    val extraTiles: Int = 0,
    val hint: Boolean = false,
)

@Serializable
data class MemoryFile(
    override val schema: Int,
    val startAt: Map<String, String> = emptyMap(),
    val levels: List<MemoryLevelJson>,
) : Versioned

@Serializable
data class MemoryLevelJson(
    val id: String,
    val pairs: Int,
    val kinds: List<String>,
    val categories: List<String> = emptyList(),
    val maxNumber: Int = 10,
)

/** Letter Tracing, one file per language: `tracing/<language>.json`. */
@Serializable
data class TracingFile(
    override val schema: Int,
    val language: String,
    val startAt: Map<String, String> = emptyMap(),
    val viewBox: List<Float> = listOf(100f, 100f),
    val levels: List<TraceLetterJson>,
) : Versioned

/** Strokes in writing order; each is SVG path data to follow from its start, or a dot [x, y] to tap. */
@Serializable
data class TraceLetterJson(val id: String, val letter: String, val strokes: List<TraceStrokeJson>)

@Serializable
data class TraceStrokeJson(val path: String? = null, val dot: List<Float>? = null)

/** Things with a picture, shared by all languages; and colors. */
@Serializable
data class ConceptsFile(
    override val schema: Int,
    val concepts: List<ConceptJson>,
    val colors: List<ColorJson> = emptyList(),
) : Versioned {
    data class Loaded(val concepts: Map<String, Concept>, val colors: Map<String, Long>)
}

@Serializable
data class ConceptJson(val id: String, val emoji: String = "", val category: String, val image: String? = null)

@Serializable
data class ColorJson(val id: String, val hex: String)

/** One language: its alphabet (each letter points at a concept) and the words for concepts and colors. */
@Serializable
data class LanguageFile(
    override val schema: Int,
    val language: String,
    val letters: List<LetterJson>,
    val words: Map<String, String>,
    val colors: Map<String, String> = emptyMap(),
) : Versioned

@Serializable
data class LetterJson(val letter: String, val name: String, val concept: String)

@Serializable
data class ConnectFile(
    override val schema: Int,
    val startAt: Map<String, String> = emptyMap(),
    val levels: List<ShapeJson>,
) : Versioned

/** Dots are [x, y] or [x, y, curveX, curveY] in 0..1; the curve bends the line arriving at the dot. */
@Serializable
data class ShapeJson(
    val id: String,
    val emoji: String = "",
    val closed: Boolean = true,
    val image: String? = null,
    val dots: List<List<Float>>,
)

@Serializable
data class ColoringFile(
    override val schema: Int,
    val startAt: Map<String, String> = emptyMap(),
    val distractors: List<String> = emptyList(),
    val levels: List<PictureJson>,
) : Versioned

/** Region coordinates are in [viewBox] units: [1, 1] for hand-made pictures, the SVG's size for drawings. */
@Serializable
data class PictureJson(
    val id: String,
    val emoji: String = "",
    val colors: List<String>,
    val extraColors: Int = 0,
    val viewBox: List<Float> = listOf(1f, 1f),
    val regions: List<RegionJson>,
)

/** Exactly one of box [l, t, r, b], oval [cx, cy, rx, ry], poly [x0, y0, …] or SVG path data. */
@Serializable
data class RegionJson(
    val color: Int,
    val box: List<Float>? = null,
    val oval: List<Float>? = null,
    val poly: List<Float>? = null,
    val path: String? = null,
)
