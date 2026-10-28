package com.nourtime.app.core.coroutines

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeepRunningTest {

    private class Gone : Exception()

    private val logged = mutableListOf<String>()
    private val log: (String, Throwable) -> Unit = { msg, _ -> logged += msg }
    private val pause: (Int) -> Long = { doublingPause(it, 1_000, 30_000) }

    @Test
    fun `a reader whose store fails starts again and then follows the store`() = runTest {
        var reads = 0
        var seen: String? = null
        val store = flow {
            reads++
            if (reads == 1) throw IOException("settings file unreadable")
            emit("limited: [video]")
            awaitCancellation()
        }
        val reader = launch { keepRunning("limited apps", pause, log) { store.collect { seen = it } } }
        runCurrent()
        assertEquals(null, seen)
        advanceTimeBy(1_001)
        assertEquals("limited: [video]", seen)
        assertEquals(2, reads)
        assertEquals(listOf("limited apps failed (1); restarting"), logged)
        reader.cancel()
    }

    @Test
    fun `pauses between restarts double up to the maximum`() = runTest {
        val startedAt = mutableListOf<Long>()
        keepRunning("backup", pause, log) {
            startedAt += currentTime
            if (startedAt.size < 7) throw IOException("write failed")
        }
        // Failures 1..6 wait 1, 2, 4, 8, 16, then 30 (not 32) seconds.
        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L, 15_000L, 31_000L, 61_000L), startedAt)
    }

    @Test
    fun `cancellation thrown by the task ends it without a restart`() = runTest {
        var runs = 0
        try {
            keepRunning("mode", pause, log) {
                runs++
                throw CancellationException("stopped")
            }
            fail("expected the cancellation")
        } catch (_: CancellationException) {
        }
        assertEquals(1, runs)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `cancelling during the pause stops the restarts`() = runTest {
        var runs = 0
        val job = launch { keepRunning("mode", pause, log) { runs++; throw IOException("read failed") } }
        advanceTimeBy(500)
        job.cancel()
        advanceTimeBy(60_000)
        assertEquals(1, runs)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `a fatal error is rethrown at once`() = runTest {
        var runs = 0
        try {
            keepRunning("commands", pause, log, fatal = { it is Gone }) { runs++; throw Gone() }
            fail("expected the fatal error")
        } catch (_: Gone) {
        }
        assertEquals(1, runs)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `doubling pause`() {
        assertEquals(1_000L, doublingPause(1, 1_000, 30_000))
        assertEquals(2_000L, doublingPause(2, 1_000, 30_000))
        assertEquals(16_000L, doublingPause(5, 1_000, 30_000))
        assertEquals(30_000L, doublingPause(6, 1_000, 30_000))
        assertEquals(30_000L, doublingPause(Int.MAX_VALUE, 1_000, 30_000))
    }
}
