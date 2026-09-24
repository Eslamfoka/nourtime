package com.nourtime.app.data.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SecurityRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeClock(var now: Long = 10_000, var boot: Int = 1) : DeviceClock {
        override fun elapsedRealtime() = now
        override fun bootCount() = boot
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clock = FakeClock()
    private val repo by lazy {
        val store = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("test.preferences_pb") }
        SecurityRepository(store, SecretHasher(iterations = 1_000), clock)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `no pin until one is set`() = runTest {
        assertFalse(repo.hasPin.first())
        assertEquals(PinCheckResult.NoPin, repo.verifyPin("4827"))
        repo.setPin("4827")
        assertTrue(repo.hasPin.first())
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
    }

    @Test
    fun `wrong pins count down then lock out`() = runTest {
        repo.setPin("4827")
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("0000"))
        assertEquals(PinCheckResult.Wrong(2), repo.verifyPin("0000"))
        assertEquals(PinCheckResult.Wrong(1), repo.verifyPin("0000"))
        assertEquals(PinCheckResult.Wrong(0), repo.verifyPin("0000"))
        assertEquals(PinCheckResult.LockedOut(30_000), repo.verifyPin("0000"))
        // Even the right PIN is refused while locked out.
        clock.now += 10_000
        assertEquals(PinCheckResult.LockedOut(20_000), repo.verifyPin("4827"))
        assertEquals(20_000L, repo.lockoutRemaining())
    }

    @Test
    fun `correct pin after lockout resets the counter`() = runTest {
        repo.setPin("4827")
        repeat(5) { repo.verifyPin("0000") }
        clock.now += 30_000
        assertEquals(0L, repo.lockoutRemaining())
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("0000"))
    }

    @Test
    fun `reboot does not clear a lockout`() = runTest {
        repo.setPin("4827")
        repeat(5) { repo.verifyPin("0000") }
        clock.now = 1_000
        clock.boot = 2
        assertEquals(30_000L, repo.lockoutRemaining())
    }

    @Test
    fun `security answer is checked after normalization`() = runTest {
        repo.setSecurityQuestion("  اسم قرية جدتي؟ ", "أبو حمص")
        assertEquals("اسم قرية جدتي؟", repo.securityQuestion.first())
        assertEquals(AnswerCheckResult.Correct, repo.checkAnswer("ابو  حمص"))
        assertEquals(AnswerCheckResult.Wrong, repo.checkAnswer("طنطا"))
    }

    @Test
    fun `wrong answers lock out like the PIN, independently of it`() = runTest {
        repo.setPin("4827")
        repo.setSecurityQuestion("Question?", "blue")
        repeat(4) { assertEquals(AnswerCheckResult.Wrong, repo.checkAnswer("red")) }
        assertEquals(AnswerCheckResult.LockedOut(30_000), repo.checkAnswer("red"))
        assertEquals(AnswerCheckResult.LockedOut(30_000), repo.checkAnswer("blue"))
        // The PIN has its own counter.
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
        clock.now += 30_000
        assertEquals(AnswerCheckResult.Correct, repo.checkAnswer("BLUE"))
    }
}
