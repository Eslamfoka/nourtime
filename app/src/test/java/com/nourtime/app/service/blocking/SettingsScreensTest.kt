package com.nourtime.app.service.blocking

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScreensTest {

    private val own = "com.nourtime.app"

    @Test
    fun `Settings stays even though it has a fallback home screen`() {
        // Seen on the emulator: Android's Settings declares a home screen (FallbackHome), and leaving out
        // every home app dropped Settings itself from the list.
        val found = listOf("com.android.settings", "com.google.android.packageinstaller")
        assertEquals(
            setOf("com.android.settings", "com.google.android.packageinstaller"),
            SettingsScreens.filter(found, own, defaultHome = "com.google.android.apps.nexuslauncher"),
        )
    }

    @Test
    fun `the default home screen, System UI, the chooser and Nour Time are never covered`() {
        val found = listOf("com.example.launcher", "com.android.systemui", "android", own, "com.android.dialer")
        assertEquals(emptySet<String>(), SettingsScreens.filter(found, own, defaultHome = "com.example.launcher"))
    }
}
