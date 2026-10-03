package com.nourtime.app.core.learning

/**
 * A recorded voice for one language: its clips live in `assets/audio/<dir>/` (see [SpeechCatalog]).
 * The first pack of each language is its default and has every clip; another pack may lack some,
 * and those clips are played from the default instead.
 *
 * The Egyptian pack says numbers the Egyptian way (تلاتة، حداشر); its words and letters are the
 * written Fusha words, so what the child hears matches the screen.
 */
enum class VoicePack(val language: LearnLanguage, val dir: String) {
    ARABIC_FUSHA(LearnLanguage.ARABIC, "ar"),
    ARABIC_EGYPTIAN(LearnLanguage.ARABIC, "ar-eg"),
    ENGLISH_AMERICAN(LearnLanguage.ENGLISH, "en"),
    ;

    val isDefault: Boolean get() = this == defaultFor(language)

    companion object {
        fun defaultFor(language: LearnLanguage): VoicePack = entries.first { it.language == language }

        fun of(language: LearnLanguage): List<VoicePack> = entries.filter { it.language == language }

        /** The parent's choice for [language], or its default. */
        fun chosen(choices: Map<LearnLanguage, VoicePack>, language: LearnLanguage): VoicePack =
            choices[language]?.takeIf { it.language == language } ?: defaultFor(language)
    }
}
