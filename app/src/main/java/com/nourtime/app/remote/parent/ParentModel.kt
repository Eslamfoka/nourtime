package com.nourtime.app.remote.parent

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.remote.model.RemoteStatus
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Outcome of typing or scanning a child's pairing code on the parent's phone. */
enum class ClaimResult {
    /** No phone shows this code, it expired, or someone else claimed it: the rules don't say which. */
    NOT_FOUND,
    WAITING_FOR_CHILD,
    REFUSED,
    PAIRED,
    OFFLINE,
    FAILED,
}

object ClaimCheck {
    /**
     * After the server refused a claim: null when the code is already ours (a retry; [claimedBy] is
     * readable only then), otherwise why it can't be claimed.
     */
    fun afterRefusal(claimedBy: String?, myUid: String): ClaimResult? =
        if (claimedBy == myUid) null else ClaimResult.NOT_FOUND

    /**
     * Whether the claimed code is gone (the child refused, or confirmed and cleaned up), from its
     * listener: missing by the server's word, or no longer readable at all ([denied]).
     */
    fun goneOnServer(exists: Boolean?, fromCache: Boolean?, denied: Boolean): Boolean =
        denied || (exists == false && fromCache == false)
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
    /** Apps open on minutes earned in the Learning Hub; the lock resumes after them. */
    data class Break(val remainingMs: Long, val lockPendingMs: Long, val degraded: Boolean) : DeviceSummary
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
                TimerPhase.AVAILABLE -> if (status.lockPendingMs > 0) {
                    // The waiting lock counts down in real time, like a running one.
                    Break(status.remainingMs, (status.lockPendingMs - age).coerceAtLeast(0), status.protectionDegraded)
                } else {
                    Available(status.remainingMs, status.protectionDegraded)
                }
                // The lock counts down in real time.
                TimerPhase.LOCKED -> Locked((status.lockRemainingMs - age).coerceAtLeast(0), status.protectionDegraded)
            }
        }
    }
}

/** Why Google sign-in failed on the parent's phone, for a message that fits; null when the parent cancelled. */
enum class SignInFailure {
    NO_ACCOUNT, OFFLINE, OTHER;

    companion object {
        fun of(error: Throwable): SignInFailure? = when (error) {
            is GetCredentialCancellationException -> null
            is NoCredentialException -> NO_ACCOUNT
            is FirebaseNetworkException, is IOException -> OFFLINE
            else -> OTHER
        }
    }
}
