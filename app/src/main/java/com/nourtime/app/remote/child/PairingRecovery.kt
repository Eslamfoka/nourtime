package com.nourtime.app.remote.child

/**
 * A phone whose account record names another device (two device documents left by older builds,
 * or a restored local device id) was refused every new code: the rules allow one device per
 * account, and nothing cleared the old one (2026-10-28 review). The old device and its open code
 * are this account's to delete, so the phone removes them and tries once more.
 */
object PairingRecovery {

    /** The device the account record names that isn't this phone's any more, or null. */
    fun staleDevice(recorded: String?, current: String): String? =
        recorded?.takeIf { it.isNotBlank() && it != current }

    /**
     * Runs [attempt]; when [isRefused] says the rules refused it, runs [recover] and tries once more.
     * A second refusal becomes [PairingRefusedException]. Other failures pass through untouched.
     */
    suspend fun <T> retryOnceAfter(
        isRefused: (Throwable) -> Boolean,
        recover: suspend () -> Unit,
        attempt: suspend () -> T,
    ): T {
        val first = runCatching { attempt() }
        val error = first.exceptionOrNull() ?: return first.getOrThrow()
        if (!isRefused(error)) throw error
        recover()
        val second = runCatching { attempt() }
        val again = second.exceptionOrNull() ?: return second.getOrThrow()
        throw if (isRefused(again)) PairingRefusedException(again) else again
    }
}

/** The rules still refused this phone's device after the old one was cleared. */
class PairingRefusedException(cause: Throwable) : Exception("Pairing refused after clearing the old device", cause)
