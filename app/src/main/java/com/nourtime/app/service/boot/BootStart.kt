package com.nourtime.app.service.boot

import kotlin.coroutines.cancellation.CancellationException

/**
 * Whether protection starts after a restart or an update. A settings read that fails starts it anyway
 * (fail closed): a set-up child's phone left without protection until someone opens Nour Time is the
 * worse mistake; on a phone not set up (or a parent's) the cost is the status notification until the
 * next restart. The read error is logged, never thrown: it would crash the process, and with it the
 * Accessibility service, which runs in the same process.
 */
object BootStart {
    suspend fun shouldStart(readSetupComplete: suspend () -> Boolean, log: (String, Throwable) -> Unit): Boolean =
        try {
            readSetupComplete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("couldn't read whether setup is complete; starting protection", e)
            true
        }
}
