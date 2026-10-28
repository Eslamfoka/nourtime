package com.nourtime.app.core.security

import com.nourtime.app.core.security.SecurityQuestionValidator.Error
import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityQuestionValidatorTest {

    @Test
    fun `valid form has no errors`() {
        assertEquals(emptySet<Error>(), SecurityQuestionValidator.validate("اسم قرية جدتي؟", "أبو حمص", "ابوحمص"))
    }

    @Test
    fun `question must be long enough`() {
        assertEquals(setOf(Error.QUESTION_TOO_SHORT), SecurityQuestionValidator.validate(" ؟ ", "نورة", "نورة"))
    }

    @Test
    fun `answer must be long enough after normalization`() {
        assertEquals(setOf(Error.ANSWER_TOO_SHORT), SecurityQuestionValidator.validate("My question", " a ", " a "))
        // Spaces don't count: "a b c" is 3 letters.
        assertEquals(setOf(Error.ANSWER_TOO_SHORT), SecurityQuestionValidator.validate("My question", "a b c", "abc"))
        assertEquals(emptySet<Error>(), SecurityQuestionValidator.validate("My question", "a b c d", "abcd"))
    }

    @Test
    fun `an answer of 2 or 3 letters, enough before, is refused for a new answer`() {
        assertEquals(setOf(Error.ANSWER_TOO_SHORT), SecurityQuestionValidator.validate("My question", "Al", "Al"))
        assertEquals(setOf(Error.ANSWER_TOO_SHORT), SecurityQuestionValidator.validate("My question", "Sam", "Sam"))
    }

    @Test
    fun `Arabic letters count one each, and diacritics and tatweel don't count`() {
        assertEquals(setOf(Error.ANSWER_TOO_SHORT), SecurityQuestionValidator.validate("سؤالي هنا", "نور", "نور"))
        // "نُوـر": a damma and a tatweel, still 3 letters.
        assertEquals(setOf(Error.ANSWER_TOO_SHORT), SecurityQuestionValidator.validate("سؤالي هنا", "نُوـر", "نور"))
        assertEquals(emptySet<Error>(), SecurityQuestionValidator.validate("سؤالي هنا", "نورة", "نوره"))
    }

    @Test
    fun `confirmation must match after normalization`() {
        assertEquals(setOf(Error.ANSWER_MISMATCH), SecurityQuestionValidator.validate("My question", "Blue", "Green"))
    }
}
