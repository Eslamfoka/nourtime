package com.nourtime.app.data.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SecurityRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeClock(var now: Long = 10_000, var boot: Int = 1) : DeviceClock {
        override fun elapsedRealtime() = now
        override fun bootCount() = boot
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clock = FakeClock()
    private var wallMs = 1_800_000_000_000L
    private val wall = WallClock { ZonedDateTime.ofInstant(Instant.ofEpochMilli(wallMs), ZoneOffset.UTC) }
    private val day = 24 * 60 * 60_000L
    private val repo by lazy {
        val store = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("test.preferences_pb") }
        SecurityRepository(store, SecretHasher(iterations = 1_000), clock, wall)
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
    fun `wrong PINs on the lock screen don't make the parent wait in the app`() = runTest {
        repo.setPin("4827")
        // A child tapping at random, waiting out each lockout, up to the longest one.
        repeat(9) {
            repo.verifyPin("0000", PinEntryPoint.LOCK_SCREEN)
            clock.now += 60 * 60_000L
        }
        repo.verifyPin("0000", PinEntryPoint.LOCK_SCREEN)
        assertEquals(60 * 60_000L, repo.lockoutRemaining(PinEntryPoint.LOCK_SCREEN))
        assertEquals(0L, repo.lockoutRemaining(PinEntryPoint.APP))
        // The app's pad still has all its free attempts, and the right PIN works there at once.
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("1111", PinEntryPoint.APP))
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827", PinEntryPoint.APP))
        // The parent is proven: the lock screen's count is forgiven too.
        assertEquals(0L, repo.lockoutRemaining(PinEntryPoint.LOCK_SCREEN))
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("0000", PinEntryPoint.LOCK_SCREEN))
    }

    @Test
    fun `wrong PINs in the app don't lock the lock screen's pad`() = runTest {
        repo.setPin("4827")
        repeat(5) { repo.verifyPin("0000") }
        assertEquals(30_000L, repo.lockoutRemaining(PinEntryPoint.APP))
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827", PinEntryPoint.LOCK_SCREEN))
        assertEquals(0L, repo.lockoutRemaining(PinEntryPoint.APP))
    }

    @Test
    fun `reboot does not clear a lock screen lockout`() = runTest {
        repo.setPin("4827")
        repeat(5) { repo.verifyPin("0000", PinEntryPoint.LOCK_SCREEN) }
        clock.now = 1_000
        clock.boot = 2
        assertEquals(30_000L, repo.lockoutRemaining(PinEntryPoint.LOCK_SCREEN))
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
        assertEquals(AnswerCheckResult.Wrong(2), repo.checkAnswer("طنطا"))
    }

    @Test
    fun `three wrong answers, then answers are locked for a day, independently of the PIN`() = runTest {
        repo.setPin("4827")
        repo.setSecurityQuestion("Question?", "blue")
        assertNull(repo.answerAttemptsLeft())
        assertEquals(AnswerCheckResult.Wrong(2), repo.checkAnswer("red"))
        assertEquals(AnswerCheckResult.Wrong(1), repo.checkAnswer("red"))
        assertEquals(AnswerCheckResult.Wrong(0), repo.checkAnswer("red"))
        assertEquals(0, repo.answerAttemptsLeft())
        assertEquals(AnswerCheckResult.LockedOut(day), repo.checkAnswer("red"))
        assertEquals(AnswerCheckResult.LockedOut(day), repo.checkAnswer("blue"))
        // The PIN has its own counter.
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
        clock.now += day
        assertEquals(AnswerCheckResult.Correct, repo.checkAnswer("BLUE"))
    }

    @Test
    fun `a PIN reset through the answer waits a day, while the current PIN keeps working`() = runTest {
        repo.setPin("4827")
        val activeAt = repo.setPinAfterRecovery("1593")
        assertEquals(wallMs + day, activeAt)
        assertEquals(activeAt, repo.pendingPinActiveAt.first())
        // Not yet: the new PIN isn't accepted (and isn't counted as a wrong PIN).
        assertEquals(PinCheckResult.NotYetActive(activeAt), repo.verifyPin("1593"))
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("0000"))

        wallMs += day
        assertEquals(PinCheckResult.Success, repo.verifyPin("1593"))
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("4827"))
        assertNull(repo.pendingPinActiveAt.first())
    }

    @Test
    fun `using the current PIN cancels a pending reset`() = runTest {
        repo.setPin("4827")
        repo.setPinAfterRecovery("1593")
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
        assertNull(repo.pendingPinActiveAt.first())
        wallMs += day
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("1593"))
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
    }

    @Test
    fun `changing the PIN normally cancels a pending reset`() = runTest {
        repo.setPin("4827")
        repo.setPinAfterRecovery("1593")
        repo.setPin("2468")
        assertNull(repo.pendingPinActiveAt.first())
        wallMs += day
        assertEquals(PinCheckResult.Success, repo.verifyPin("2468"))
    }

    @Test
    fun `approval from the parent's phone activates the new PIN at once`() = runTest {
        repo.setPin("4827")
        repo.setPinAfterRecovery("1593")
        assertTrue(repo.approvePendingPin())
        assertEquals(PinCheckResult.Success, repo.verifyPin("1593"))
        assertNull(repo.pendingPinActiveAt.first())
        // Nothing left to approve.
        assertFalse(repo.approvePendingPin())
    }
}
