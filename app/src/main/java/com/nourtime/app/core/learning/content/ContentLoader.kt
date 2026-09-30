package com.nourtime.app.core.learning.content

import com.nourtime.app.core.learning.Area
import com.nourtime.app.core.learning.ClockGame
import com.nourtime.app.core.learning.ClockLevel
import com.nourtime.app.core.learning.ClockPrecision
import com.nourtime.app.core.learning.ColorEntry
import com.nourtime.app.core.learning.ColoringPack
import com.nourtime.app.core.learning.ColoringPicture
import com.nourtime.app.core.learning.ColoringRules
import com.nourtime.app.core.learning.Concept
import com.nourtime.app.core.learning.ConnectRules
import com.nourtime.app.core.learning.Dot
import com.nourtime.app.core.learning.DotShape
import com.nourtime.app.core.learning.GamePack
import com.nourtime.app.core.learning.LearnLanguage
import com.nourtime.app.core.learning.LetterEntry
import com.nourtime.app.core.learning.LettersGame
import com.nourtime.app.core.learning.LettersLanguagePack
import com.nourtime.app.core.learning.LettersLevel
import com.nourtime.app.core.learning.Level
import com.nourtime.app.core.learning.ListenLevel
import com.nourtime.app.core.learning.LevelIds
import com.nourtime.app.core.learning.MathLevel
import com.nourtime.app.core.learning.MathOp
import com.nourtime.app.core.learning.MemoryLevel
import com.nourtime.app.core.learning.PairKind
import com.nourtime.app.core.learning.PatternGame
import com.nourtime.app.core.learning.PatternKind
import com.nourtime.app.core.learning.PatternLevel
import com.nourtime.app.core.learning.Region
import com.nourtime.app.core.learning.SvgPath
import com.nourtime.app.core.learning.Task
import com.nourtime.app.core.learning.TraceLetter
import com.nourtime.app.core.learning.TraceRules
import com.nourtime.app.core.learning.TraceStroke
import com.nourtime.app.core.learning.WordLevel
import com.nourtime.app.core.learning.WordEntry
import com.nourtime.app.data.settings.AgeGroup
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot

/** Reads a content file by its path under the content root (`assets/learning/` in the app). */
fun interface ContentFiles {
    fun read(path: String): String?
}

/**
 * Loads the Learning Hub's content packs (JSON) and turns them into game objects. Every item is
 * checked; a broken item is left out and reported instead of crashing, so one typo in a pack can't
 * take a game away from a child. Packs are parsed once and kept (they're small and read-only).
 *
 * Blocking: call it off the main thread. The unit tests run [checkAll] over the real packs, so a
 * broken pack fails the build long before it reaches a phone.
 */
class ContentLoader(private val files: ContentFiles, private val report: (String) -> Unit = {}) {

    private val json = Json {
        // New fields in future packs must not break older app versions.
        ignoreUnknownKeys = true
    }
    private val cache = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cached(key: String, load: () -> T): T = cache.getOrPut(key) { load() } as T

    fun math(): GamePack<MathLevel> = cached("math") {
        val file = parse<MathFile>(MATH) ?: return@cached GamePack(emptyList())
        pack(MATH, file.levels.mapNotNull { mathLevel(it) }, file.startAt)
    }

    fun letterLevels(): GamePack<LettersLevel> = cached("letters-levels") {
        val file = parse<LettersLevelsFile>(LETTER_LEVELS) ?: return@cached GamePack(emptyList())
        pack(LETTER_LEVELS, file.levels.mapNotNull { lettersLevel(it) }, file.startAt)
    }

    fun listen(): GamePack<ListenLevel> = cached("listen") {
        val file = parse<ListenFile>(LISTEN) ?: return@cached GamePack(emptyList())
        pack(LISTEN, file.levels.mapNotNull { listenLevel(it) }, file.startAt)
    }

    fun patterns(): GamePack<PatternLevel> = cached("patterns") {
        val file = parse<PatternsFile>(PATTERNS) ?: return@cached GamePack(emptyList())
        pack(PATTERNS, file.levels.mapNotNull { patternLevel(it) }, file.startAt)
    }

