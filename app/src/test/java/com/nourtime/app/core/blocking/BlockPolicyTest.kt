package com.nourtime.app.core.blocking

import com.nourtime.app.core.detection.ForegroundState
import com.nourtime.app.core.detection.ScreenState
import com.nourtime.app.data.settings.Bedtime
import com.nourtime.app.data.settings.LockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class BlockPolicyTest {

    private val youtube = "com.google.android.youtube"
    private val launcher = "com.google.android.apps.nexuslauncher"

    private fun on(fg: String, vararg visible: String) = ForegroundState(foreground = fg, visible = setOf(fg, *visible))

    private fun input(
        fg: ForegroundState,
        lockType: LockType = LockType.SELECTED_APPS,
        timeUp: Boolean = false,
        bedtime: Boolean = false,
        degraded: Boolean = false,
        protect: Boolean = true,
        devicePass: Boolean = false,
        fullPass: Boolean = false,
    ) = BlockInput(fg, setOf(youtube), lockType, timeUp, bedtime, degraded, protect, "com.nourtime.app", devicePass, fullPass)

    @Test
    fun `nothing is blocked while time is available`() {
        assertNull(BlockPolicy.decide(input(on(youtube))))
    }

    @Test
    fun `time up blocks the limited app, needing the security answer`() {
        assertEquals(
            BlockDecision(BlockReason.TIME_UP, wholeDevice = false, needsSecurityAnswer = true),
            BlockPolicy.decide(input(on(youtube), timeUp = true)),
        )
        assertNull(BlockPolicy.decide(input(on(launcher), timeUp = true)))
    }

    @Test
    fun `picture in picture of a limited app is blocked`() {
        assertEquals(BlockReason.TIME_UP, BlockPolicy.decide(input(on(launcher, youtube), timeUp = true))?.reason)
    }

    @Test
    fun `whole device lock covers every app until a device pass`() {
        val d = BlockPolicy.decide(input(on(launcher), lockType = LockType.WHOLE_DEVICE, timeUp = true))
        assertTrue(d!!.wholeDevice)
        assertNull(BlockPolicy.decide(input(on(launcher), lockType = LockType.WHOLE_DEVICE, timeUp = true, devicePass = true)))
        // The PIN alone still keeps limited apps closed during the lock period.
        val limited = BlockPolicy.decide(input(on(youtube), lockType = LockType.WHOLE_DEVICE, timeUp = true, devicePass = true))
        assertEquals(BlockDecision(BlockReason.TIME_UP, wholeDevice = false, needsSecurityAnswer = true), limited)
        assertNull(BlockPolicy.decide(input(on(youtube), lockType = LockType.WHOLE_DEVICE, timeUp = true, fullPass = true)))
    }

    @Test
    fun `bedtime blocks like a lock period`() {
        assertEquals(BlockReason.BEDTIME, BlockPolicy.decide(input(on(youtube), bedtime = true))?.reason)
    }

    @Test
    fun `degraded protection blocks limited apps with the PIN only`() {
        val d = BlockPolicy.decide(input(on(youtube), degraded = true))
        assertEquals(BlockDecision(BlockReason.PROTECTION, wholeDevice = false, needsSecurityAnswer = false), d)
        assertNull(BlockPolicy.decide(input(on(launcher), degraded = true)))
    }

    @Test
    fun `system settings need the PIN, and the answer during a lock period`() {
        val settings = on("com.android.settings")
        assertEquals(BlockReason.SYSTEM_SETTINGS, BlockPolicy.decide(input(settings))?.reason)
        assertNull(BlockPolicy.decide(input(settings, devicePass = true)))
        assertEquals(true, BlockPolicy.decide(input(settings, timeUp = true, devicePass = true))?.needsSecurityAnswer)
        assertNull(BlockPolicy.decide(input(settings, timeUp = true, fullPass = true)))
        assertNull(BlockPolicy.decide(input(settings, protect = false)))
    }

    @Test
    fun `own app and locked screen are never covered`() {
        assertNull(BlockPolicy.decide(input(on("com.nourtime.app"), lockType = LockType.WHOLE_DEVICE, timeUp = true)))
        val offScreen = on(youtube).copy(screen = ScreenState(interactive = true, keyguardLocked = true))
        assertNull(BlockPolicy.decide(input(offScreen, timeUp = true)))
    }

    @Test
    fun `phone calls are never covered`() {
        val ringing = input(on(youtube), lockType = LockType.WHOLE_DEVICE, timeUp = true).copy(phoneCallActive = true)
        assertNull(BlockPolicy.decide(ringing))
        assertNull(BlockPolicy.decide(input(on("com.google.android.dialer"), lockType = LockType.WHOLE_DEVICE, timeUp = true)))
        assertNull(BlockPolicy.decide(input(on("com.samsung.android.incallui"), lockType = LockType.WHOLE_DEVICE, bedtime = true)))
    }

    @Test
    fun `bedtime window crossing midnight`() {
        val b = Bedtime(enabled = true, startMinute = 21 * 60, endMinute = 7 * 60)
        assertTrue(b.isActive(LocalTime.of(21, 0)))
        assertTrue(b.isActive(LocalTime.of(2, 30)))
        assertFalse(b.isActive(LocalTime.of(7, 0)))
        assertFalse(b.isActive(LocalTime.of(12, 0)))
        assertEquals(60, b.minutesUntilEnd(LocalTime.of(6, 0)))
        assertEquals(10 * 60, b.minutesUntilEnd(LocalTime.of(21, 0)))
        assertFalse(b.copy(enabled = false).isActive(LocalTime.of(22, 0)))
    }

    @Test
    fun `bedtime window within one day`() {
        val b = Bedtime(enabled = true, startMinute = 13 * 60, endMinute = 15 * 60)
        assertTrue(b.isActive(LocalTime.of(14, 0)))
        assertFalse(b.isActive(LocalTime.of(15, 0)))
    }
}
