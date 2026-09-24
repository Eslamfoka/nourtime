package com.nourtime.app.core.security

object SecurityQuestionValidator {
    const val MIN_QUESTION_LENGTH = 5
    const val MIN_ANSWER_LENGTH = 2

    enum class Error { QUESTION_TOO_SHORT, ANSWER_TOO_SHORT, ANSWER_MISMATCH }

    fun validate(question: String, answer: String, confirmation: String): Set<Error> = buildSet {
        if (question.trim().length < MIN_QUESTION_LENGTH) add(Error.QUESTION_TOO_SHORT)
        val normalized = AnswerNormalizer.normalize(answer)
        if (normalized.length < MIN_ANSWER_LENGTH) {
            add(Error.ANSWER_TOO_SHORT)
        } else if (normalized != AnswerNormalizer.normalize(confirmation)) {
            add(Error.ANSWER_MISMATCH)
        }
    }
}
