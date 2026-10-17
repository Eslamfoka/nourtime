package com.nourtime.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaRulesTest {

    private val limited = setOf("com.google.android.youtube", "com.spotify.music")
    private val sessions = listOf(
        MediaSessionInfo("com.google.android.youtube", playing = true),
        MediaSessionInfo("com.spotify.music", playing = false),
        // Allowed during the lock (not limited): an audiobook app.
        MediaSessionInfo("com.example.stories", playing = true),
    )

    @Test
    fun `only limited apps that are playing count`() {
        assertEquals(setOf("com.google.android.youtube"), MediaRules.playingLimited(sessions, limited))
    }

    @Test
    fun `while limited apps are blocked, their playing media is paused, nobody else's`() {
        assertEquals(setOf("com.google.android.youtube"), MediaRules.toPause(sessions, limited, limitedBlocked = true, fullPass = false))
    }

    @Test
    fun `nothing is paused with time left or once the parent unlocked everything`() {
        assertEquals(emptySet<String>(), MediaRules.toPause(sessions, limited, limitedBlocked = false, fullPass = false))
        assertEquals(emptySet<String>(), MediaRules.toPause(sessions, limited, limitedBlocked = true, fullPass = true))
    }

    @Test
    fun `an app no longer limited is left alone`() {
        assertEquals(emptySet<String>(), MediaRules.toPause(sessions, setOf("com.spotify.music"), limitedBlocked = true, fullPass = false))
    }
}
