package com.nourtime.app.core.learning.content

import com.nourtime.app.core.learning.Area
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
import com.nourtime.app.core.learning.LettersLanguagePack
import com.nourtime.app.core.learning.LettersLevel
import com.nourtime.app.core.learning.Level
import com.nourtime.app.core.learning.LevelIds
import com.nourtime.app.core.learning.MathLevel
import com.nourtime.app.core.learning.MathOp
import com.nourtime.app.core.learning.Region
import com.nourtime.app.core.learning.Task
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
            Task.entries.firstOrNull { it.name.equals(key, ignoreCase = true) && it != Task.SOLVE && it != Task.COMPARE }
                ?: return null.also { problem(LETTER_LEVELS, l.id, "unknown task $key") }
        }
        if (tasks.isEmpty()) return null.also { problem(LETTER_LEVELS, l.id, "no tasks") }
        if (l.choices !in 2..4) return null.also { problem(LETTER_LEVELS, l.id, "choices must be 2..4") }
        if (l.questions !in 1..30) return null.also { problem(LETTER_LEVELS, l.id, "questions must be 1..30") }
        return LettersLevel(l.id, tasks, l.choices, l.firstLettersOnly, l.categories.toSet(), l.questions)
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
