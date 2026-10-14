package com.nourtime.app.remote.child

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/** How long protection must stay degraded before the parent hears about it. */
const val DEGRADED_REPORT_AFTER_MS = 2 * 60_000L

/**
 * Degraded protection as the parent's phone should see it: a phone that just (re)started is degraded
 * until Accessibility connects, which isn't news; staying degraded is. Its end is reported at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<Boolean>.reportedDegraded(afterMs: Long = DEGRADED_REPORT_AFTER_MS): Flow<Boolean> =
    distinctUntilChanged()
        .transformLatest { degraded ->
            if (degraded) {
                delay(afterMs)
                emit(true)
            } else {
                emit(false)
            }
        }
        .distinctUntilChanged()
