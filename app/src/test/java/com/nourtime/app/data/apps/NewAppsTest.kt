package com.nourtime.app.data.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class NewAppsTest {

    private val youtube = "com.google.android.youtube"
    private val chrome = "com.android.chrome"
    private val game = "com.example.game"

    private fun check(
        known: Set<String>?,
        launchable: Set<String>,
        on: Boolean = true,
        limited: Set<String> = emptySet(),
        allowed: Set<String> = emptySet(),
        exempt: Set<String> = emptySet(),
    ) = NewApps.check(known, launchable, on, limited, allowed, exempt)

    @Test
    fun `the first look only remembers the apps`() {
        // Otherwise every app on the phone would be limited when protection starts.
        assertEquals(NewAppsResult(toLimit = emptySet(), known = setOf(youtube, chrome)), check(null, setOf(youtube, chrome)))
    }

    @Test
    fun `an app installed later is limited`() {
        // Found in the 2026-10-12 audit: a game or browser installed from Play was never counted.
        assertEquals(setOf(game), check(setOf(youtube, chrome), setOf(youtube, chrome, game)).toLimit)
    }

    @Test
    fun `the new app becomes known`() {
        assertEquals(setOf(youtube, chrome, game), check(setOf(youtube, chrome), setOf(youtube, chrome, game)).known)
    }

    @Test
    fun `nothing is limited when the parent turned it off, but the app is still remembered`() {
        val r = check(setOf(youtube), setOf(youtube, game), on = false)
        assertEquals(emptySet<String>(), r.toLimit)
        assertEquals(setOf(youtube, game), r.known)
    }

    @Test
    fun `apps the parent already limited or allowed are left alone`() {
        val r = check(setOf(youtube), setOf(youtube, game, chrome), limited = setOf(game), allowed = setOf(chrome))
        assertEquals(emptySet<String>(), r.toLimit)
    }

    @Test
    fun `the phone, Settings and the home screen are never limited`() {
        val r = check(setOf(youtube), setOf(youtube, "com.android.dialer"), exempt = setOf("com.android.dialer"))
        assertEquals(emptySet<String>(), r.toLimit)
    }

    @Test
    fun `an app removed and installed again counts as new`() {
        val removed = check(setOf(youtube, game), setOf(youtube))
        assertEquals(setOf(game), check(removed.known, setOf(youtube, game)).toLimit)
    }
}
