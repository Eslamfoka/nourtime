package com.nourtime.app.remote.parent

import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.remote.child.PairingStates
import com.nourtime.app.remote.model.RemoteStatus
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of typing or scanning a child's pairing code on the parent's phone. */
enum class ClaimResult { NOT_FOUND, EXPIRED, ALREADY_CLAIMED, WAITING_FOR_CHILD, REFUSED, PAIRED, OFFLINE, FAILED }

object ClaimCheck {
    /** Why a code can't be claimed, from the pairing document; null when it can. */
    fun before(exists: Boolean, createdAtMs: Long?, claimedBy: String?, myUid: String, nowMs: Long): ClaimResult? = when {
        !exists -> ClaimResult.NOT_FOUND
        claimedBy == myUid -> ClaimResult.WAITING_FOR_CHILD
        claimedBy != null -> ClaimResult.ALREADY_CLAIMED
        createdAtMs != null && nowMs >= createdAtMs + PairingStates.VALID_MS -> ClaimResult.EXPIRED
        else -> null
    }
}

/**
 * The steps of claiming a code. The parent is told to tap "Allow" on the child's phone only once the
 * server has the claim; a claim that isn't confirmed in time counts as offline.
 */
object ClaimFlow {
    const val CLAIM_TIMEOUT_MS = 20_000L

    /**
     * [claimOnServer] returns null once the claim is on the server (or was already ours), or why the
     * code can't be claimed. [onClaimed] runs between that and [waitForChild].
     */
    suspend fun run(
        claimOnServer: suspend () -> ClaimResult?,
        onClaimed: () -> Unit,
        waitForChild: suspend () -> ClaimResult,
    ): ClaimResult {
        // Wrapped so that "timed out" (null) and "claimed" (a null result) stay apart.
        val claim = withTimeoutOrNull(CLAIM_TIMEOUT_MS) { Claim(claimOnServer()) } ?: return ClaimResult.OFFLINE
        claim.refusal?.let { return it }
        onClaimed()
        return waitForChild()
    }

    private class Claim(val refusal: ClaimResult?)
}

/** One line about a child's phone for the parent: time left, lock countdown, or not seen lately. */
sealed interface DeviceSummary {
    data object Unknown : DeviceSummary
    data class Available(val remainingMs: Long, val degraded: Boolean) : DeviceSummary
    data class Locked(val lockRemainingMs: Long, val degraded: Boolean) : DeviceSummary
    data class NotSeen(val sinceMs: Long) : DeviceSummary

    companion object {
        /** A phone that hasn't uploaded for this long may be off or offline. */
        const val STALE_AFTER_MS = 15 * 60_000L

        /** Whole minutes since the update, or null for "just now" (also when the clocks disagree slightly). */
        fun minutesAgo(updatedAtMs: Long, nowMs: Long): Long? = ((nowMs - updatedAtMs) / 60_000).takeIf { it >= 1 }

        fun of(status: RemoteStatus?, nowMs: Long): DeviceSummary {
            if (status == null) return Unknown
            val updatedAt = status.updatedAtMs
            val age = if (updatedAt == null) 0 else (nowMs - updatedAt).coerceAtLeast(0)
            if (updatedAt != null && age > STALE_AFTER_MS) return NotSeen(updatedAt)
            return when (status.phase) {
                // The budget only runs while a limited app is open, so it isn't extrapolated.
                TimerPhase.AVAILABLE -> Available(status.remainingMs, status.protectionDegraded)
                // The lock counts down in real time.
                TimerPhase.LOCKED -> Locked((status.lockRemainingMs - age).coerceAtLeast(0), status.protectionDegraded)
            }
        }
    }
}