    fun clock(): GamePack<ClockLevel> = cached("clock") {
        val file = parse<ClockFile>(CLOCK) ?: return@cached GamePack(emptyList())
        pack(CLOCK, file.levels.mapNotNull { clockLevel(it) }, file.startAt)
    }

    fun tracing(language: LearnLanguage): GamePack<TraceLetter> = cached("tracing-${language.tag}") {
        val path = "tracing/${language.tag}.json"
        val file = parse<TracingFile>(path) ?: return@cached GamePack(emptyList())
        if (file.language != language.tag) return@cached GamePack<TraceLetter>(emptyList()).also { problem(path, "-", "language is ${file.language}") }
        val (vw, vh) = file.viewBox.takeIf { it.size == 2 && it.all { v -> v > 0 } }?.let { it[0] to it[1] }
            ?: return@cached GamePack<TraceLetter>(emptyList()).also { problem(path, "-", "viewBox is [width, height]") }
        pack(path, file.levels.mapNotNull { traceLetter(path, it, vw, vh) }, file.startAt)
    }

    fun words(): GamePack<WordLevel> = cached("words") {
        val file = parse<WordsFile>(WORDS) ?: return@cached GamePack(emptyList())
        pack(WORDS, file.levels.mapNotNull { wordLevel(it) }, file.startAt)
    }

    fun memory(): GamePack<MemoryLevel> = cached("memory") {
        val file = parse<MemoryFile>(MEMORY) ?: return@cached GamePack(emptyList())
        pack(MEMORY, file.levels.mapNotNull { memoryLevel(it) }, file.startAt)
    }

    fun letters(language: LearnLanguage): LettersLanguagePack = cached("letters-${language.tag}") {
        lettersLanguage(language)
    }

    fun connect(): GamePack<DotShape> = cached("connect") {
        val file = parse<ConnectFile>(CONNECT) ?: return@cached GamePack(emptyList())
        pack(CONNECT, file.levels.mapNotNull { shape(it) }, file.startAt)
    }

    fun coloring(): ColoringPack = cached("coloring") {
        val file = parse<ColoringFile>(COLORING) ?: return@cached ColoringPack(GamePack(emptyList()), emptyList())
        ColoringPack(
            pack(COLORING, file.levels.mapNotNull { picture(it) }, file.startAt),
            file.distractors.mapNotNull { color(COLORING, "distractor", it) },
        )
    }

    fun concepts(): ConceptsFile.Loaded = cached("concepts") {
        val file = parse<ConceptsFile>(CONCEPTS) ?: return@cached ConceptsFile.Loaded(emptyMap(), emptyMap())
        val concepts = linkedMapOf<String, Concept>()
        val emojis = hashSetOf<String>()
        file.concepts.forEach { c ->
            when {
                !LevelIds.valid(c.id) -> problem(CONCEPTS, c.id, "bad id")
                c.id in concepts -> problem(CONCEPTS, c.id, "duplicate id")
                c.emoji.isBlank() && c.image == null -> problem(CONCEPTS, c.id, "needs an emoji or an image")
                // Two things with the same picture would make a question with two right answers.
                c.emoji.isNotBlank() && !emojis.add(c.emoji) -> problem(CONCEPTS, c.id, "same emoji as another concept: ${c.emoji}")
                else -> concepts[c.id] = Concept(c.id, c.emoji, c.category, c.image)
            }
        }
        val colors = linkedMapOf<String, Long>()
        file.colors.forEach { c ->
            val argb = color(CONCEPTS, c.id, c.hex) ?: return@forEach
            if (c.id in colors || argb in colors.values) problem(CONCEPTS, c.id, "duplicate color") else colors[c.id] = argb
        }
        ConceptsFile.Loaded(concepts, colors)
    }

    /** Loads everything and returns every problem found, including the slower [deep] checks. */
    fun checkAll(deep: Boolean = true): List<String> {
        val found = mutableListOf<String>()
        val checker = ContentLoader(files) { found += it }
        checker.math()
        checker.letterLevels()
        checker.listen()
        checker.patterns()
        checker.clock()
        checker.memory()
        checker.words()
        LearnLanguage.entries.forEach { checker.tracing(it) }
        LearnLanguage.entries.forEach { checker.letters(it) }
        checker.connect()
        val coloring = checker.coloring()
        if (deep) {
            // Every region must be tappable somewhere (not fully hidden under later regions).
            coloring.pack.levels.forEach { p ->
                val hit = mutableSetOf<Int>()
                for (i in 0..120) for (j in 0..120) ColoringRules.regionAt(p, i / 120f, j / 120f)?.let(hit::add)
                p.regions.indices.filter { it !in hit }.forEach { found += "$COLORING ${p.id}: region $it can't be tapped" }
            }
        }
        return found
    }

