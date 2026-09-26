package com.nourtime.app.remote.model

import com.nourtime.app.core.timer.TimerCommand

/** Bonus minutes allowed per command; the Firestore rules enforce the same range. */
val BONUS_MINUTES = 1..240

/** A command document from `devices/{id}/commands` as a [TimerCommand], or null if invalid or unknown. */
fun remoteCommandOf(m: Map<String, Any?>): TimerCommand? = when (m["type"]) {
    "BONUS" -> (m["minutes"] as? Number)?.toInt()?.takeIf { it in BONUS_MINUTES }?.let(TimerCommand::Bonus)
    "LOCK_NOW" -> TimerCommand.LockNow
    "END_LOCK" -> TimerCommand.EndLock
    else -> null
}

/** The fields the parent's phone writes for [command] (plus `createdAt` and `by`). */
fun commandMap(command: TimerCommand): Map<String, Any> = when (command) {
    is TimerCommand.Bonus -> mapOf("type" to "BONUS", "minutes" to command.minutes)
    TimerCommand.LockNow -> mapOf("type" to "LOCK_NOW")
    TimerCommand.EndLock -> mapOf("type" to "END_LOCK")
}
