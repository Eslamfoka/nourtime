package com.nourtime.app.core.blocking

import com.nourtime.app.core.time.DeviceClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parent pass lasts until the screen turns off (seen failing on the Honor: it survived an off/on). */
class ParentPassTest {

    private class FakeClock(var now: Long = 1_000_000) : DeviceClock {
        override fun elapsedRealtime() = now
        override fun bootCount() = 1
    }

    private val clock = FakeClock()
    private val pass = ParentPass(clock)

    @Test
    fun `a screen-off ends a full pass`() {
        pass.grantFull()
        pass.onScreenOff()
        assertFalse(pass.fullActive())
        assertFalse(pass.deviceActive())
    }

    @Test
    fun `a screen-off ends a device pass`() {
        pass.grantDevice()
        pass.onScreenOff()
        assertFalse(pass.deviceActive())
    }

    @Test
    fun `without a screen-off the pass lasts 15 minutes`() {
        pass.grantFull()
        clock.now += ParentPass.DURATION_MS - 1
        assertTrue(pass.fullActive())
        clock.now += 1
        assertFalse(pass.fullActive())
    }
}
