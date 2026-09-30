package com.nourtime.app.core.learning

/**
 * One level being played. A wrong pick marks that choice and the child tries again, so every
 * level ends on success; stars come from first-try answers. The tutorial question (the first one,
 * the first time a game is opened) doesn't count.
 */
data class Round(
    val questions: List<Question>,
    val tutorial: Boolean = false,
    val index: Int = 0,
    val firstTry: Int = 0,
    /** Choices already picked wrongly on the current question. */
    val wrong: Set<Int> = emptySet(),
) {
    init {
        require(questions.isNotEmpty())
    }

    val done: Boolean get() = index >= questions.size
    val current: Question? get() = questions.getOrNull(index)
    val isTutorialQuestion: Boolean get() = tutorial && index == 0

    /** Questions that count toward the stars. */
    val scored: Int get() = if (tutorial) questions.size - 1 else questions.size

    val stars: Int get() = Stars.of(firstTry, scored)

    enum class Outcome { CORRECT, WRONG, IGNORED }

    fun pick(choice: Int): Pair<Round, Outcome> {
        val q = current ?: return this to Outcome.IGNORED
        if (choice !in q.choices.indices || choice in wrong) return this to Outcome.IGNORED
        if (choice != q.answer) return copy(wrong = wrong + choice) to Outcome.WRONG
        val counts = !isTutorialQuestion && wrong.isEmpty()
        return copy(index = index + 1, firstTry = firstTry + if (counts) 1 else 0, wrong = emptySet()) to Outcome.CORRECT
    }
}

object Stars {
    /** 3 stars from 90 % first-try answers, 2 from 70 %, otherwise 1 (a finished level is never 0). */
    fun of(correct: Int, total: Int): Int {
        if (total <= 0) return 3
        val pct = correct * 100 / total
        return when {
            pct >= 90 -> 3
            pct >= 70 -> 2
            else -> 1
        }
    }

    /** Fewest stars a level needs to earn minutes, so tapping at random doesn't pay. */
    const val FOR_REWARD = 2
}
