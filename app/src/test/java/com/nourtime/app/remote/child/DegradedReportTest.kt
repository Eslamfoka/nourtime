package com.nourtime.app.remote.child

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A child's phone that just restarted is "degraded" until Accessibility connects: that isn't news for the parent. */
class DegradedReportTest {

    @Test
    fun `a short blip after a restart is never reported`() = runTest {
        val states = flow {
            emit(true)
            delay(30_000)
            emit(false)
        }
        assertEquals(listOf(false), states.reportedDegraded().toList())
    }

    @Test
    fun `degraded for a while is reported, and its end at once`() = runTest {
        val states = flow {
            emit(false)
            emit(true)
            delay(DEGRADED_REPORT_AFTER_MS + 1)
            emit(false)
        }
        assertEquals(listOf(false, true, false), states.reportedDegraded().toList())
    }
}
