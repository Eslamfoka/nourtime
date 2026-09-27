package com.nourtime.app.remote.parent

import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.remote.model.RemoteStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSummaryTest {

    private val min = 60_000L
    private val at = 10_000_000L

    private fun status(phase: TimerPhase, remaining: Long = 0, lockLeft: Long = 0, degraded: Boolean = false, updatedAt: Long? = at) =
        RemoteStatus(phase, remaining, 60 * min, lockLeft, degraded, updatedAt)

    @Test
    fun `nothing uploaded yet`() {
        assertEquals(DeviceSummary.Unknown, DeviceSummary.of(null, nowMs = at))
    }

    @Test
    fun `available shows the time left as uploaded`() {
        assertEquals(DeviceSummary.Available(35 * min, degraded = false), DeviceSummary.of(status(TimerPhase.AVAILABLE, remaining = 35 * min), nowMs = at + 2 * min))
    }

    @Test
    fun `a lock keeps counting down between uploads`() {
        assertEquals(DeviceSummary.Locked(100 * min, degraded = false), DeviceSummary.of(status(TimerPhase.LOCKED, lockLeft = 110 * min), nowMs = at + 10 * min))
    }

    @Test
    fun `a lock that should have ended shows zero until the phone reports`() {
        assertEquals(DeviceSummary.Locked(0, degraded = false), DeviceSummary.of(status(TimerPhase.LOCKED, lockLeft = 5 * min), nowMs = at + 10 * min))
    }

    @Test
    fun `quiet for more than 15 minutes is not seen`() {
        assertEquals(DeviceSummary.NotSeen(at), DeviceSummary.of(status(TimerPhase.AVAILABLE, remaining = min), nowMs = at + 16 * min))
    }

    @Test
    fun `protection problems are passed on`() {
        assertEquals(DeviceSummary.Available(min, degraded = true), DeviceSummary.of(status(TimerPhase.AVAILABLE, remaining = min, degraded = true), nowMs = at))
    }

    @Test
    fun `a status still being written counts as fresh`() {
        assertEquals(DeviceSummary.Available(min, degraded = false), DeviceSummary.of(status(TimerPhase.AVAILABLE, remaining = min, updatedAt = null), nowMs = at))
    }
}
