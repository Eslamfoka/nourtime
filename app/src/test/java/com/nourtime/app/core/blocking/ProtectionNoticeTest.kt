package com.nourtime.app.core.blocking

import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.detection.ForegroundState
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.feature.lock.OverlayParentFlow
import com.nourtime.app.feature.lock.TemplateKind
import com.nourtime.app.feature.lock.templateFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Protection is off" notice: shown over the limited apps while Accessibility is lost (seen through
 * usage stats), never over the phone app, a ringing call, Nour Time or the Accessibility settings the
 * parent opens with the PIN; gone as soon as Accessibility is back.
 */
class ProtectionNoticeTest {

    private val youtube = "com.google.android.youtube"
    private val whatsapp = "com.whatsapp"
    private val settingsApp = "com.android.settings"

    private fun on(fg: String) = ForegroundState(foreground = fg, visible = setOf(fg), source = DetectionSource.USAGE_STATS)

    private fun input(
        fg: ForegroundState,
        degraded: Boolean = true,
        timeUp: Boolean = false,
        lockType: LockType = LockType.SELECTED_APPS,
        fullPass: Boolean = false,
        ringing: Boolean = false,
        inCall: Boolean = false,
    ) = BlockInput(
        foreground = fg,
        limitedApps = setOf(youtube),
        lockType = lockType,
        timeUp = timeUp,
        bedtime = false,
        protectionDegraded = degraded,
        protectSystemSettings = true,
        ownPackage = "com.nourtime.app",
        devicePass = fullPass,
        fullPass = fullPass,
        phoneRinging = ringing,
        inCall = inCall,
    )

    @Test
    fun `a limited app gets the protection notice, which leads to the Accessibility settings`() {
        val d = BlockPolicy.decide(input(on(youtube)))!!
        assertEquals(BlockDecision(BlockReason.PROTECTION, wholeDevice = false, needsSecurityAnswer = false), d)
        assertEquals(TemplateKind.PROTECTION_OFF, templateFor(d.reason, null))
        assertTrue(OverlayParentFlow.opensAccessibilitySettings(d))
    }

    @Test
    fun `apps the parent didn't limit stay usable, so the child can still reach a parent`() {
        assertNull(BlockPolicy.decide(input(on(whatsapp))))
        assertNull(BlockPolicy.decide(input(on("com.google.android.dialer"))))
    }

    @Test
    fun `a ringing phone uncovers the screen so the call can be answered`() {
        assertNull(BlockPolicy.decide(input(on(youtube), ringing = true)))
    }

    @Test
    fun `during a call the call screen stays usable`() {
        assertNull(BlockPolicy.decide(input(on("com.android.incallui"), inCall = true)))
    }

    @Test
    fun `after the parent's PIN the Accessibility settings and the app are uncovered`() {
        assertNull(BlockPolicy.decide(input(on(settingsApp), fullPass = true)))
        assertNull(BlockPolicy.decide(input(on(youtube), fullPass = true)))
        // Without the PIN the settings keep their own cover, which isn't the notice.
        val settings = BlockPolicy.decide(input(on(settingsApp)))!!
        assertEquals(BlockReason.SYSTEM_SETTINGS, settings.reason)
        assertFalse(OverlayParentFlow.opensAccessibilitySettings(settings))
    }

    @Test
    fun `once Accessibility is back the notice goes`() {
        assertNull(BlockPolicy.decide(input(on(youtube), degraded = false)))
    }

    @Test
    fun `during a lock period the lock's own screen stays`() {
        val d = BlockPolicy.decide(input(on(youtube), timeUp = true))!!
        assertEquals(BlockReason.TIME_UP, d.reason)
        assertFalse(OverlayParentFlow.opensAccessibilitySettings(d))
        assertTrue(BlockPolicy.decide(input(on(whatsapp), timeUp = true, lockType = LockType.WHOLE_DEVICE))!!.wholeDevice)
    }

    @Test
    fun `nothing to unlock means nothing to open`() {
        assertFalse(OverlayParentFlow.opensAccessibilitySettings(null))
    }
}