    // --- parsing ---

    private inline fun <reified T : Versioned> parse(path: String): T? {
        val text = files.read(path) ?: run {
            problem(path, "-", "file missing")
            return null
        }
        val file = runCatching { json.decodeFromString<T>(text) }.getOrElse {
            problem(path, "-", "not valid: ${it.message?.lineSequence()?.firstOrNull()}")
            return null
        }
        if (file.schema != SCHEMA) {
            problem(path, "-", "schema ${file.schema}, this app reads $SCHEMA")
            return null
        }
        return file
    }

    private fun problem(path: String, id: String, message: String) = report("$path $id: $message")

    private fun <T : Level> pack(path: String, levels: List<T>, startAt: Map<String, String>): GamePack<T> {
        val unique = mutableListOf<T>()
        levels.forEach { level ->
            if (unique.any { it.id == level.id }) problem(path, level.id, "duplicate id") else unique += level
        }
        val ages = startAt.mapNotNull { (age, id) ->
            val group = AgeGroup.entries.firstOrNull { it.name == age }
            when {
                group == null -> null.also { problem(path, age, "unknown age group") }
                unique.none { it.id == id } -> null.also { problem(path, age, "start level $id doesn't exist") }
                else -> group to id
            }
        }.toMap()
        return GamePack(unique, ages)
    }

    private fun checkId(path: String, id: String): Boolean =
        LevelIds.valid(id).also { if (!it) problem(path, id, "bad id (use a-z, 0-9, - and _)") }

    private fun mathLevel(l: MathLevelJson): MathLevel? {
        if (!checkId(MATH, l.id)) return null
        val ops = l.ops.map { key -> MathOp.entries.firstOrNull { it.key == key } ?: return null.also { problem(MATH, l.id, "unknown op $key") } }
        val bad = when {
            ops.isEmpty() -> "no ops"
            l.choices !in 2..4 -> "choices must be 2..4"
            MathOp.COMPARE in ops && l.choices != 3 && ops.size == 1 -> "compare levels have 3 choices (< = >)"
            ops.any { it.additive } && l.max < 2 -> "max must be at least 2"
            ops.any { it.additive } && (l.min < 1 || l.max < 2 * l.min + 1) -> "min must be at least 1 and max at least 2 × min + 1"
            ops.any { it.multiplicative } && (l.factor < 2 || l.minFactor !in 1..l.factor) -> "factor must be at least 2 and minFactor 1..factor"
            l.questions !in 1..30 -> "questions must be 1..30"
            else -> null
        }
        if (bad != null) return null.also { problem(MATH, l.id, bad) }
        return MathLevel(l.id, ops, l.max, l.min, l.minFactor, l.factor, l.dots, l.choices, l.questions)
    }

    private fun lettersLevel(l: LettersLevelJson): LettersLevel? {
        if (!checkId(LETTER_LEVELS, l.id)) return null
        val tasks = l.tasks.map { key ->
            LettersGame.TASKS.firstOrNull { it.name.equals(key, ignoreCase = true) }
                ?: return null.also { problem(LETTER_LEVELS, l.id, "unknown task $key") }
        }
        if (tasks.isEmpty()) return null.also { problem(LETTER_LEVELS, l.id, "no tasks") }
        if (l.choices !in 2..4) return null.also { problem(LETTER_LEVELS, l.id, "choices must be 2..4") }
        if (l.questions !in 1..30) return null.also { problem(LETTER_LEVELS, l.id, "questions must be 1..30") }
        return LettersLevel(l.id, tasks, l.choices, l.firstLettersOnly, l.categories.toSet(), l.questions)
    }

