package com.nourtime.app.remote.model

import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.core.timer.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteStatusTest {

    private fun status(
        phase: TimerPhase = TimerPhase.AVAILABLE,
        remaining: Long = 600_000,
        source: DetectionSource = DetectionSource.ACCESSIBILITY,
        inUse: Boolean = false,
        lock: Long = if (phase == TimerPhase.LOCKED) 7_200_000 else 0,
    ) = TimerStatus(phase, remaining, 3_600_000, lock, if (inUse) setOf("com.example.game") else emptySet(), source)

    @Test
    fun `round-trips through the Firestore map`() {
        val r = RemoteStatus.fromMap(statusMap(status(TimerPhase.LOCKED, 0)), updatedAtMs = 42)
        assertEquals(RemoteStatus(TimerPhase.LOCKED, 0, 3_600_000, 7_200_000, protectionDegraded = false, updatedAtMs = 42), r)
    }

    @Test
    fun `the first status is uploaded`() {
        assertTrue(StatusThrottle.shouldUpload(prev = null, next = status(), sinceLastMs = 0))
    }

    @Test
    fun `a phase change is uploaded at once`() {
        assertTrue(StatusThrottle.shouldUpload(status(), status(TimerPhase.LOCKED, 0), sinceLastMs = 1_000))
    }

    @Test
    fun `a protection change is uploaded at once`() {
        assertTrue(StatusThrottle.shouldUpload(status(), status(source = DetectionSource.USAGE_STATS), sinceLastMs = 1_000))
    }

    @Test
    fun `while the timer counts, it is uploaded once a minute`() {
        assertFalse(StatusThrottle.shouldUpload(status(remaining = 600_000, inUse = true), status(remaining = 570_000, inUse = true), sinceLastMs = 30_000))
        assertTrue(StatusThrottle.shouldUpload(status(remaining = 600_000, inUse = true), status(remaining = 540_000, inUse = true), sinceLastMs = 60_000))
    }

    @Test
    fun `an idle phone only sends a heartbeat`() {
        // Deferred review item: ~1,440 writes a day per phone for nothing.
        assertFalse(StatusThrottle.shouldUpload(status(), status(), sinceLastMs = 5 * 60_000))
        assertTrue(StatusThrottle.shouldUpload(status(), status(), sinceLastMs = StatusThrottle.HEARTBEAT_MS))
    }

    @Test
    fun `a running lock only sends a heartbeat (the parent's phone counts it down)`() {
        val locked = status(TimerPhase.LOCKED, 0)
        assertFalse(StatusThrottle.shouldUpload(locked, status(TimerPhase.LOCKED, 0, lock = 7_000_000), sinceLastMs = 5 * 60_000))
    }

    @Test
    fun `the heartbeat is well inside the parent's "not seen" limit`() {
        assertTrue(StatusThrottle.HEARTBEAT_MS * 3 / 2 <= com.nourtime.app.remote.parent.DeviceSummary.STALE_AFTER_MS)
    }

    @Test
    fun `starting or stopping a limited app is uploaded at once`() {
        assertTrue(StatusThrottle.shouldUpload(status(), status(inUse = true), sinceLastMs = 1_000))
        assertTrue(StatusThrottle.shouldUpload(status(remaining = 500_000, inUse = true), status(remaining = 490_000), sinceLastMs = 1_000))
    }

    @Test
    fun `extra time is uploaded at once`() {
        assertTrue(StatusThrottle.shouldUpload(status(remaining = 60_000), status(remaining = 960_000), sinceLastMs = 1_000))
        assertTrue(StatusThrottle.shouldUpload(status(TimerPhase.LOCKED, 0, lock = 3_600_000), status(TimerPhase.LOCKED, 0, lock = 7_200_000), sinceLastMs = 1_000))
    }
}
