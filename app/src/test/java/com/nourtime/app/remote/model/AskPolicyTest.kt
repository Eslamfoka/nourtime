package com.nourtime.app.remote.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 4c: the child asks for more time on the "Time's up" screen; the parent answers. */
class AskPolicyTest {

    private val now = 10_000_000L
    private val min = 60_000L

    private fun req(status: String, createdAgo: Long, answeredAgo: Long? = null, minutes: Int? = null) =
        TimeRequest("r1", status, createdAtMs = now - createdAgo, answeredAtMs = answeredAgo?.let { now - it }, minutes = minutes)

    @Test
    fun `without a request the child can ask`() {
        assertEquals(AskState.CanAsk, AskPolicy.state(null, now))
    }

    @Test
    fun `a pending request waits for the parent`() {
        assertEquals(AskState.Waiting, AskPolicy.state(req("pending", createdAgo = 5 * min), now))
    }

    @Test
    fun `a request the parent never answered expires`() {
        assertEquals(AskState.CanAsk, AskPolicy.state(req("pending", createdAgo = AskPolicy.PENDING_EXPIRES_MS + 1), now))
    }

    @Test
    fun `a request whose server time hasn't arrived yet is waiting`() {
        assertEquals(AskState.Waiting, AskPolicy.state(TimeRequest("r1", "pending", createdAtMs = null, answeredAtMs = null, minutes = null), now))
    }

    @Test
    fun `after "not now" the child can ask again only after a pause`() {
        val declined = req("declined", createdAgo = 5 * min, answeredAgo = 2 * min)
        assertEquals(AskState.Declined(retryAtMs = now - 2 * min + AskPolicy.RETRY_AFTER_DECLINE_MS), AskPolicy.state(declined, now))
        val later = req("declined", createdAgo = 20 * min, answeredAgo = AskPolicy.RETRY_AFTER_DECLINE_MS)
        assertEquals(AskState.CanAsk, AskPolicy.state(later, now))
    }

    @Test
    fun `an approval is shown for a moment, then the child can ask again`() {
        assertEquals(AskState.Approved(15), AskPolicy.state(req("approved", createdAgo = 3 * min, answeredAgo = 30_000, minutes = 15), now))
        assertEquals(AskState.CanAsk, AskPolicy.state(req("approved", createdAgo = 90 * min, answeredAgo = 80 * min, minutes = 15), now))
    }

    @Test
    fun `an approval from before this lock started doesn't show on the new lock`() {
        val approved = req("approved", createdAgo = 3 * min, answeredAgo = 60_000, minutes = 15)
        // The parent locked the phone again 30 s after approving: this lock can ask again.
        assertEquals(AskState.CanAsk, AskPolicy.state(approved, now, lockStartedAtMs = now - 30_000))
        // The lock the child asked from started before the answer: the approval shows.
        assertEquals(AskState.Approved(15), AskPolicy.state(approved, now, lockStartedAtMs = now - 5 * min))
    }
}