    private fun listenLevel(l: ListenLevelJson): ListenLevel? {
        if (!checkId(LISTEN, l.id)) return null
        val tasks = l.tasks.map { key ->
            Task.entries.firstOrNull { it.listening && it.name.equals(key, ignoreCase = true) }
                ?: return null.also { problem(LISTEN, l.id, "unknown task $key") }
        }
        val bad = when {
            tasks.isEmpty() -> "no tasks"
            l.choices !in 2..4 -> "choices must be 2..4"
            l.questions !in 1..30 -> "questions must be 1..30"
            Task.LISTEN_TO_NUMBER in tasks && l.maxNumber !in l.choices - 1..1000 -> "maxNumber must be choices - 1..1000"
            else -> null
        }
        if (bad != null) return null.also { problem(LISTEN, l.id, bad) }
        return ListenLevel(l.id, tasks, l.choices, l.categories.toSet(), l.firstLettersOnly, l.maxNumber, l.questions)
    }

    private fun patternLevel(l: PatternLevelJson): PatternLevel? {
        if (!checkId(PATTERNS, l.id)) return null
        val kind = PatternKind.entries.firstOrNull { it.key == l.kind } ?: return null.also { problem(PATTERNS, l.id, "unknown kind ${l.kind}") }
        val numbers = kind == PatternKind.NUMBERS
        val allowed = if (numbers) PatternGame.NUMBER_RULES else PatternGame.UNITS
        val badRule = l.rules.firstOrNull { it !in allowed }
        if (badRule != null) return null.also { problem(PATTERNS, l.id, "rule $badRule doesn't fit ${l.kind}") }
        val bad = when {
            l.rules.isEmpty() -> "no rules"
            l.choices !in 2..4 -> "choices must be 2..4"
            l.shown !in 3..6 -> "shown must be 3..6"
            l.questions !in 1..30 -> "questions must be 1..30"
            numbers && "step" in l.rules && (l.steps.isEmpty() || 0 in l.steps) -> "steps must be set and not 0"
            numbers && "step" in l.rules && l.steps.any { kotlin.math.abs(it) * l.shown > l.max } -> "max is too small for the steps"
            numbers && "double" in l.rules && l.max < (1 shl l.shown) -> "max must be at least 2^shown for double"
            else -> null
        }
        if (bad != null) return null.also { problem(PATTERNS, l.id, bad) }
        return PatternLevel(l.id, kind, l.rules, l.shown, l.steps, l.max, l.categories.toSet(), l.choices, l.questions)
    }

    private fun clockLevel(l: ClockLevelJson): ClockLevel? {
        if (!checkId(CLOCK, l.id)) return null
        val tasks = l.tasks.map { key ->
            ClockGame.TASKS.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: return null.also { problem(CLOCK, l.id, "unknown task $key") }
        }
        val precision = ClockPrecision.entries.firstOrNull { it.key == l.precision } ?: return null.also { problem(CLOCK, l.id, "unknown precision ${l.precision}") }
        val bad = when {
            tasks.isEmpty() -> "no tasks"
            l.choices !in 2..4 -> "choices must be 2..4"
            l.questions !in 1..30 -> "questions must be 1..30"
            else -> null
        }
        if (bad != null) return null.also { problem(CLOCK, l.id, bad) }
        return ClockLevel(l.id, tasks, precision, l.choices, l.questions)
    }

    private fun wordLevel(l: WordLevelJson): WordLevel? {
        if (!checkId(WORDS, l.id)) return null
        val bad = when {
            l.words !in 1..10 -> "words must be 1..10"
            l.minLength < 2 || l.maxLength !in l.minLength..10 -> "lengths must be 2 <= minLength <= maxLength <= 10"
            l.extraTiles !in 0..6 -> "extraTiles must be 0..6"
            else -> null
        }
        if (bad != null) return null.also { problem(WORDS, l.id, bad) }
        return WordLevel(l.id, l.words, l.minLength, l.maxLength, l.categories.toSet(), l.extraTiles, l.hint)
    }

