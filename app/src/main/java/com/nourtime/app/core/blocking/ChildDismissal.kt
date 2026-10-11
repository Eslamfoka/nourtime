package com.nourtime.app.core.blocking

import com.nourtime.app.core.detection.ForegroundState

/**
 * The child's "OK" on the lock screen: it hides the screen while the same apps stay on screen (focus
 * moving between them doesn't count, or the screen would flicker).
 * A limited app still on screen (picture in picture, split screen) gets only [graceMs] to be closed;
 * then the screen comes back, so "OK" can't leave a video playing.
 */
class ChildDismissal(private val graceMs: Long = GRACE_MS) {
    private var dismissedFor: Set<String>? = null
    private var dismissedAt = 0L

    @Synchronized
    fun dismiss(fg: ForegroundState, nowElapsed: Long) {
        dismissedFor = onScreen(fg)
        dismissedAt = nowElapsed
    }

    @Synchronized
    fun isHidden(decision: BlockDecision?, fg: ForegroundState, limitedApps: Set<String>, nowElapsed: Long): Boolean {
        if (decision == null) dismissedFor = null
        if (decision == null || decision.wholeDevice || dismissedFor != onScreen(fg)) return false
        if (fg.limitedInUse(limitedApps).isNotEmpty() && nowElapsed - dismissedAt >= graceMs) {
            dismissedFor = null
            return false
        }
        return true
    }

    private fun onScreen(fg: ForegroundState) = fg.visible + listOfNotNull(fg.foreground)

    companion object {
        /** Long enough to drag a picture-in-picture window away. */
        const val GRACE_MS = 15_000L
    }
}
