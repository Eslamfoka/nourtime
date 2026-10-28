package com.nourtime.app.remote.child

import com.nourtime.app.core.coroutines.doublingPause
import com.nourtime.app.core.coroutines.keepRunning

/** First pause before a failed sync task runs again; it doubles up to [SYNC_RETRY_MAX_MS]. */
const val SYNC_RETRY_FIRST_MS = 5_000L
const val SYNC_RETRY_MAX_MS = 60_000L

fun syncRetryPause(failures: Int): Long = doublingPause(failures, SYNC_RETRY_FIRST_MS, SYNC_RETRY_MAX_MS)

/**
 * Runs one of [RemoteSync]'s tasks on its own: when it fails, it alone runs again after a growing
 * pause, and the other tasks (the settings listener, commands) carry on. Only a [fatal] error (this
 * phone's pairing is gone) ends the whole sync, which then unpairs (2026-10-17 audit).
 */
suspend fun runAlone(
    name: String,
    fatal: suspend (Throwable) -> Boolean,
    log: (String, Throwable) -> Unit,
    block: suspend () -> Unit,
) = keepRunning(name, ::syncRetryPause, log, fatal, block)
