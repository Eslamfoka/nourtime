package com.nourtime.app.core.locale

import java.util.Locale

/** The app's own language choice, independent of the phone's. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ARABIC("ar"),
    ENGLISH("en");

    /** The locale to show: the phone's when following it. */
    fun localeOr(phone: Locale): Locale = if (this == SYSTEM) phone else Locale.forLanguageTag(tag)

    companion object {
        /** From a stored or system tag ("ar-EG" counts as Arabic); anything else follows the phone. */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it).language } ?: return SYSTEM
            return entries.firstOrNull { it != SYSTEM && it.tag == language } ?: SYSTEM
        }
    }
}
