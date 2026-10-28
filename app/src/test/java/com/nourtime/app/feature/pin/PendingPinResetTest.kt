package com.nourtime.app.feature.pin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.WallClock
import com.nourtime.app.data.security.PinCheckResult
import com.nourtime.app.data.security.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class PendingPinResetTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeClock(var now: Long = 10_000) : DeviceClock {
        override fun elapsedRealtime() = now
        override fun bootCount() = 1
    }

    private val wallMs = 1_800_000_000_000L
    private val day = 24 * 60 * 60_000L
    private val clock = FakeClock()
    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val security by lazy {
        val store = PreferenceDataStoreFactory.create(scope = io) { tmp.newFile("r.preferences_pb") }
        val wall = WallClock { ZonedDateTime.ofInstant(Instant.ofEpochMilli(wallMs), ZoneOffset.UTC) }
        SecurityRepository(store, SecretHasher(iterations = 1_000), clock, wall)
    }

    @After
    fun tearDown() = io.cancel()

    private suspend fun StateFlow<Long?>.becomes(value: Long?) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { first { it == value } } }

    @Test
    fun `nothing is shown until a reset waits, then its date until the current PIN cancels it`() = runTest {
        security.setPin("4827")
        val banner = PendingPinReset(io, security)
        banner.activeAt.becomes(null)

        security.setPinAfterRecovery("1593")
        assertEquals(wallMs + day, banner.activeAt.becomes(wallMs + day))

        assertEquals(PinCheckResult.Success, security.verifyPin("4827"))
        assertEquals(null, banner.activeAt.becomes(null))
    }

    @Test
    fun `the banner goes when the reset completes`() = runTest {
        security.setPin("4827")
        val banner = PendingPinReset(io, security)
        security.setPinAfterRecovery("1593")
        banner.activeAt.becomes(wallMs + day)
        clock.now += day
        assertEquals(PinCheckResult.Success, security.verifyPin("1593"))
        banner.activeAt.becomes(null)
    }

    @Test
    fun `the banner goes when the parent's phone approves the reset`() = runTest {
        security.setPin("4827")
        val banner = PendingPinReset(io, security)
        security.setPinAfterRecovery("1593")
        banner.activeAt.becomes(wallMs + day)
        security.approvePendingPin()
        banner.activeAt.becomes(null)
    }

    @Test
    fun `cancelling from Home removes the reset and keeps the current PIN`() = runTest {
        security.setPin("4827")
        val banner = PendingPinReset(io, security)
        security.setPinAfterRecovery("1593")
        banner.activeAt.becomes(wallMs + day)
        banner.cancel()
        banner.activeAt.becomes(null)
        clock.now += day
        assertEquals(PinCheckResult.Wrong(3), security.verifyPin("1593"))
        assertEquals(PinCheckResult.Success, security.verifyPin("4827"))
        // Nothing left to cancel.
        assertFalse(security.cancelPendingPin())
    }
}
