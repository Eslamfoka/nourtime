package com.nourtime.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnswerNormalizerTest {

    private fun same(a: String, b: String) =
        assertEquals(AnswerNormalizer.normalize(a), AnswerNormalizer.normalize(b))

    @Test
    fun `ignores spaces and letter case`() {
        same("  Blue Sky ", "bluesky")
        same("Cairo\tTower", "CAIROTOWER")
    }

    @Test
    fun `treats alef and hamza forms as equal`() {
        same("أحمد", "احمد")
        same("إسلام", "اسلام")
        same("آمنة", "امنه")
        same("مؤمن", "مومن")
        same("سماء", "سما")
        same("هانئ", "هاني")
    }

    @Test
    fun `treats alef maqsura and taa marbuta variants as equal`() {
        same("مصطفى", "مصطفي")
        same("مدرسة", "مدرسه")
    }

    @Test
    fun `ignores diacritics and tatweel`() {
        same("مُحَمَّد", "محمد")
        same("نـــور", "نور")
    }

    @Test
    fun `maps arabic-indic digits to latin`() {
        same("٢٠١٩", "2019")
        same("۲۰۱۹", "2019")
    }

    @Test
    fun `different answers stay different`() {
        assertNotEquals(AnswerNormalizer.normalize("نور"), AnswerNormalizer.normalize("نار"))
    }
}
