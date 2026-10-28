package com.nourtime.app.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
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
    private val hasher = SecretHasher(iterations = 1_000)
    private val store by lazy { PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("test.preferences_pb") } }
    private val repo by lazy { SecurityRepository(store, hasher, clock, wall) }

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
    fun `random taps during a wait aren't counted, so they don't make the parent's wait longer`() = runTest {
        // What the separate counts protected: a child tapping at random mustn't keep the parent out
        // for long. Taps during a wait are refused without counting, so the wait stays the first one...
        repo.setPin("4827")
        repeat(5) { repo.verifyPin("0000") }
        repeat(50) {
            assertEquals(PinCheckResult.LockedOut(30_000L - it * 100L), repo.verifyPin("1111"))
            clock.now += 100
        }
        clock.now += 30_000
        // ...and once it's over the parent's PIN works at once, wherever it's typed.
        assertEquals(0L, repo.lockoutRemaining())
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("0000"))
    }

    /** Writes the lock screen's own count the way versions before 2026-10-28 kept it. */
    private suspend fun legacyLockScreenCount(failures: Int, lockEnd: Long = 0, lockDuration: Long = 0, boot: Int = 1) {
        store.edit {
            it[intPreferencesKey("pin_lock_screen_failures")] = failures
            it[longPreferencesKey("pin_lock_screen_lock_end_elapsed")] = lockEnd
            it[longPreferencesKey("pin_lock_screen_lock_duration")] = lockDuration
            it[intPreferencesKey("pin_lock_screen_lock_boot")] = boot
        }
    }

    @Test
    fun `migration keeps the lock screen's count when it's the stricter one`() = runTest {
        repo.setPin("4827")
        repeat(2) { repo.verifyPin("0000") }
        // The lock screen's pad had 7 wrong PINs and its 5-minute wait has 200 s left.
        legacyLockScreenCount(7, lockEnd = clock.now + 200_000, lockDuration = 5 * 60_000L)
        assertEquals(200_000L, repo.lockoutRemaining())
        assertEquals(PinCheckResult.LockedOut(200_000L), repo.verifyPin("4827"))
        clock.now += 200_000
        // The 8th wrong PIN in all: 15 minutes.
        assertEquals(PinCheckResult.LockedOut(15 * 60_000L), repo.verifyPin("0000"))
        assertNull(store.data.first()[intPreferencesKey("pin_lock_screen_failures")])
    }

    @Test
    fun `migration takes the most failures from one count and the longer wait from the other`() = runTest {
        repo.setPin("4827")
        repeat(5) { repo.verifyPin("0000") }  // the app's pad: 30 s wait running
        // The lock screen's pad: more failures, but its wait is already over.
        legacyLockScreenCount(6, lockEnd = clock.now - 1, lockDuration = 60_000L)
        assertEquals(30_000L, repo.lockoutRemaining())
        clock.now += 30_000
        // 7 wrong PINs in all: 5 minutes.
        assertEquals(PinCheckResult.LockedOut(5 * 60_000L), repo.verifyPin("0000"))
    }

    @Test
    fun `migration restarts a lock screen wait from an earlier boot in full`() = runTest {
        repo.setPin("4827")
        legacyLockScreenCount(5, lockEnd = 999_999_999, lockDuration = 30_000L, boot = 0)
        assertEquals(30_000L, repo.lockoutRemaining())
    }

    @Test
    fun `a correct PIN clears the migrated count too`() = runTest {
        repo.setPin("4827")
        legacyLockScreenCount(4)
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
        assertEquals(AnswerCheckResult.Wrong(2), repo.checkAnswer("طنطا"))
    }

    @Test
    fun `an answer saved under the old 2-letter minimum still works`() = runTest {
        // The 4-letter minimum is checked only when an answer is set or changed, never when one is checked.
        repo.setSecurityQuestion("Initials?", "Al")
        assertEquals(AnswerCheckResult.Correct, repo.checkAnswer(" al "))
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

        clock.now += day - 1
        wallMs += day - 1
        assertEquals(PinCheckResult.NotYetActive(activeAt), repo.verifyPin("1593"))
        clock.now += 1
        assertEquals(PinCheckResult.Success, repo.verifyPin("1593"))
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("4827"))
        assertNull(repo.pendingPinActiveAt.first())
    }

    @Test
    fun `moving the date forward doesn't shorten the wait for a reset PIN`() = runTest {
        repo.setPin("4827")
        repo.setPinAfterRecovery("1593")
        wallMs += 2 * day
        assertTrue(repo.verifyPin("1593") is PinCheckResult.NotYetActive)
        // Nor does a reboot with the date moved forward (automatic time off).
        clock.boot = 2
        clock.now = 1_000
        wallMs += 2 * day
        assertTrue(repo.verifyPin("1593") is PinCheckResult.NotYetActive)
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
    }

    @Test
    fun `a reboot keeps the wait left and doesn't count the time the phone was off`() = runTest {
        repo.setPin("4827")
        val firstShown = repo.setPinAfterRecovery("1593")
        clock.now += 10 * 60 * 60_000L
        wallMs += 10 * 60 * 60_000L
        assertTrue(repo.verifyPin("1593") is PinCheckResult.NotYetActive)
        // Off for five hours, then on again: elapsed time restarts and 14 hours are still left.
        clock.boot = 2
        clock.now = 5_000
        wallMs += 5 * 60 * 60_000L
        val result = repo.verifyPin("1593")
        assertEquals(PinCheckResult.NotYetActive(wallMs + 14 * 60 * 60_000L), result)
        // The date shown moved later by the time off.
        assertEquals(firstShown + 5 * 60 * 60_000L, repo.pendingPinActiveAt.first())
        clock.now += 14 * 60 * 60_000L - 1
        assertTrue(repo.verifyPin("1593") is PinCheckResult.NotYetActive)
        clock.now += 1
        assertEquals(PinCheckResult.Success, repo.verifyPin("1593"))
    }

    @Test
    fun `a reset waiting since an older version starts its wait again`() = runTest {
        repo.setPin("4827")
        // Saved by a version that timed the wait by the wall clock; that time has passed.
        store.edit {
            it[stringPreferencesKey("pin_pending_hash")] = hasher.hash("1593").encode()
            it[longPreferencesKey("pin_pending_active_at")] = wallMs - 1
        }
        assertEquals(PinCheckResult.NotYetActive(wallMs + day), repo.verifyPin("1593"))
        assertEquals(wallMs + day, repo.pendingPinActiveAt.first())
        clock.now += day
        assertEquals(PinCheckResult.Success, repo.verifyPin("1593"))
    }

    @Test
    fun `using the current PIN partway through the wait cancels the reset`() = runTest {
        repo.setPin("4827")
        repo.setPinAfterRecovery("1593")
        clock.now += day / 2
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
        assertNull(repo.pendingPinActiveAt.first())
        clock.now += day
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("1593"))
    }

    @Test
    fun `using the current PIN cancels a pending reset`() = runTest {
        repo.setPin("4827")
        repo.setPinAfterRecovery("1593")
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
        assertNull(repo.pendingPinActiveAt.first())
        clock.now += day
        assertEquals(PinCheckResult.Wrong(3), repo.verifyPin("1593"))
        assertEquals(PinCheckResult.Success, repo.verifyPin("4827"))
    }

    @Test
    fun `changing the PIN normally cancels a pending reset`() = runTest {
        repo.setPin("4827")
        repo.setPinAfterRecovery("1593")
        repo.setPin("2468")
        assertNull(repo.pendingPinActiveAt.first())
        clock.now += day
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

    /** Settings in memory, so the save loop runs on the test's virtual time; [failingWrites] saves throw. */
    private class FlakyStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        var failingWrites = 0
        val current: Preferences get() = state.value
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failingWrites > 0) {
                failingWrites--
                throw IOException("disk full")
            }
            return transform(state.value).also { state.value = it }
        }
    }

    @Test
    fun `while Nour Time runs the wait is saved every five minutes, and a failed save doesn't stop it`() = runTest {
        val flaky = FlakyStore()
        val repo = SecurityRepository(flaky, hasher, clock, wall)
        val checkpoint = SecurityRepository.WAIT_CHECKPOINT_MS
        val remaining = { flaky.current[longPreferencesKey("pin_pending_wait_remaining")] }
        fun fiveMinutesPass() {
            clock.now += checkpoint
            advanceTimeBy(checkpoint)
            runCurrent()
        }
        repo.setPin("4827")
        repo.setPinAfterRecovery("1593")
        backgroundScope.launch { repo.keepPendingPinWaitCounting() }
        runCurrent()
        assertEquals(day, remaining())

        fiveMinutesPass()
        assertEquals(day - checkpoint, remaining())
        // One save fails: nothing is saved this time...
        flaky.failingWrites = 1
        fiveMinutesPass()
        assertEquals(day - checkpoint, remaining())
        // ...and the next one still comes, counting the time since the last save that worked.
        fiveMinutesPass()
        assertEquals(day - 3 * checkpoint, remaining())
    }
}
