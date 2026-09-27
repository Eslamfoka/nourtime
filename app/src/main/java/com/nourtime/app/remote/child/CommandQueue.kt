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
     * Commands to act on, oldest first. Only the current owner's commands take effect: one from a
     * removed parent (or sent before another parent paired) is consumed without effect.
     */
    fun due(ownerUid: String, docs: List<CommandDoc>, alreadyApplied: Set<String>): List<DueCommand> = docs
        .filter { it.id !in alreadyApplied && it.createdAtMs != null }
        .sortedWith(compareBy<CommandDoc> { it.createdAtMs }.thenBy { it.id })
        .map { DueCommand(it.id, if (it.data["by"] == ownerUid) remoteCommandOf(it.data) else null) }

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
     * while it was offline) or another parent was confirmed. A snapshot that isn't loaded yet isn't
     * a removal.
     */
    fun stillPaired(owner: PairedOwner, docExists: Boolean, docOwnerUid: String?): Boolean =
        !docExists || docOwnerUid == owner.uid
}
