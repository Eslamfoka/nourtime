package com.nourtime.app.core.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    @Test
    fun `tags for the stored choice`() {
        assertEquals("", AppLanguage.SYSTEM.tag)
        assertEquals("ar", AppLanguage.ARABIC.tag)
        assertEquals("en", AppLanguage.ENGLISH.tag)
    }

    @Test
    fun `reads a stored or system tag back, including regional ones`() {
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromTag("ar-EG"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        // A language the app doesn't have follows the phone.
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("fr"))
    }

    @Test
    fun `the locale to use keeps the phone's when following it`() {
        val phone = Locale.forLanguageTag("ar-EG")
        assertEquals(phone, AppLanguage.SYSTEM.localeOr(phone))
        assertEquals("en", AppLanguage.ENGLISH.localeOr(phone).language)
        assertEquals("ar", AppLanguage.ARABIC.localeOr(Locale.US).language)
    }
}
