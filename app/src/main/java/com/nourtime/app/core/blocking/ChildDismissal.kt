package com.nourtime.app.core.blocking

import com.nourtime.app.core.detection.ForegroundState

/**
 * The child's "OK" on the lock screen: it hides the screen while what's on screen stays the same.
 * A limited app still on screen (picture in picture, split screen) gets only [graceMs] to be closed;
 * then the screen comes back, so "OK" can't leave a video playing.
 */
class ChildDismissal(private val graceMs: Long = GRACE_MS) {
    private var dismissedFor: Pair<String?, Set<String>>? = null
    private var dismissedAt = 0L

    @Synchronized
    fun dismiss(fg: ForegroundState, nowElapsed: Long) {
        dismissedFor = fg.foreground to fg.visible
        dismissedAt = nowElapsed
    }

    @Synchronized
    fun isHidden(decision: BlockDecision?, fg: ForegroundState, limitedApps: Set<String>, nowElapsed: Long): Boolean {
        if (decision == null) dismissedFor = null
        if (decision == null || decision.wholeDevice || dismissedFor != (fg.foreground to fg.visible)) return false
        if (fg.limitedInUse(limitedApps).isNotEmpty() && nowElapsed - dismissedAt >= graceMs) {
            dismissedFor = null
            return false
        }
        return true
    }

    companion object {
        /** Long enough to drag a picture-in-picture window away. */
        const val GRACE_MS = 15_000L
    }
}
