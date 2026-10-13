package com.nourtime.app.remote.parent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** The parent's screens tell "still loading", "couldn't load" and "nothing there" apart. */
class LoadTest {

    @Test
    fun `starts as loading, then shows each value`() = runTest {
        val states = loadWithRetry(flowOf(0)) { flowOf(listOf("a"), listOf("a", "b")) }.toList()
        assertEquals(listOf(Load.Loading, Load.Ready(listOf("a")), Load.Ready(listOf("a", "b"))), states)
    }

    @Test
    fun `an error is a failure, not an empty list`() = runTest {
        val states = loadWithRetry(flowOf(0)) { flow<List<String>> { throw IOException("offline") } }.toList()
        assertEquals(listOf(Load.Loading, Load.Failed), states)
    }

    @Test
    fun `retry starts the source again`() = runTest {
        val retries = MutableStateFlow(0)
        var calls = 0
        val states = mutableListOf<Load<String>>()
        loadWithRetry(retries) {
            calls++
            flow { if (calls == 1) throw IOException("offline") else emit("ok") }
        }.take(4).collect { state ->
            states += state
            if (state == Load.Failed) retries.value++
        }
        assertEquals(listOf(Load.Loading, Load.Failed, Load.Loading, Load.Ready("ok")), states)
        assertEquals(2, calls)
    }

    @Test
    fun `valueOrNull gives the value only when ready`() {
        assertEquals(null, Load.Loading.valueOrNull())
        assertEquals(null, Load.Failed.valueOrNull())
        assertEquals(3, Load.Ready(3).valueOrNull())
    }
}
