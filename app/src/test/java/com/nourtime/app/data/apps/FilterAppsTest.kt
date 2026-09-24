package com.nourtime.app.data.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterAppsTest {

    private val apps = listOf(
        InstalledApp("com.android.chrome", "Chrome"),
        InstalledApp("com.facebook.katana", "Facebook"),
        InstalledApp("com.zhiliaoapp.musically", "TikTok"),
        InstalledApp("com.google.android.youtube", "YouTube"),
        InstalledApp("com.example.quran", "القرآن الكريم"),
    )

    private fun labels(list: List<InstalledApp>) = list.map { it.label }

    @Test
    fun `empty query returns everything in order`() {
        assertEquals(labels(apps), labels(filterApps(apps, "  ", emptySet())))
    }

    @Test
    fun `matches label ignoring case and spaces`() {
        assertEquals(listOf("YouTube"), labels(filterApps(apps, "you tube", emptySet())))
        assertEquals(listOf("TikTok"), labels(filterApps(apps, "TIKT", emptySet())))
    }

    @Test
    fun `matches package name`() {
        assertEquals(listOf("TikTok"), labels(filterApps(apps, "musically", emptySet())))
    }

    @Test
    fun `matches arabic ignoring alef forms`() {
        assertEquals(listOf("القرآن الكريم"), labels(filterApps(apps, "القران", emptySet())))
    }

    @Test
    fun `pinned apps come first, keeping alphabetical order within groups`() {
        val result = filterApps(apps, "", setOf("com.google.android.youtube", "com.facebook.katana"))
        assertEquals(listOf("Facebook", "YouTube", "Chrome", "TikTok", "القرآن الكريم"), labels(result))
    }
}
