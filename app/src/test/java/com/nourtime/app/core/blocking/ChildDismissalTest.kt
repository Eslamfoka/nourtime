package com.nourtime.app.core.blocking

import com.nourtime.app.core.detection.ForegroundState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildDismissalTest {

    private val youtube = "com.google.android.youtube"
    private val launcher = "com.google.android.apps.nexuslauncher"
    private val limited = setOf(youtube)

    private val timeUp = BlockDecision(BlockReason.TIME_UP, wholeDevice = false, needsSecurityAnswer = true)
    private val settingsBlock = BlockDecision(BlockReason.SYSTEM_SETTINGS, wholeDevice = false, needsSecurityAnswer = false)
    private val wholePhone = BlockDecision(BlockReason.TIME_UP, wholeDevice = true, needsSecurityAnswer = false)

    private fun on(fg: String, vararg visible: String) = ForegroundState(foreground = fg, visible = setOf(fg, *visible))

    @Test
    fun `nothing is hidden before OK`() {
        assertFalse(ChildDismissal(graceMs = 15_000).isHidden(timeUp, on(launcher, youtube), limited, nowElapsed = 0))
    }

    @Test
    fun `OK keeps the screen hidden while nothing changes`() {
        val d = ChildDismissal(graceMs = 15_000)
        val settings = on("com.android.settings")
        d.dismiss(settings, nowElapsed = 0)
        assertTrue(d.isHidden(settingsBlock, settings, limited, nowElapsed = 600_000))
    }

    @Test
    fun `OK hides a limited app in picture in picture only for the grace period`() {
        val d = ChildDismissal(graceMs = 15_000)
        val pip = on(launcher, youtube)
        d.dismiss(pip, nowElapsed = 1_000)
        assertTrue(d.isHidden(timeUp, pip, limited, nowElapsed = 15_999))
        assertFalse(d.isHidden(timeUp, pip, limited, nowElapsed = 16_000))
        // Once back, it stays back: no new grace period without another OK.
        assertFalse(d.isHidden(timeUp, pip, limited, nowElapsed = 17_000))
    }

    @Test
    fun `a whole phone lock is never hidden`() {
        val d = ChildDismissal(graceMs = 15_000)
        d.dismiss(on(launcher), nowElapsed = 0)
        assertFalse(d.isHidden(wholePhone, on(launcher), limited, nowElapsed = 1))
    }

    @Test
    fun `a different screen shows the lock again`() {
        val d = ChildDismissal(graceMs = 15_000)
        d.dismiss(on("com.android.settings"), nowElapsed = 0)
        assertFalse(d.isHidden(settingsBlock, on("com.android.settings", "com.android.chrome"), limited, nowElapsed = 1))
    }

    @Test
    fun `the dismissal ends once nothing is blocked`() {
        val d = ChildDismissal(graceMs = 15_000)
        val settings = on("com.android.settings")
        d.dismiss(settings, nowElapsed = 0)
        assertFalse(d.isHidden(null, settings, limited, nowElapsed = 1))
        assertFalse(d.isHidden(settingsBlock, settings, limited, nowElapsed = 2))
    }
}