    private fun memoryLevel(l: MemoryLevelJson): MemoryLevel? {
        if (!checkId(MEMORY, l.id)) return null
        val kinds = l.kinds.map { key -> PairKind.entries.firstOrNull { it.key == key } ?: return null.also { problem(MEMORY, l.id, "unknown kind $key") } }
        val bad = when {
            kinds.isEmpty() -> "no kinds"
            l.pairs !in 2..8 -> "pairs must be 2..8"
            PairKind.NUMBER_DOTS in kinds && l.maxNumber !in l.pairs..20 -> "maxNumber must be pairs..20"
            else -> null
        }
        if (bad != null) return null.also { problem(MEMORY, l.id, bad) }
        return MemoryLevel(l.id, l.pairs, kinds, l.categories.toSet(), l.maxNumber)
    }

    private fun traceLetter(path: String, l: TraceLetterJson, vw: Float, vh: Float): TraceLetter? {
        if (!checkId(path, l.id)) return null
        if (l.letter.isBlank() || l.strokes.isEmpty()) return null.also { problem(path, l.id, "needs a letter and strokes") }
        val strokes = l.strokes.mapIndexed { i, s ->
            val stroke = when {
                s.dot?.size == 2 -> TraceStroke(listOf(Dot(s.dot[0] / vw, s.dot[1] / vh)), dot = true)
                s.path != null -> runCatching { SvgPath.flatten(s.path, vw, vh, minPoints = 2) }.getOrNull()
                    ?.singleOrNull()
                    ?.let { f -> TraceStroke(TraceRules.resample(List(f.size / 2) { Dot(f[2 * it], f[2 * it + 1]) })) }
                else -> null
            } ?: return null.also { problem(path, l.id, "stroke ${i + 1} needs one path (a single line, no M in the middle) or a dot [x, y]") }
            if (stroke.points.any { it.x !in 0f..1f || it.y !in 0f..1f }) return null.also { problem(path, l.id, "stroke ${i + 1} leaves the canvas") }
            if (!stroke.dot && stroke.points.size < 3) return null.also { problem(path, l.id, "stroke ${i + 1} is too short") }
            stroke
        }
        return TraceLetter(l.id, l.letter.trim(), strokes)
    }

    private fun lettersLanguage(language: LearnLanguage): LettersLanguagePack {
        val path = "letters/${language.tag}.json"
        val empty = LettersLanguagePack(language, emptyList(), emptyList(), emptyList())
        val file = parse<LanguageFile>(path) ?: return empty
        if (file.language != language.tag) return empty.also { problem(path, "-", "language is ${file.language}") }
        val (concepts, colors) = concepts()
        val words = file.words.mapNotNull { (id, text) ->
            val c = concepts[id] ?: return@mapNotNull null.also { problem(path, id, "no such concept") }
            if (text.isBlank()) return@mapNotNull null.also { problem(path, id, "empty word") }
            WordEntry(id, text.trim(), c.emoji, c.category, c.image)
        }
        if (words.map { it.word }.toSet().size != words.size) problem(path, "-", "two concepts share a word")
        val byConcept = words.associateBy { it.concept }
        val letters = file.letters.mapNotNull { l ->
            val w = byConcept[l.concept] ?: return@mapNotNull null.also { problem(path, l.letter, "word for ${l.concept} missing") }
            if (!startsWith(w.word, l.letter, language)) return@mapNotNull null.also { problem(path, l.letter, "${w.word} doesn't start with ${l.letter}") }
            LetterEntry(l.letter, l.name, w.word, w.emoji, w.image)
        }
        if (letters.map { it.letter }.toSet().size != letters.size) problem(path, "-", "a letter appears twice")
        if (letters.map { it.emoji }.toSet().size != letters.size) problem(path, "-", "two letters use the same picture")
        val colorEntries = file.colors.mapNotNull { (id, name) ->
            val argb = colors[id] ?: return@mapNotNull null.also { problem(path, id, "no such color") }
            ColorEntry(id, name, argb)
        }
        return LettersLanguagePack(language, letters, words, colorEntries)
    }

