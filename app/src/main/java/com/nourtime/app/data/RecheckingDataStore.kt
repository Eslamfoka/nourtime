package com.nourtime.app.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * A DataStore whose [data] can't stay behind. DataStore's own flow can miss a write that lands just as
 * a collector starts, and that collector then keeps the old value until the next write (seen on API 37:
 * 1 in 300 tries; the lock screen kept "selected apps" after the parent chose "whole phone").
 *
 * Each collector re-reads the store [RECHECKS_MS] after it starts. When the store holds something other
 * than what was sent, the subscription starts over: a new one begins with the store's current value, so
 * an older value can never follow a newer one.
 */
class RecheckingDataStore<T>(private val delegate: DataStore<T>) : DataStore<T> {

    override val data: Flow<T> = channelFlow {
        val sent = AtomicReference<Any?>(NOTHING)
        var subscription = subscribe(sent)
        var waited = 0L
        for (at in RECHECKS_MS) {
            delay(at - waited)
            waited = at
            if (delegate.data.first() != sent.get()) {
                subscription.cancelAndJoin()
                subscription = subscribe(sent)
            }
        }
    }.distinctUntilChanged()

    private fun ProducerScope<T>.subscribe(sent: AtomicReference<Any?>): Job = launch {
        delegate.data.collect {
            sent.set(it)
            send(it)
        }
    }

    override suspend fun updateData(transform: suspend (t: T) -> T): T = delegate.updateData(transform)

    companion object {
        /** When each collector re-reads the store, counted from its start. */
        val RECHECKS_MS = listOf(1_000L, 5_000L)

        private val NOTHING = Any()
    }
}
