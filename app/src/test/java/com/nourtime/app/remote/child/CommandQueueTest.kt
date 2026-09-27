package com.nourtime.app.remote.child

import com.nourtime.app.core.timer.TimerCommand
import org.junit.Assert.assertEquals
import org.junit.Test

/** Review Focus 1: stale or duplicate commands apply exactly once, in the order they were sent. */
class CommandQueueTest {

    private fun doc(id: String, createdAt: Long?, type: String, minutes: Long? = null) =
        CommandDoc(id, createdAt, buildMap {
            put("type", type)
            if (minutes != null) put("minutes", minutes)
        })

    @Test
    fun `applies in the order the parent sent them`() {
        val due = CommandQueue.due(
            listOf(doc("b", 2_000, "LOCK_NOW"), doc("a", 1_000, "BONUS", 15), doc("c", 3_000, "END_LOCK")),
            alreadyApplied = emptySet(),
        )
        assertEquals(listOf("a", "b", "c"), due.map { it.id })
        assertEquals(listOf(TimerCommand.Bonus(15), TimerCommand.LockNow, TimerCommand.EndLock), due.map { it.command })
    }

    @Test
    fun `a command delivered again is skipped`() {
        val due = CommandQueue.due(listOf(doc("a", 1_000, "BONUS", 15), doc("b", 2_000, "LOCK_NOW")), alreadyApplied = setOf("a"))
        assertEquals(listOf("b"), due.map { it.id })
    }

    @Test
    fun `invalid commands are consumed without effect`() {
        val due = CommandQueue.due(listOf(doc("x", 1_000, "BONUS", 999), doc("y", 2_000, "WIPE")), alreadyApplied = emptySet())
        assertEquals(listOf("x", "y"), due.map { it.id })
        assertEquals(listOf(null, null), due.map { it.command })
    }

    @Test
    fun `commands without a server time yet wait`() {
        val due = CommandQueue.due(listOf(doc("a", null, "LOCK_NOW"), doc("b", 1_000, "END_LOCK")), alreadyApplied = emptySet())
        assertEquals(listOf("b"), due.map { it.id })
    }

    @Test
    fun `remembers a bounded number of applied ids`() {
        val ids = (1..80).map { "id$it" }
        val kept = CommandQueue.remember(emptyList(), ids)
        assertEquals(CommandQueue.REMEMBERED, kept.size)
        assertEquals("id80", kept.last())
    }
}
