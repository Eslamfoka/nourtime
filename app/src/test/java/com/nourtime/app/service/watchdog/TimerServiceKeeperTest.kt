package com.nourtime.app.service.watchdog

import com.nourtime.app.data.mode.AppMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Accessibility events restart a stopped timer service long before the 15-minute watchdog would. */
class TimerServiceKeeperTest {

    private var now = 0L
    private var mode: AppMode? = AppMode.CHILD
    private var setupComplete = true
    private var running = false
    private var starts = 0
    private val keeper = TimerServiceKeeper(
        nowMs = { now },
        mode = { mode },
        setupComplete = { setupComplete },
        timerRunning = { running },
        start = { starts++ },
    )

    @Test
    fun `a stopped timer service is started on the next event`() {
        assertTrue(keeper.onEvent())
        assertEquals(1, starts)
    }

    @Test
    fun `a running timer service is left alone`() {
        running = true
        assertFalse(keeper.onEvent())
        assertEquals(0, starts)
    }

    @Test
    fun `checks at most once per interval, however many events arrive`() {
        running = true
        keeper.onEvent()
        running = false
        now += TimerServiceKeeper.INTERVAL_MS - 1
        repeat(50) { keeper.onEvent() }
        assertEquals(0, starts)
        now += 1
        assertTrue(keeper.onEvent())
        assertEquals(1, starts)
    }

    @Test
    fun `nothing is started on a parent's phone or before setup is finished`() {
        mode = AppMode.PARENT
        assertFalse(keeper.onEvent())
        mode = AppMode.CHILD
        setupComplete = false
        now += TimerServiceKeeper.INTERVAL_MS
        assertFalse(keeper.onEvent())
        mode = null
        setupComplete = true
        now += TimerServiceKeeper.INTERVAL_MS
        assertFalse(keeper.onEvent())
        assertEquals(0, starts)
    }
}
