package com.nourtime.app.core.blocking

/**
 * How long a ringing phone may uncover the screen (Nour Time's lock is drawn above the notifications,
 * so a call can only be answered with it out of the way). The ring is read from the audio mode, which
 * repeated calls (WhatsApp rings set it too) or an app could keep setting, so each ring gets at most
 * [MAX_RING_MS] and all rings together at most [BUDGET_MS] per [WINDOW_MS] (2026-10-13 audit).
 */
class RingingGrace {
    private var ringStart: Long? = null
    /** Uncovered time of past rings: (when the ring ended, how long it was uncovered). */
    private val past = ArrayDeque<Pair<Long, Long>>()

    /** Whether the screen stays uncovered for the ring now; [nowElapsed] is elapsedRealtime. */
    @Synchronized
    fun exempt(ringing: Boolean, nowElapsed: Long): Boolean {
        while (past.isNotEmpty() && nowElapsed - past.first().first >= WINDOW_MS) past.removeFirst()
        val start = ringStart
        if (!ringing) {
            if (start != null) past.addLast(nowElapsed to (nowElapsed - start).coerceIn(0, MAX_RING_MS))
            ringStart = null
            return false
        }
        if (start == null) ringStart = nowElapsed
        val ringMs = nowElapsed - (start ?: nowElapsed)
        val used = past.sumOf { it.second }
        return ringMs < MAX_RING_MS && used + ringMs < BUDGET_MS
    }

    companion object {
        const val MAX_RING_MS = 60_000L
        const val BUDGET_MS = 3 * 60_000L
        const val WINDOW_MS = 30 * 60_000L
    }
}
