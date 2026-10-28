package com.nourtime.app.core.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Keeps a long-running task alive (a settings reader, a protection loop, a sync task): when [block]
 * fails, the failure is logged and [block] runs again after [pauseMs] of the number of failures so
 * far, instead of the task ending and its readers keeping their last value for the rest of the
 * process (2026-10-28 audit). Returns when [block] returns. Cancellation passes through untouched, and
 * an error [fatal] says ends more than this task is rethrown.
 */
suspend fun keepRunning(
    name: String,
    pauseMs: (failures: Int) -> Long,
    log: (String, Throwable) -> Unit,
    fatal: suspend (Throwable) -> Boolean = { false },
    block: suspend () -> Unit,
) {
    var failures = 0
    while (true) {
        try {
            block()
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (fatal(e)) throw e
            failures++
            log("$name failed ($failures); restarting", e)
            delay(pauseMs(failures))
        }
    }
}

/** [firstMs] after the first failure, doubling with each one after it, never more than [maxMs]. */
fun doublingPause(failures: Int, firstMs: Long, maxMs: Long): Long =
    (firstMs shl (failures - 1).coerceIn(0, 10)).coerceAtMost(maxMs)
