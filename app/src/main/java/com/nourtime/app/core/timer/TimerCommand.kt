package com.nourtime.app.core.timer

/** Something the parent's phone asks the timer to do (Phase 2). Applied by [TimeRules.apply]. */
sealed interface TimerCommand {
    /** Extra minutes. During a lock period it ends the lock and gives exactly these minutes. */
    data class Bonus(val minutes: Int) : TimerCommand

    /** Starts a full lock period now. */
    data object LockNow : TimerCommand

    /** Ends the lock period now and refills the budget. */
    data object EndLock : TimerCommand
}
