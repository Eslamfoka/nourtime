package com.nourtime.app.remote.child

/** What the child's pairing screen shows (Phase 2). */
sealed interface PairingState {
    data object Starting : PairingState

    /** Showing the QR code and the 6-digit code until a parent claims it. */
    data class Waiting(val code: String, val expiresAtMs: Long) : PairingState

    /** A parent claimed the code; the parent (holding this phone) confirms or refuses. */
    data class Claimed(val code: String, val name: String?, val email: String?) : PairingState

    data object Paired : PairingState

    /** The code ran out, was refused, or was removed. */
    data object Expired : PairingState

    data class Failed(val reason: PairingError) : PairingState
}

enum class PairingError { OFFLINE, OTHER }

object PairingStates {

    /** Codes are valid for 10 minutes after the server stored them (enforced by the rules too). */
    const val VALID_MS = 10 * 60_000L

    /**
     * State from the pairing document. The countdown runs from [localStartMs] on this phone's own
     * clock: comparing the server's createdAt with a phone clock that is off (or was changed) would
     * expire the code early. The Firestore rules enforce the real 10 minutes with server time.
     */
    fun of(
        code: String,
        exists: Boolean,
        claimedBy: String?,
        claimedName: String?,
        claimedEmail: String?,
        localStartMs: Long,
        nowMs: Long,
    ): PairingState {
        if (!exists) return PairingState.Expired
        // A claim the server accepted counts even if the snapshot arrives after the expiry.
        if (claimedBy != null) return PairingState.Claimed(code, claimedName, claimedEmail)
        val expiresAt = localStartMs + VALID_MS
        return if (nowMs >= expiresAt) PairingState.Expired else PairingState.Waiting(code, expiresAt)
    }
}
