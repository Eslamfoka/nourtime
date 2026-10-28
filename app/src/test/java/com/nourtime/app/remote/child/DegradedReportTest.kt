package com.nourtime.app.remote.child

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Accessibility often drops for a moment and comes back (an update, a test tool, a restart): that isn't
 * news for the parent. A real loss is, within seconds.
 */
class DegradedReportTest {

    /** Long after the phone started: only the short grace applies. */
    private val settled = { DegradedGrace.delayMs(sinceBootMs = 60 * 60_000L) }

    @Test
    fun `a brief rebind is never reported`() = runTest {
        val states = flow {
            emit(false)
            emit(true)
            delay(DegradedGrace.GRACE_MS - 1)
            emit(false)
        }
        assertEquals(listOf(false), states.reportedDegraded(settled).toList())
    }

    @Test
    fun `a loss is reported after the grace, not after minutes`() = runTest {
        var reportedAt = -1L
        flow {
            emit(true)
            delay(10 * 60_000L)
        }.reportedDegraded(settled).collect { if (it) reportedAt = currentTime }
        assertEquals(DegradedGrace.GRACE_MS, reportedAt)
    }

    @Test
    fun `recovery is reported at once`() = runTest {
        val states = flow {
            emit(false)
            emit(true)
            delay(DegradedGrace.GRACE_MS + 1)
            emit(false)
        }
        assertEquals(listOf(false, true, false), states.reportedDegraded(settled).toList())
    }

    @Test
    fun `flapping keeps restarting the wait, then a lasting loss is reported once`() = runTest {
        val states = flow {
            repeat(5) {
                emit(true)
                delay(2_000)
                emit(false)
                delay(1_000)
            }
            emit(true)
            delay(DegradedGrace.GRACE_MS + 1)
        }
        assertEquals(listOf(false, true), states.reportedDegraded(settled).toList())
    }

    @Test
    fun `right after a restart of the phone, the wait lasts until two minutes of uptime`() {
        assertEquals(DegradedGrace.AFTER_BOOT_MS - 20_000L, DegradedGrace.delayMs(sinceBootMs = 20_000L))
        assertEquals(DegradedGrace.GRACE_MS, DegradedGrace.delayMs(sinceBootMs = DegradedGrace.AFTER_BOOT_MS - 5_000L))
        assertEquals(DegradedGrace.GRACE_MS, DegradedGrace.delayMs(sinceBootMs = 3 * 60 * 60_000L))
    }

    @Test
    fun `a slow reconnection after a restart isn't reported`() = runTest {
        val sinceBoot = 10_000L
        val states = flow {
            emit(true)
            delay(90_000)
            emit(false)
        }
        assertEquals(listOf(false), states.reportedDegraded { DegradedGrace.delayMs(sinceBoot) }.toList())
    }
}
