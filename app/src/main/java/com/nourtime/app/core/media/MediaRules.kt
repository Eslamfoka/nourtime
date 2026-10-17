package com.nourtime.app.core.media

/** A limited app's media session as Nour Time sees it: which app, and whether it's playing. */
data class MediaSessionInfo(val packageName: String, val playing: Boolean)

/**
 * Audio from limited apps that isn't on screen: YouTube or Spotify playing in the background, or
 * started again from the media controls in the notification shade (2026-10-17 audit, M2).
 */
object MediaRules {

    /** Limited apps playing right now: they count as in use while the screen is usable (owner, 2026-10-17). */
    fun playingLimited(sessions: Collection<MediaSessionInfo>, limited: Set<String>): Set<String> =
        sessions.filterTo(mutableSetOf()) { it.playing && it.packageName in limited }.mapTo(mutableSetOf()) { it.packageName }

    /**
     * Limited apps to pause now: while limited apps are blocked (lock period, bedtime, or detection
     * degraded), screen on or off, unless the parent has unlocked everything. Apps allowed during
     * the lock aren't limited, so they keep playing.
     */
    fun toPause(sessions: Collection<MediaSessionInfo>, limited: Set<String>, limitedBlocked: Boolean, fullPass: Boolean): Set<String> =
        if (!limitedBlocked || fullPass) emptySet() else playingLimited(sessions, limited)
}
