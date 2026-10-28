package com.nourtime.app.data.apps

import com.nourtime.app.feature.setup.appsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Browsers are found by what opens a web link, suggested for limiting, and never limited for the parent. */
class BrowsersTest {

    private val chrome = InstalledApp("com.android.chrome", "Chrome")
    private val samsung = InstalledApp("com.sec.android.app.sbrowser", "Samsung Internet")
    private val youtube = InstalledApp("com.google.android.youtube", "YouTube")
    private val quran = InstalledApp("com.quran.labs.androidquran", "Quran")
    private val all = listOf(chrome, quran, samsung, youtube)

    @Test
    fun `browsers are the launchable apps that open web links, once each`() {
        val resolved = listOf(chrome.packageName, samsung.packageName, chrome.packageName, "com.android.htmlviewer", "com.nourtime.app")
        val launchable = all.map { it.packageName } + "com.nourtime.app"
        assertEquals(setOf(chrome.packageName, samsung.packageName), Browsers.among(resolved, launchable, ownPackage = "com.nourtime.app"))
    }

    @Test
    fun `no browser installed, nothing found`() {
        assertEquals(emptySet<String>(), Browsers.among(emptyList(), all.map { it.packageName }, "com.nourtime.app"))
    }

    @Test
    fun `browsers already limited or allowed during the lock aren't suggested`() {
        val browsers = setOf(chrome.packageName, samsung.packageName, quran.packageName)
        assertEquals(
            setOf(samsung.packageName),
            Browsers.unlimited(browsers, limited = setOf(chrome.packageName), allowedDuringLock = setOf(quran.packageName)),
        )
    }

    @Test
    fun `the list marks browsers and suggests the unlimited ones without choosing them`() {
        val browsers = setOf(chrome.packageName, samsung.packageName)
        val state = appsUiState(all, browsers, query = "", pinned = emptySet(), limited = setOf(youtube.packageName), allowedDuringLock = emptySet())
        assertEquals(listOf(chrome, samsung), state.unlimitedBrowsers)
        assertTrue(state.rows.single { it.app == chrome }.browser)
        assertFalse(state.rows.single { it.app == youtube }.browser)
        // Suggested, not pre-selected: only the parent's choice is limited.
        assertFalse(state.rows.single { it.app == chrome }.checked)
        assertEquals(1, state.limitedCount)
    }

    @Test
    fun `once the browsers are limited the warning goes`() {
        val browsers = setOf(chrome.packageName, samsung.packageName)
        val state = appsUiState(all, browsers, "", emptySet(), limited = browsers, allowedDuringLock = emptySet())
        assertEquals(emptyList<InstalledApp>(), state.unlimitedBrowsers)
        assertTrue(state.rows.single { it.app == samsung }.checked)
    }

    @Test
    fun `searching doesn't hide the warning, only rows`() {
        val state = appsUiState(all, setOf(chrome.packageName), "quran", emptySet(), emptySet(), emptySet())
        assertEquals(listOf(quran), state.rows.map { it.app })
        assertEquals(listOf(chrome), state.unlimitedBrowsers)
    }
}
