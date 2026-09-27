package com.nourtime.app.remote.model

/** A child's request for more time, `devices/{id}/requests/{rid}` (Phase 4c). */
data class TimeRequest(
    val id: String,
    val status: String,
    val createdAtMs: Long?,
    val answeredAtMs: Long?,
    val minutes: Int?,
)

sealed interface AskState {
    data object CanAsk : AskState
    data object Waiting : AskState
    data class Approved(val minutes: Int) : AskState
    data class Declined(val retryAtMs: Long) : AskState
}

/** What the "Time's up" screen shows for asking, from the latest request. */
object AskPolicy {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val DECLINED = "declined"

    /** A request the parent didn't answer in this time lapses; the child can ask again. */
    const val PENDING_EXPIRES_MS = 30 * 60_000L

    /** After "not now", the child waits this long before asking again. */
    const val RETRY_AFTER_DECLINE_MS = 10 * 60_000L

    /** How long "Yes! +15 minutes" stays; the bonus usually closes the screen sooner. */
    const val SHOW_APPROVAL_MS = 2 * 60_000L

    /**
     * [lockStartedAtMs] is when the current lock period began: an approval given before it belongs to
     * an earlier lock (its bonus was already used), so this lock can ask again.
     */
    fun state(latest: TimeRequest?, nowMs: Long, lockStartedAtMs: Long? = null): AskState {
        if (latest == null) return AskState.CanAsk
        return when (latest.status) {
            PENDING -> {
                val created = latest.createdAtMs ?: return AskState.Waiting
                if (nowMs - created > PENDING_EXPIRES_MS) AskState.CanAsk else AskState.Waiting
            }
            DECLINED -> {
                val retryAt = (latest.answeredAtMs ?: return AskState.CanAsk) + RETRY_AFTER_DECLINE_MS
                if (nowMs < retryAt) AskState.Declined(retryAt) else AskState.CanAsk
            }
            APPROVED -> {
                val answered = latest.answeredAtMs ?: return AskState.CanAsk
                val minutes = latest.minutes ?: return AskState.CanAsk
                if (lockStartedAtMs != null && answered < lockStartedAtMs) return AskState.CanAsk
                if (nowMs - answered < SHOW_APPROVAL_MS) AskState.Approved(minutes) else AskState.CanAsk
            }
            else -> AskState.CanAsk
        }
    }
}
