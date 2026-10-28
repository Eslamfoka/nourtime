package com.nourtime.app.core.security

/**
 * Checks a security question and answer when they are set or changed. Only then: an answer saved
 * under an older, shorter minimum keeps working ([com.nourtime.app.data.security.SecurityRepository.checkAnswer]
 * never looks at the length), so no parent is locked out by a stricter rule.
 */
object SecurityQuestionValidator {
    const val MIN_QUESTION_LENGTH = 5

    /**
     * Counted after [AnswerNormalizer] (no spaces, one form of each letter), in characters, so an
     * Arabic answer counts its letters like any other. Was 2 until 2026-10-28: with 3 free tries and then
     * 1 a day, a child who knows the family could guess a 2-letter answer slowly.
     */
    const val MIN_ANSWER_LENGTH = 4

    enum class Error { QUESTION_TOO_SHORT, ANSWER_TOO_SHORT, ANSWER_MISMATCH }

    fun validate(question: String, answer: String, confirmation: String): Set<Error> = buildSet {
        if (question.trim().length < MIN_QUESTION_LENGTH) add(Error.QUESTION_TOO_SHORT)
        val normalized = AnswerNormalizer.normalize(answer)
        if (normalized.codePointCount(0, normalized.length) < MIN_ANSWER_LENGTH) {
            add(Error.ANSWER_TOO_SHORT)
        } else if (normalized != AnswerNormalizer.normalize(confirmation)) {
            add(Error.ANSWER_MISMATCH)
        }
    }
}
