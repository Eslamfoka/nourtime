package com.nourtime.app.core.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundRulesTest {

    private val ignored = setOf("com.android.systemui", "com.google.android.inputmethod.latin")
    private val limited = setOf("com.google.android.youtube", "com.zhiliaoapp.musically")

    private fun app(pkg: String, active: Boolean = false) = AppWindow(pkg, isApplication = true, isActive = active)
    private fun system(pkg: String) = AppWindow(pkg, isApplication = false, isActive = false)

    @Test
    fun `full screen app is foreground and visible`() {
        val s = ForegroundRules.fromWindows(listOf(app("com.google.android.youtube", active = true)), ignored, ForegroundState())
        assertEquals("com.google.android.youtube", s.foreground)
        assertEquals(setOf("com.google.android.youtube"), s.limitedInUse(limited))
    }

    @Test
    fun `picture in picture counts even when launcher is focused`() {
        val s = ForegroundRules.fromWindows(
            listOf(app("com.google.android.youtube"), app("com.google.android.apps.nexuslauncher", active = true)),
            ignored,
            ForegroundState(),
        )
        assertEquals("com.google.android.apps.nexuslauncher", s.foreground)
        assertEquals(setOf("com.google.android.youtube"), s.limitedInUse(limited))
    }

    @Test
    fun `split screen counts both apps`() {
        val s = ForegroundRules.fromWindows(
            listOf(app("com.android.chrome", active = true), app("com.zhiliaoapp.musically")),
            ignored,
            ForegroundState(),
        )
        assertEquals(setOf("com.zhiliaoapp.musically"), s.limitedInUse(limited))
    }

    @Test
    fun `keyboard and system windows are ignored`() {
        val s = ForegroundRules.fromWindows(
            listOf(system("com.google.android.inputmethod.latin"), app("com.google.android.youtube", active = true), system("com.android.systemui")),
            ignored,
            ForegroundState(),
        )
        assertEquals(setOf("com.google.android.youtube"), s.visible)
    }

    @Test
    fun `notification shade over an app keeps the previous app`() {
        val before = ForegroundRules.fromWindows(listOf(app("com.google.android.youtube", active = true)), ignored, ForegroundState())
        val shade = ForegroundRules.fromWindows(listOf(system("com.android.systemui")), ignored, before)
        assertEquals(before, shade)
    }

    @Test
    fun `nour time's own screens stop the countdown`() {
        val before = ForegroundRules.fromWindows(listOf(app("com.google.android.youtube", active = true)), ignored, ForegroundState())
        val after = ForegroundRules.fromWindows(listOf(app("com.nourtime.app", active = true)), ignored, before)
        assertEquals("com.nourtime.app", after.foreground)
        assertEquals(emptySet<String>(), after.limitedInUse(limited))
    }

    @Test
    fun `nothing counts while the screen is off or locked`() {
        val s = ForegroundRules.fromWindows(listOf(app("com.google.android.youtube", active = true)), ignored, ForegroundState())
        assertEquals(emptySet<String>(), s.copy(screen = ScreenState(interactive = false, keyguardLocked = false)).limitedInUse(limited))
        assertEquals(emptySet<String>(), s.copy(screen = ScreenState(interactive = true, keyguardLocked = true)).limitedInUse(limited))
    }

    @Test
    fun `usage events give the last resumed non-ignored app`() {
        val events = listOf(
            "com.google.android.youtube" to true,
            "com.google.android.youtube" to false,
            "com.google.android.apps.nexuslauncher" to true,
            "com.android.systemui" to true,
        )
        assertEquals("com.google.android.apps.nexuslauncher", ForegroundRules.lastResumed(events, ignored))
        assertEquals(null, ForegroundRules.lastResumed(emptyList(), ignored))
    }

    // Windows whose owner can't be read (null root) must not freeze detection on the previous app.

    private fun unknown(id: Int, active: Boolean = false) = AppWindow(null, isApplication = true, isActive = active, windowId = id)

    @Test
    fun `unknown active window is filled from the package seen in its events`() {
        val resolved = ForegroundRules.resolveUnknown(listOf(unknown(7, active = true)), mapOf(7 to "com.google.android.youtube")) {
            error("fallback not needed")
        }
        assertEquals(listOf(AppWindow("com.google.android.youtube", isApplication = true, isActive = true, windowId = 7)), resolved)
    }

    @Test
    fun `unknown active window without events falls back to usage stats`() {
        val resolved = ForegroundRules.resolveUnknown(listOf(system("com.android.systemui"), unknown(7, active = true)), emptyMap()) {
            "com.google.android.youtube"
        }
        assertEquals("com.google.android.youtube", resolved.single { it.isActive }.packageName)
    }

    @Test
    fun `known windows are left alone and the fallback is not asked`() {
        val windows = listOf(app("com.android.chrome", active = true), system("com.android.systemui"))
        assertEquals(windows, ForegroundRules.resolveUnknown(windows, emptyMap()) { error("fallback not needed") })
    }

    @Test
    fun `failed window read falls back to usage stats`() {
        val resolved = ForegroundRules.resolveUnknown(null, emptyMap()) { "com.google.android.youtube" }
        assertEquals(listOf(AppWindow("com.google.android.youtube", isApplication = true, isActive = true)), resolved)
    }

    @Test
    fun `failed window read with no fallback gives nothing`() {
        assertEquals(emptyList<AppWindow>(), ForegroundRules.resolveUnknown(null, emptyMap()) { null })
    }

    @Test
    fun `limited app is detected when its window owner is unreadable and Nour Time was on screen before`() {
        val previous = ForegroundState(foreground = "com.nourtime.app", visible = setOf("com.nourtime.app"))
        val resolved = ForegroundRules.resolveUnknown(listOf(unknown(3, active = true)), emptyMap()) { "com.google.android.youtube" }
        val s = ForegroundRules.fromWindows(resolved, ignored, previous)
        assertEquals("com.google.android.youtube", s.foreground)
        assertEquals(setOf("com.google.android.youtube"), s.limitedInUse(limited))
    }
}
