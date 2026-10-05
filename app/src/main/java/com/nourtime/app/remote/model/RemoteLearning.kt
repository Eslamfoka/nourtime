package com.nourtime.app.remote.model

import com.nourtime.app.data.learning.LearningState
import java.time.LocalDate

/**
 * The child's Learning Hub minutes as the parent's phone sees them (`devices/{id}.learning`): the
 * bank (earned, not used yet) and what was earned on [earnedDay]. Uploaded by the child's phone
 * whenever they change.
 */
data class RemoteLearning(
    val bankMinutes: Int,
    val earnedDay: LocalDate?,
    val earnedMinutes: Int,
) {
    fun earnedOn(day: LocalDate): Int = if (earnedDay == day) earnedMinutes else 0

    fun toMap(): Map<String, Any?> = mapOf(
        "bankMinutes" to bankMinutes,
        "earnedDay" to earnedDay?.toString(),
        "earnedMinutes" to earnedMinutes,
    )

    companion object {
        fun of(s: LearningState) = RemoteLearning(s.bankMinutes, s.earnedDay, s.earnedMinutes)

        fun fromMap(m: Map<String, Any?>?): RemoteLearning? {
            if (m == null) return null
            return RemoteLearning(
                bankMinutes = (m["bankMinutes"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                earnedDay = (m["earnedDay"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                earnedMinutes = (m["earnedMinutes"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
            )
        }
    }
}
