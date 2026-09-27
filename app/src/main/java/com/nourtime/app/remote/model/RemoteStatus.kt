package com.nourtime.app.remote.model

import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.core.timer.TimerStatus

/** The child's timer as the parent's phone sees it (`devices/{id}.status`). */
data class RemoteStatus(
    val phase: TimerPhase,
    val remainingMs: Long,
    val budgetMs: Long,
    val lockRemainingMs: Long,
    val protectionDegraded: Boolean,
    /** When the child's phone wrote it (server time), or null while the write is pending. */
    val updatedAtMs: Long?,
) {
    companion object {
        fun fromMap(m: Map<String, Any?>?, updatedAtMs: Long?): RemoteStatus? {
            if (m == null) return null
            val phase = (m["phase"] as? String)?.let { name -> TimerPhase.entries.firstOrNull { it.name == name } } ?: return null
            return RemoteStatus(
                phase = phase,
                remainingMs = (m["remainingMs"] as? Number)?.toLong() ?: 0,
                budgetMs = (m["budgetMs"] as? Number)?.toLong() ?: 0,
                lockRemainingMs = (m["lockRemainingMs"] as? Number)?.toLong() ?: 0,
                protectionDegraded = m["protectionDegraded"] as? Boolean ?: false,
                updatedAtMs = updatedAtMs,
            )
        }
    }
}

/** The status fields the child's phone uploads (the caller adds `updatedAt`). */
fun statusMap(s: TimerStatus): Map<String, Any> = mapOf(
    "phase" to s.phase.name,
    "remainingMs" to s.remainingMs,
    "budgetMs" to s.budgetMs,
    "lockRemainingMs" to s.lockRemainingMs,
    "protectionDegraded" to s.protectionDegraded,
)

/**
 * Uploads anything the parent should see at once; the remaining time once a minute while it counts
 * down; and otherwise only a heartbeat, so an idle or locked phone doesn't write every minute (the
 * parent's phone counts a lock down itself).
 */
object StatusThrottle {
    const val INTERVAL_MS = 60_000L
    const val HEARTBEAT_MS = 10 * 60_000L

    fun shouldUpload(prev: TimerStatus?, next: TimerStatus, sinceLastMs: Long): Boolean =
        prev == null ||
            prev.phase != next.phase ||
            prev.protectionDegraded != next.protectionDegraded ||
            prev.budgetMs != next.budgetMs ||
            prev.counting != next.counting ||
            next.remainingMs > prev.remainingMs ||
            next.lockRemainingMs > prev.lockRemainingMs ||
            (next.counting && sinceLastMs >= INTERVAL_MS) ||
            sinceLastMs >= HEARTBEAT_MS
}