    private fun shape(s: ShapeJson): DotShape? {
        if (!checkId(CONNECT, s.id)) return null
        val dots = s.dots.map { d ->
            when (d.size) {
                2 -> Dot(d[0], d[1])
                4 -> Dot(d[0], d[1], d[2], d[3])
                else -> return null.also { problem(CONNECT, s.id, "a dot is [x, y] or [x, y, curveX, curveY]") }
            }
        }
        val bad = when {
            dots.size < 3 -> "needs at least 3 dots"
            dots.any { it.x !in 0f..1f || it.y !in 0f..1f } -> "dots must be inside 0..1"
            else -> null
        }
        if (bad != null) return null.also { problem(CONNECT, s.id, bad) }
        // A finger must not be able to touch two dots at once.
        val minGap = 2 * ConnectRules.radiusFor(young = false)
        for (i in dots.indices) for (j in i + 1 until dots.size) {
            if (hypot(dots[i].x - dots[j].x, dots[i].y - dots[j].y) <= minGap) {
                return null.also { problem(CONNECT, s.id, "dots ${i + 1} and ${j + 1} are too close") }
            }
        }
        return DotShape(s.id, s.emoji, dots, s.closed, s.image)
    }

    private fun picture(p: PictureJson): ColoringPicture? {
        if (!checkId(COLORING, p.id)) return null
        val colors = p.colors.map { color(COLORING, p.id, it) ?: return null }
        if (colors.isEmpty() || colors.toSet().size != colors.size) return null.also { problem(COLORING, p.id, "colors must be set and different") }
        val (vw, vh) = p.viewBox.takeIf { it.size == 2 && it.all { v -> v > 0 } }?.let { it[0] to it[1] }
            ?: return null.also { problem(COLORING, p.id, "viewBox is [width, height]") }
        val regions = p.regions.mapIndexed { i, r ->
            if (r.color !in colors.indices) return null.also { problem(COLORING, p.id, "region $i uses color ${r.color}") }
            val area = area(r, vw, vh) ?: return null.also { problem(COLORING, p.id, "region $i needs one of box, oval, poly, path") }
            Region(area, r.color)
        }
        if (regions.isEmpty()) return null.also { problem(COLORING, p.id, "no regions") }
        if (regions.map { it.color }.toSet().size != colors.size) problem(COLORING, p.id, "a color isn't used by any region")
        return ColoringPicture(p.id, p.emoji, colors, regions, p.extraColors.coerceIn(0, 4))
    }

    private fun area(r: RegionJson, vw: Float, vh: Float): Area? {
        fun List<Float>.scaled() = mapIndexed { i, v -> if (i % 2 == 0) v / vw else v / vh }
        return when {
            r.box?.size == 4 -> r.box.scaled().let { Area.Box(it[0], it[1], it[2], it[3]) }
            r.oval?.size == 4 -> r.oval.let { Area.Oval(it[0] / vw, it[1] / vh, it[2] / vw, it[3] / vh) }
            r.poly != null && r.poly.size >= 6 && r.poly.size % 2 == 0 -> Area.Poly(*r.poly.scaled().toFloatArray())
            r.path != null -> runCatching { Area.Svg(r.path, vw, vh) }.getOrNull()?.takeIf { it.polygons.isNotEmpty() }
            else -> null
        }
    }

    private fun color(path: String, id: String, hex: String): Long? {
        val v = hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)
        return if (v == null) null.also { problem(path, id, "color $hex isn't #RRGGBB") } else 0xFF000000 or v
    }

    companion object {
        const val SCHEMA = 1
        const val MATH = "math/levels.json"
        const val LETTER_LEVELS = "letters/levels.json"
        const val LISTEN = "listen/levels.json"
        const val PATTERNS = "patterns/levels.json"
        const val CLOCK = "clock/levels.json"
        const val MEMORY = "memory/levels.json"
        const val WORDS = "words/levels.json"
        fun tracingPath(language: LearnLanguage) = "tracing/${language.tag}.json"
        const val CONCEPTS = "concepts.json"
        const val CONNECT = "connect/shapes.json"
        const val COLORING = "coloring/pictures.json"

        /** Arabic alef forms count as one letter; English ignores case. */
        fun startsWith(word: String, letter: String, language: LearnLanguage): Boolean {
            if (language == LearnLanguage.ENGLISH) return word.startsWith(letter, ignoreCase = true)
            val alef = "اأإآ"
            val first = word.firstOrNull() ?: return false
            val l = letter.firstOrNull() ?: return false
            return first == l || (first in alef && l in alef) || (l == 'ه' && first == 'ه')
        }
    }
}
