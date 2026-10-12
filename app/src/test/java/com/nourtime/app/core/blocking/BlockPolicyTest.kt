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
        allowed: Set<String> = emptySet(),
    ) = BlockInput(fg, setOf(youtube), lockType, timeUp, bedtime, degraded, protect, "com.nourtime.app", devicePass, fullPass, allowedDuringLock = allowed)

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
        // Nour Time's lock screen is drawn above the notifications, so it steps aside while the phone
        // rings: the incoming call can only be answered from its notification.
        val ringing = input(on(youtube), lockType = LockType.WHOLE_DEVICE, timeUp = true).copy(phoneRinging = true)
        assertNull(BlockPolicy.decide(ringing))
        assertNull(BlockPolicy.decide(input(on("com.google.android.dialer"), lockType = LockType.WHOLE_DEVICE, timeUp = true)))
        assertNull(BlockPolicy.decide(input(on("com.samsung.android.incallui"), lockType = LockType.WHOLE_DEVICE, bedtime = true)))
    }

    // Bypasses found in the 2026-10-12 audit: a ringing phone uncovered Settings too (a call from
    // another phone gave the child ~30 s to force-stop Nour Time), and a call turned a whole-phone lock
    // into a limited-apps lock (calling voicemail was enough).

    @Test
    fun `a ringing phone doesn't uncover the phone's settings`() {
        val ringing = input(on("com.android.settings")).copy(phoneRinging = true)
        assertEquals(BlockReason.SYSTEM_SETTINGS, BlockPolicy.decide(ringing)?.reason)
    }

    // Found in the 2026-10-12 audit: without device admin (it can be skipped during setup), the Play
    // Store's "Uninstall" button removes Nour Time without the package installer's screen.

    private val playStore = "com.android.vending"

    @Test
    fun `without device admin the Play Store needs the parent PIN`() {
        val noAdmin = input(on(playStore)).copy(deviceAdminActive = false)
        assertEquals(BlockReason.SYSTEM_SETTINGS, BlockPolicy.decide(noAdmin)?.reason)
    }

    @Test
    fun `with device admin the Play Store stays usable`() {
        assertNull(BlockPolicy.decide(input(on(playStore)).copy(deviceAdminActive = true)))
    }

    @Test
    fun `the parent PIN opens the Play Store without device admin`() {
        assertNull(BlockPolicy.decide(input(on(playStore), devicePass = true).copy(deviceAdminActive = false)))
    }

    @Test
    fun `a ringing phone doesn't uncover the app installer`() {
        val ringing = input(on("com.google.android.packageinstaller"), timeUp = true).copy(phoneRinging = true)
        assertEquals(BlockReason.SYSTEM_SETTINGS, BlockPolicy.decide(ringing)?.reason)
    }

    // Bypasses found in the 2026-10-11 review: a limited app kept playing in picture in picture
    // while Nour Time or the phone app was in front, and a call unlocked every app.

    private val dialer = "com.google.android.dialer"
    private val timeUpBlock = BlockDecision(BlockReason.TIME_UP, wholeDevice = false, needsSecurityAnswer = true)

    @Test
    fun `a limited app in picture in picture over Nour Time is blocked`() {
        assertEquals(timeUpBlock, BlockPolicy.decide(input(on("com.nourtime.app", youtube), timeUp = true)))
    }

    @Test
    fun `a limited app in picture in picture over the phone app is blocked`() {
        assertEquals(timeUpBlock, BlockPolicy.decide(input(on(dialer, youtube), timeUp = true)))
    }

    @Test
    fun `a limited app over the default third-party dialer is blocked`() {
        val custom = "com.example.dialer"
        val alone = input(on(custom), LockType.WHOLE_DEVICE, timeUp = true).copy(dialerPackage = custom)
        assertNull(BlockPolicy.decide(alone))
        val withVideo = input(on(custom, youtube), timeUp = true).copy(dialerPackage = custom)
        assertEquals(timeUpBlock, BlockPolicy.decide(withVideo))
    }

    @Test
    fun `during a call the call screen stays usable`() {
        assertNull(BlockPolicy.decide(input(on("com.samsung.android.incallui", youtube), timeUp = true).copy(inCall = true)))
    }

    @Test
    fun `during a call a limited app is still blocked`() {
        assertEquals(timeUpBlock, BlockPolicy.decide(input(on(youtube), timeUp = true).copy(inCall = true)))
    }

    @Test
    fun `during a call the phone's settings stay protected`() {
        assertEquals(BlockReason.SYSTEM_SETTINGS, BlockPolicy.decide(input(on("com.android.settings")).copy(inCall = true))?.reason)
    }

    @Test
    fun `during a call the whole phone lock still covers other apps`() {
        // The lock screen offers "Back to the call" instead (LockScreenState.inCall).
        val chrome = input(on("com.android.chrome"), LockType.WHOLE_DEVICE, timeUp = true).copy(inCall = true)
        assertEquals(true, BlockPolicy.decide(chrome)?.wholeDevice)
        val home = input(on(launcher), LockType.WHOLE_DEVICE, bedtime = true).copy(inCall = true)
        assertEquals(true, BlockPolicy.decide(home)?.wholeDevice)
    }

    @Test
    fun `during a call the dialer stays usable under a whole phone lock`() {
        assertNull(BlockPolicy.decide(input(on(dialer), LockType.WHOLE_DEVICE, timeUp = true).copy(inCall = true)))
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

    // Educational apps the parent allows during the lock period (Phase 1.5, task 2).

    private val quran = "com.quran.labs.androidquran"

    @Test
    fun `whole phone lock lets an allowed app through`() {
        assertNull(BlockPolicy.decide(input(on(quran), LockType.WHOLE_DEVICE, timeUp = true, allowed = setOf(quran))))
    }

    @Test
    fun `whole phone bedtime lets an allowed app through`() {
        assertNull(BlockPolicy.decide(input(on(quran), LockType.WHOLE_DEVICE, bedtime = true, allowed = setOf(quran))))
    }

    @Test
    fun `whole phone lock still covers the launcher and other apps`() {
        assertEquals(
            BlockDecision(BlockReason.TIME_UP, wholeDevice = true, needsSecurityAnswer = false),
            BlockPolicy.decide(input(on(launcher), LockType.WHOLE_DEVICE, timeUp = true, allowed = setOf(quran))),
        )
    }

    @Test
    fun `split screen with a non-allowed app keeps the whole phone locked`() {
        assertEquals(
            BlockDecision(BlockReason.TIME_UP, wholeDevice = true, needsSecurityAnswer = false),
            BlockPolicy.decide(input(on(quran, "com.android.chrome"), LockType.WHOLE_DEVICE, timeUp = true, allowed = setOf(quran))),
        )
    }

    @Test
    fun `a limited app in picture in picture over an allowed app is still blocked`() {
        assertEquals(
            BlockDecision(BlockReason.TIME_UP, wholeDevice = false, needsSecurityAnswer = true),
            BlockPolicy.decide(input(on(quran, youtube), timeUp = true, allowed = setOf(quran))),
        )
    }

    @Test
    fun `allowed apps don't open the phone's settings`() {
        assertEquals(
            BlockDecision(BlockReason.SYSTEM_SETTINGS, wholeDevice = false, needsSecurityAnswer = true),
            BlockPolicy.decide(input(on("com.android.settings"), LockType.WHOLE_DEVICE, timeUp = true, allowed = setOf("com.android.settings"))),
        )
    }
}
