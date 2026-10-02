package com.nourtime.app.core.learning

import com.nourtime.app.core.learning.content.ContentLoader
import java.security.MessageDigest

/**
 * The recorded voice clips behind everything the games say.
 *
 * A clip is the recording of one piece of text, stored as `audio/<language>/<key>.mp3` (see
 * [assetPath]). Words, letters, colors and labels each have their own clip. Numbers up to [WHOLE_NUMBERS]
 * do too; bigger ones are said as two clips back to back: "three hundred" + "forty-five", and in
 * Arabic "ثلاثمئة" + "وخمسة وأربعون" (the "+45" clip, with its و).
 *
 * `tools/audio/generate_audio.py` records the clips listed by [clips]; a unit test checks that every
 * one is in the assets, so the phone's own text-to-speech is only ever a fallback.
 */
object SpeechCatalog {
    const val MAX_NUMBER = 1000
    private const val WHOLE_NUMBERS = 100

    /** The clips that say [text], in order; null when nothing recorded can say it. */
    fun clipsFor(text: String, language: LearnLanguage): List<String>? {
        val n = text.toIntOrNull() ?: return listOf(text)
        if (n < 0 || n > MAX_NUMBER) return null
        if (n <= WHOLE_NUMBERS || n % 100 == 0) return listOf(n.toString())
        val rest = n % 100
        return listOf((n - rest).toString(), if (language == LearnLanguage.ARABIC) "+$rest" else rest.toString())
    }

    /** Every clip the games can ask for in [language]. */
    fun clips(loader: ContentLoader, language: LearnLanguage): Set<String> {
        val out = linkedSetOf<String>()
        val pack = loader.letters(language)
        pack.letters.forEach {
            out += it.name
            out += pack.speech(it)
            out += it.letter
        }
        pack.words.forEach { w ->
            out += w.word
            // Word Builder says each tile as it's placed.
            w.word.filter { !it.isWhitespace() }.forEach { out += it.toString() }
        }
        pack.colors.forEach { out += it.name }
        loader.sorting().levels.forEach { level ->
            level.bins.forEach { bin -> (bin.labels[language.tag] ?: bin.labels["en"])?.let { out += it } }
        }
        (0..MAX_NUMBER).forEach { n -> out += clipsFor(n.toString(), language)!! }
        return out
    }

    /** The asset that holds [clip]: `audio/ar/3f2a…mp3`. */
    fun assetPath(clip: String, language: LearnLanguage): String = "audio/${language.tag}/${key(clip)}.mp3"

    /** A file name for any text: the first 16 hex digits of its SHA-1 (the generator does the same). */
    fun key(clip: String): String =
        MessageDigest.getInstance("SHA-1").digest(clip.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
}
