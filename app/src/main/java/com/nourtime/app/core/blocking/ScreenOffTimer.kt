package com.nourtime.app.core.blocking

import com.nourtime.app.data.settings.LockType

/**
 * Whole-device mode turns the screen off when a lock period starts (brief §3), after the child has
 * seen the "Time's up" screen for [delayMs] so the change isn't abrupt. It fires once per lock
 * period start: not when protection starts mid-period (e.g. after a reboot), and not again each
 * time the screen comes back on.
 */
class ScreenOffTimer(private val delayMs: Long = DEFAULT_DELAY_MS) {
    private var wasLockPeriod: Boolean? = null
    private var dueAt: Long? = null

    /** Returns true when the screen should be turned off now. */
    fun update(
        nowElapsed: Long,
        lockPeriod: Boolean,
        lockType: LockType,
        wholeDeviceBlocking: Boolean,
        screenOn: Boolean,
    ): Boolean {
        if (wasLockPeriod == false && lockPeriod && lockType == LockType.WHOLE_DEVICE) dueAt = nowElapsed + delayMs
        wasLockPeriod = lockPeriod
        val due = dueAt ?: return false
        // Cancelled when the period ended or the screen is already off.
        if (!lockPeriod || !screenOn) {
            dueAt = null
            return false
        }
        if (nowElapsed < due) return false
        dueAt = null
        // Only if the whole-device lock covers the screen right now: not during a parent pass or
        // while Nour Time itself is open. Brief moments before that (e.g. switching apps) don't matter.
        return wholeDeviceBlocking
    }

    companion object {
        const val DEFAULT_DELAY_MS = 8_000L
    }
}
