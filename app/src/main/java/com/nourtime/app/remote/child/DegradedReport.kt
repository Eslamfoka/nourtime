package com.nourtime.app.remote.child

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/**
 * How long protection must stay degraded before anyone is told (the parent's phone and this phone's
 * notification). Accessibility often drops for a second or two and comes back by itself: an update of
 * Nour Time or of the phone's accessibility components, a test tool (uiautomator) taking over, a
 * phone maker's cleaner restarting the process. [GRACE_MS] lets those pass without an alert while a
 * real loss still reaches the parent within seconds; the push function adds its own throttle (one
 * degraded push per 30 minutes), so even a service that keeps flapping can't flood the parent.
 * Right after a restart of the phone, Nour Time often runs before Accessibility has been connected
 * again (slow phones take longer while everything starts), so the first [AFTER_BOOT_MS] of uptime
 * count as part of the wait.
 */
object DegradedGrace {
    const val GRACE_MS = 15_000L
    const val AFTER_BOOT_MS = 2 * 60_000L

    /** The wait for a loss seen [sinceBootMs] after the phone started (elapsedRealtime). */
    fun delayMs(sinceBootMs: Long): Long = maxOf(GRACE_MS, AFTER_BOOT_MS - sinceBootMs)
}

/**
 * Degraded protection as the parent's phone should see it: a loss is reported once it has lasted
 * [delayMs] (asked when it starts, see [DegradedGrace]); a reconnection within that time cancels it.
 * Its end is reported at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<Boolean>.reportedDegraded(delayMs: () -> Long): Flow<Boolean> =
    distinctUntilChanged()
        .transformLatest { degraded ->
            if (degraded) {
                delay(delayMs())
                emit(true)
            } else {
                emit(false)
            }
        }
        .distinctUntilChanged()
