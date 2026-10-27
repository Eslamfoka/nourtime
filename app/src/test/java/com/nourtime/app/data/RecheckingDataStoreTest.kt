package com.nourtime.app.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecheckingDataStoreTest {

    /** Like DataStore when the race hits: the first subscription gets the value, then nothing more. */
    private class MissingStore(initial: Int) : DataStore<Int> {
        val state = MutableStateFlow(initial)
        private var subscriptions = 0

        override val data: Flow<Int> = flow {
            emit(state.value)
            if (subscriptions++ == 0) awaitCancellation() else emitAll(state)
        }

        override suspend fun updateData(transform: suspend (t: Int) -> Int): Int = transform(state.value).also { state.value = it }
    }

    @Test
    fun `a write the subscription missed arrives at the first recheck`() = runTest {
        val store = MissingStore(1)
        val seen = mutableListOf<Int>()
        val job = launch { RecheckingDataStore(store).data.collect { seen += it } }
        runCurrent()
        store.updateData { 2 }
        advanceTimeBy(RecheckingDataStore.RECHECKS_MS.first() + 1)
        assertEquals(listOf(1, 2), seen)
        job.cancel()
    }

    @Test
    fun `after starting over, later writes keep arriving, in order and once each`() = runTest {
        val store = MissingStore(1)
        val seen = mutableListOf<Int>()
        val wrapped = RecheckingDataStore(store)
        val job = launch { wrapped.data.collect { seen += it } }
        runCurrent()
        store.updateData { 2 }
        advanceTimeBy(RecheckingDataStore.RECHECKS_MS.last() + 1)
        wrapped.updateData { 3 }
        runCurrent()
        wrapped.updateData { 4 }
        runCurrent()
        assertEquals(listOf(1, 2, 3, 4), seen)
        job.cancel()
    }

    @Test
    fun `a store that delivers everything isn't disturbed`() = runTest {
        val state = MutableStateFlow(1)
        val healthy = object : DataStore<Int> {
            override val data: Flow<Int> = state
            override suspend fun updateData(transform: suspend (t: Int) -> Int) = transform(state.value).also { state.value = it }
        }
        val seen = mutableListOf<Int>()
        val job = launch { RecheckingDataStore(healthy).data.collect { seen += it } }
        runCurrent()
        healthy.updateData { 2 }
        advanceTimeBy(RecheckingDataStore.RECHECKS_MS.last() + 1)
        assertEquals(listOf(1, 2), seen)
        job.cancel()
    }
}
