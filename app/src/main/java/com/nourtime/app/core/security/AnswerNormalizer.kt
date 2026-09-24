package com.nourtime.app.core.security

import java.text.Normalizer

/**
 * Normalizes a security answer before hashing so small typing differences don't matter:
 * whitespace, letter case, Arabic diacritics and tatweel, hamza/alef forms (أ إ آ ٱ ا),
 * ى/ي, ة/ه, and Arabic-Indic digits.
 */
object AnswerNormalizer {

    fun normalize(input: String): String {
        val text = Normalizer.normalize(input, Normalizer.Form.NFKC).lowercase()
        val out = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch.isWhitespace() -> Unit
                ch in 'ً'..'ٟ' || ch == 'ٰ' || ch == 'ـ' -> Unit // diacritics, tatweel
                ch == 'ء' -> Unit
                ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> out.append('ا')
                ch == 'ؤ' -> out.append('و')
                ch == 'ئ' || ch == 'ى' -> out.append('ي')
                ch == 'ة' -> out.append('ه')
                ch in '٠'..'٩' -> out.append('0' + (ch - '٠'))
                ch in '۰'..'۹' -> out.append('0' + (ch - '۰'))
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}
