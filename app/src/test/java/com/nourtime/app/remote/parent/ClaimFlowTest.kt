package com.nourtime.app.remote.parent

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parent is told to tap "Allow" only after the server has the claim (seen offline on the real project). */
class ClaimFlowTest {

    @Test
    fun `a claim the server never confirms ends as offline without asking to tap Allow`() = runTest {
        var toldToTapAllow = false
        val result = ClaimFlow.run(
            claimOnServer = { awaitCancellation() },
            onClaimed = { toldToTapAllow = true },
            waitForChild = { ClaimResult.PAIRED },
        )
        assertEquals(ClaimResult.OFFLINE, result)
        assertFalse(toldToTapAllow)
    }

    @Test
    fun `a timeout counts as offline even if the claim catches everything`() = runTest {
        var toldToTapAllow = false
        val result = ClaimFlow.run(
            claimOnServer = {
                try {
                    awaitCancellation()
                } catch (e: Exception) {
                    ClaimResult.FAILED
                }
            },
            onClaimed = { toldToTapAllow = true },
            waitForChild = { ClaimResult.PAIRED },
        )
        assertEquals(ClaimResult.OFFLINE, result)
        assertFalse(toldToTapAllow)
    }

    @Test
    fun `after the server confirms, the parent is told to tap Allow and waits for the child`() = runTest {
        val steps = mutableListOf<String>()
        val result = ClaimFlow.run(
            claimOnServer = { steps += "claimed"; null },
            onClaimed = { steps += "tap allow" },
            waitForChild = { steps += "waiting"; ClaimResult.PAIRED },
        )
        assertEquals(ClaimResult.PAIRED, result)
        assertEquals(listOf("claimed", "tap allow", "waiting"), steps)
    }

    @Test
    fun `a code that can't be claimed is reported without waiting`() = runTest {
        var waited = false
        val result = ClaimFlow.run(
            claimOnServer = { ClaimResult.EXPIRED },
            onClaimed = {},
            waitForChild = { waited = true; ClaimResult.PAIRED },
        )
        assertEquals(ClaimResult.EXPIRED, result)
        assertTrue(!waited)
    }
}
