package com.nourtime.app.remote.child

import com.nourtime.app.core.timer.TimerCommand
import com.nourtime.app.remote.model.remoteCommandOf

/** A command document from `devices/{id}/commands` that isn't marked applied yet. */
data class CommandDoc(val id: String, val createdAtMs: Long?, val data: Map<String, Any?>)

/** A command to apply now; [command] is null for an invalid one, which is only marked applied. */
data class DueCommand(val id: String, val command: TimerCommand?)

/**
 * Orders and de-duplicates commands from the parent's phone (Phase 2). Each id applies once, in the
 * order the parent sent them, even if the phone was offline for hours or a document is delivered
 * twice; commands whose server time hasn't arrived yet wait for the next snapshot.
 */
object CommandQueue {

    /** How many applied ids are remembered locally (beyond Firestore's own appliedAt). */
    const val REMEMBERED = 50

    /**
     * A command that reaches the phone later than this (it was off or offline) is consumed without
     * effect: a "lock now" sent at night must not lock the phone the next morning.
     */
    const val EXPIRES_AFTER_MS = 60 * 60_000L

    /** For the parent's phone: an applied command that arrived too late did nothing. */
    fun expired(createdAtMs: Long, appliedAtMs: Long): Boolean = appliedAtMs - createdAtMs > EXPIRES_AFTER_MS

    /**
     * Commands to act on, oldest first. Only the current owner's commands take effect: one from a
     * removed parent (or sent before another parent paired) is consumed without effect, and so is one
     * older than [EXPIRES_AFTER_MS] at [nowMs] (wall time).
     */
    fun due(ownerUid: String, nowMs: Long, docs: List<CommandDoc>, alreadyApplied: Set<String>): List<DueCommand> = docs
        .filter { it.id !in alreadyApplied && it.createdAtMs != null }
        .sortedWith(compareBy<CommandDoc> { it.createdAtMs }.thenBy { it.id })
        .map { doc ->
            val live = doc.data["by"] == ownerUid && nowMs - doc.createdAtMs!! <= EXPIRES_AFTER_MS
            DueCommand(doc.id, if (live) remoteCommandOf(doc.data) else null)
        }

    /**
     * Remembers each command before applying it, one at a time: if the process dies in between, the
     * command is lost rather than applied twice (at most once, e.g. never a double bonus).
     */
    suspend fun runEach(due: List<DueCommand>, remember: suspend (String) -> Unit, apply: suspend (TimerCommand) -> Unit) {
        for (c in due) {
            remember(c.id)
            c.command?.let { apply(it) }
        }
    }

    fun remember(existing: List<String>, newIds: List<String>): List<String> =
        (existing + newIds).distinct().takeLast(REMEMBERED)
}

object PairingCheck {
    /**
     * False once the device document no longer names [owner]: the parent removed this phone (maybe
     * while it was offline) or another parent was confirmed; or the server says the document is gone
     * (its data was deleted). A missing document in the local cache only means "not loaded yet".
     */
    fun stillPaired(owner: PairedOwner, docExists: Boolean, fromCache: Boolean, docOwnerUid: String?): Boolean =
        if (docExists) docOwnerUid == owner.uid else fromCache
}
