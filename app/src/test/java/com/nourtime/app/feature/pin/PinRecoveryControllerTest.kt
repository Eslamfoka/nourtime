package com.nourtime.app.feature.pin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.data.security.PinCheckResult
import com.nourtime.app.data.security.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** "Forgot PIN?": the security question, then a new PIN twice. */
class PinRecoveryControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeClock(var now: Long = 10_000) : DeviceClock {
        override fun elapsedRealtime() = now
        override fun bootCount() = 1
    }

    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clock = FakeClock()
    private val security by lazy {
        val store = PreferenceDataStoreFactory.create(scope = io) { tmp.newFile("r.preferences_pb") }
        SecurityRepository(store, SecretHasher(iterations = 1_000), clock)
    }
    private var done = false
    private val recovery by lazy { PinRecoveryController(io, security, clock) { done = true } }

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
        realTime { recovery.answer.state.first { !it.checking && (it.wrong || recovery.stage.value != PinRecoveryStage.ANSWER) } }
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
        assertFalse(done)
        assertEquals(PinCheckResult.Success, security.verifyPin("4827"))
    }

    @Test
    fun `the right answer then a new PIN twice replaces the old PIN`() = runTest {
        answer("Blue ")
        assertEquals(PinRecoveryStage.NEW_PIN, recovery.stage.value)
        type("2580")
        type("2580")
        realTime { recovery.stage.first { it == PinRecoveryStage.DONE } }
        assertTrue(done)
        assertEquals(PinCheckResult.Success, security.verifyPin("2580"))
        assertTrue(security.verifyPin("4827") is PinCheckResult.Wrong)
    }

    @Test
    fun `a mismatched confirmation starts the new PIN again`() = runTest {
        answer("blue")
        type("2580")
        type("1397")
        assertEquals(PinRecoveryStage.NEW_PIN, recovery.stage.value)
        assertFalse(done)
        assertEquals(PinCheckResult.Success, security.verifyPin("4827"))
    }
}
