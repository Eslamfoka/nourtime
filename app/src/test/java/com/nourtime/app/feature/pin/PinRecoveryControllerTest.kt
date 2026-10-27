package com.nourtime.app.feature.pin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.WallClock
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import com.nourtime.app.data.security.PinCheckResult
import com.nourtime.app.data.security.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** "Forgot PIN?": the security question, then a new PIN twice, which starts working a day later. */
class PinRecoveryControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeClock(var now: Long = 10_000) : DeviceClock {
        override fun elapsedRealtime() = now
        override fun bootCount() = 1
    }

    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clock = FakeClock()
    @Volatile private var wallMs = 1_800_000_000_000L
    private val day = 24 * 60 * 60_000L
    private val security by lazy {
        val store = PreferenceDataStoreFactory.create(scope = io) { tmp.newFile("r.preferences_pb") }
        val wall = WallClock { ZonedDateTime.ofInstant(Instant.ofEpochMilli(wallMs), ZoneOffset.UTC) }
        SecurityRepository(store, SecretHasher(iterations = 1_000), clock, wall)
    }
    // Set on the controller's thread, just before the stage turns DONE.
    @Volatile private var resets = 0
    private val recovery by lazy { PinRecoveryController(io, security, clock, onPinReset = { resets++ }) }

    @Before
    fun setUp() = runTest {
        security.setPin("4827")
        security.setSecurityQuestion("Favourite colour?", "blue")
    }

    @After
    fun tearDown() = io.cancel()

    private suspend fun answer(text: String) {
        recovery.answer.onAnswerChange(text)
        recovery.answer.submit()
        // Watch both flows: "checking" ends just before the stage moves on, so waiting on the answer
        // state alone could miss the stage change and time out.
        realTime {
            combine(recovery.answer.state, recovery.stage) { s, stage -> !s.checking && (s.wrong || stage != PinRecoveryStage.ANSWER) }
                .first { it }
        }
    }

    private fun type(pin: String) = pin.forEach(recovery::onPinDigit)

    /** The repository does real I/O, so waits must use real time, not the test's virtual clock. */
    private suspend fun <T> realTime(block: suspend () -> T): T = withContext(Dispatchers.Default) { withTimeout(5_000) { block() } }

    @Test
    fun `a wrong answer can't change the PIN`() = runTest {
        answer("red")
        assertEquals(PinRecoveryStage.ANSWER, recovery.stage.value)
        type("2580")
        type("2580")
        assertEquals(PinRecoveryStage.ANSWER, recovery.stage.value)
        assertEquals(PinCheckResult.Success, security.verifyPin("4827"))
    }

    @Test
    fun `the right answer then a new PIN twice saves it to start working a day later`() = runTest {
        answer("Blue ")
        assertEquals(PinRecoveryStage.NEW_PIN, recovery.stage.value)
        type("2580")
        type("2580")
        realTime { recovery.stage.first { it == PinRecoveryStage.DONE } }
        // The parent hears about it (2026-10-12 audit: a child who knows the answer could take over).
        assertEquals(1, resets)
        val activeAt = recovery.activeAt.value
        assertEquals(wallMs + day, activeAt)
        // Nothing opens now (2026-10-13 audit, H1): the new PIN waits a day.
        assertEquals(PinCheckResult.NotYetActive(activeAt!!), security.verifyPin("2580"))
        clock.now += day
        assertEquals(PinCheckResult.Success, security.verifyPin("2580"))
        assertTrue(security.verifyPin("4827") is PinCheckResult.Wrong)
    }

    @Test
    fun `a mismatched confirmation starts the new PIN again`() = runTest {
        answer("blue")
        type("2580")
        type("1397")
        assertEquals(PinRecoveryStage.NEW_PIN, recovery.stage.value)
        assertEquals(PinCheckResult.Success, security.verifyPin("4827"))
    }
}
