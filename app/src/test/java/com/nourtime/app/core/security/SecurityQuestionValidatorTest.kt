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
        assertEquals(setOf(Error.QUESTION_TOO_SHORT), SecurityQuestionValidator.validate(" ؟ ", "نور", "نور"))
    }

    @Test
    fun `answer must be long enough after normalization`() {
        assertEquals(setOf(Error.ANSWER_TOO_SHORT), SecurityQuestionValidator.validate("My question", " a ", " a "))
    }

    @Test
    fun `confirmation must match after normalization`() {
        assertEquals(setOf(Error.ANSWER_MISMATCH), SecurityQuestionValidator.validate("My question", "Blue", "Green"))
    }
}
