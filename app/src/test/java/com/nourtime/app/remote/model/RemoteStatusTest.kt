package com.nourtime.app.remote.model

import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.core.timer.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteStatusTest {

    private fun status(phase: TimerPhase = TimerPhase.AVAILABLE, remaining: Long = 600_000, source: DetectionSource = DetectionSource.ACCESSIBILITY) =
        TimerStatus(phase, remaining, 3_600_000, if (phase == TimerPhase.LOCKED) 7_200_000 else 0, emptySet(), source)

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
    fun `countdown ticks are uploaded once a minute`() {
        assertFalse(StatusThrottle.shouldUpload(status(remaining = 600_000), status(remaining = 570_000), sinceLastMs = 30_000))
        assertTrue(StatusThrottle.shouldUpload(status(remaining = 600_000), status(remaining = 540_000), sinceLastMs = 60_000))
    }
}
