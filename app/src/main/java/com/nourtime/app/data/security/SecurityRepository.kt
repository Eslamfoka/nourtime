package com.nourtime.app.data.security

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nourtime.app.core.security.AnswerAttemptPolicy
import com.nourtime.app.core.security.AnswerNormalizer
import com.nourtime.app.core.security.AttemptPolicy
import com.nourtime.app.core.security.HashedSecret
import com.nourtime.app.core.security.PinAttemptPolicy
import com.nourtime.app.core.security.PinAttemptState
import com.nourtime.app.core.security.PinResetWait
import com.nourtime.app.core.security.PinResetWaitState
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.WallClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

sealed interface AnswerCheckResult {
    data object Correct : AnswerCheckResult
    /** [attemptsBeforeLockout] more wrong answers are free; the one after that starts a day's wait. */
    data class Wrong(val attemptsBeforeLockout: Int) : AnswerCheckResult
    data class LockedOut(val remainingMs: Long) : AnswerCheckResult
}

sealed interface PinCheckResult {
    data object Success : PinCheckResult
    data class Wrong(val attemptsBeforeLockout: Int) : PinCheckResult
    data class LockedOut(val remainingMs: Long) : PinCheckResult
    data object NoPin : PinCheckResult
    /** The new PIN from "Forgot PIN?", which should start working at [activeAtMs] (wall clock, shown only). Not a wrong PIN. */
    data class NotYetActive(val activeAtMs: Long) : PinCheckResult
}

/**
 * Where a PIN is typed. Each place counts its own wrong PINs: the lock screen's "For parents" pad is
 * the one a child taps at random, and that mustn't make the parent wait in Nour Time itself. A correct
 * PIN anywhere clears both counts.
 */
enum class PinEntryPoint { APP, LOCK_SCREEN }

/**
 * Parent PIN and security question. Only salted hashes are stored.
 *
 * A PIN set through the security answer ("Forgot PIN?") waits [PIN_RESET_WAIT_MS] before it works, or
 * until the parent's phone approves it; meanwhile the current PIN still works, and using it (or
 * changing the PIN normally) cancels the reset. A child who guessed the answer gains nothing at once,
 * and the parent undoes it simply by unlocking (2026-10-13 audit, H1). The wait is real time the phone
 * was on ([PinResetWait]), so moving the date forward doesn't shorten it.
 */
@Singleton
class SecurityRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val hasher: SecretHasher,
    private val clock: DeviceClock,
    private val wall: WallClock,
) {
    private val attemptMutex = Mutex()

    val hasPin: Flow<Boolean> = store.data.map { it[PIN_HASH] != null }.distinctUntilChanged()

    /** When the PIN from "Forgot PIN?" should start working (wall-clock ms, to show), or null when none is waiting. */
    val pendingPinActiveAt: Flow<Long?> = store.data.map { it.pendingPin()?.activeAtMs }.distinctUntilChanged()

    val securityQuestion: Flow<String?> = store.data.map { it[QUESTION] }.distinctUntilChanged()

    /** Setup, or a change by the parent (who knows the PIN): takes effect at once and cancels a waiting reset. */
    suspend fun setPin(pin: String) {
        val hashed = withContext(Dispatchers.Default) { hasher.hash(pin) }
        store.edit {
            it[PIN_HASH] = hashed.encode()
            it.clearPinAttempts()
            it.clearPendingPin()
        }
    }

    /**
     * After a correct security answer: the new PIN waits [PIN_RESET_WAIT_MS] of real time; returns when
     * it should start working (wall-clock ms, for display only).
     */
    suspend fun setPinAfterRecovery(pin: String): Long {
        val hashed = withContext(Dispatchers.Default) { hasher.hash(pin) }
        val wait = PinResetWait.start(clock.elapsedRealtime(), clock.bootCount())
        val activeAt = nowMs() + wait.remainingMs
        store.edit {
            it[PENDING_PIN_HASH] = hashed.encode()
            it.writeWait(wait)
            it[PENDING_PIN_AT] = activeAt
        }
        return activeAt
    }

    /**
     * Keeps counting a waiting reset while Nour Time runs. The wait is counted from elapsedRealtime, which
     * restarts with the phone, so time not saved before a reboot is lost: saving every
     * [WAIT_CHECKPOINT_MS] means a reboot loses at most that much, and a PIN nobody types still starts
     * working on time. A failed save is logged and tried again at the next checkpoint (the time since
     * the last save is still counted then); one error mustn't stop the counting until Nour Time restarts.
     */
    suspend fun keepPendingPinWaitCounting() {
        store.data.map { it[PENDING_PIN_HASH] != null }.distinctUntilChanged().collectLatest { waiting ->
            while (waiting) {
                try {
                    attemptMutex.withLock { activatePendingIfDue() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    runCatching { Log.w(TAG, "PIN reset wait not saved; trying again later", e) }
                }
                delay(WAIT_CHECKPOINT_MS)
            }
        }
    }

    /** "Approve" from the parent's phone: the waiting PIN works now. False when nothing was waiting. */
    suspend fun approvePendingPin(): Boolean = attemptMutex.withLock {
        var approved = false
        store.edit { prefs ->
            val pending = prefs.pendingPin() ?: return@edit
            prefs.activate(pending)
            approved = true
        }
        approved
    }

    suspend fun verifyPin(pin: String, from: PinEntryPoint = PinEntryPoint.APP): PinCheckResult = attemptMutex.withLock {
        activatePendingIfDue()
        val prefs = store.data.first()
        val stored = prefs[PIN_HASH]?.let(HashedSecret::decode) ?: return PinCheckResult.NoPin
        val pending = prefs.pendingPin()
        val result = checkWithLockout(
            pinAttempts(from),
            PinAttemptPolicy,
            verify = { hasher.verify(pin, stored) },
            // The waiting PIN is neither right nor wrong yet: the screen says when it starts working.
            neutral = { pending != null && HashedSecret.decode(pending.hash)?.let { hasher.verify(pin, it) } == true },
        )
        when (result) {
            is Attempt.Correct -> {
                // The parent knows the current PIN: a waiting reset (perhaps the child's) is cancelled,
                // and the other place's wrong PINs are forgiven too.
                store.edit {
                    it.clearPinAttempts()
                    it.clearPendingPin()
                }
                PinCheckResult.Success
            }
            is Attempt.Neutral -> PinCheckResult.NotYetActive(pending!!.activeAtMs)
            is Attempt.Locked -> PinCheckResult.LockedOut(result.remainingMs)
            is Attempt.Wrong -> PinCheckResult.Wrong(PinAttemptPolicy.attemptsBeforeLockout(result.failures))
        }
    }

    /**
     * Counts the real time since the last count and activates the waiting PIN once the whole wait has
     * passed. The wall clock only updates the date shown; it never decides.
     */
    private suspend fun activatePendingIfDue() {
        if (store.data.first()[PENDING_PIN_HASH] == null) return
        val elapsed = clock.elapsedRealtime()
        val boot = clock.bootCount()
        val now = nowMs()
        store.edit { prefs ->
            val pending = prefs.pendingPin() ?: return@edit
            // Saved by a version that timed the wait by the wall clock, which may already have been moved
            // forward: the wait starts again in full (the current PIN and the parent's approval still work).
            val wait = prefs.readWait()?.let { PinResetWait.advance(it, elapsed, boot) } ?: PinResetWait.start(elapsed, boot)
            if (PinResetWait.isOver(wait)) {
                prefs.activate(pending)
                return@edit
            }
            prefs.writeWait(wait)
            // The date shown moves only by more than a little, so the parent isn't told again for nothing.
            val activeAt = now + wait.remainingMs
            if (abs(activeAt - pending.activeAtMs) > SHOWN_DATE_SLACK_MS) prefs[PENDING_PIN_AT] = activeAt
        }
    }

    private fun MutablePreferences.activate(pending: PendingPin) {
        this[PIN_HASH] = pending.hash
        clearPinAttempts()
        clearPendingPin()
    }

    private fun MutablePreferences.clearPinAttempts() {
        PinEntryPoint.entries.forEach { writeAttempts(pinAttempts(it), PinAttemptPolicy.onSuccess()) }
    }

    private fun MutablePreferences.clearPendingPin() {
        remove(PENDING_PIN_HASH)
        remove(PENDING_PIN_AT)
        remove(PENDING_WAIT_REMAINING)
        remove(PENDING_WAIT_ELAPSED)
        remove(PENDING_WAIT_BOOT)
    }

    /** [activeAtMs] is the wall-clock date shown; the wait itself is [readWait]. */
    private class PendingPin(val hash: String, val activeAtMs: Long)

    private fun Preferences.pendingPin(): PendingPin? {
        val hash = this[PENDING_PIN_HASH] ?: return null
        return PendingPin(hash, this[PENDING_PIN_AT] ?: 0)
    }

    private fun Preferences.readWait(): PinResetWaitState? = PinResetWaitState(
        remainingMs = this[PENDING_WAIT_REMAINING] ?: return null,
        lastElapsed = this[PENDING_WAIT_ELAPSED] ?: return null,
        bootCount = this[PENDING_WAIT_BOOT] ?: return null,
    )

    private fun MutablePreferences.writeWait(wait: PinResetWaitState) {
        this[PENDING_WAIT_REMAINING] = wait.remainingMs
        this[PENDING_WAIT_ELAPSED] = wait.lastElapsed
        this[PENDING_WAIT_BOOT] = wait.bootCount
    }

    private suspend fun nowMs(): Long = wall.now().toInstant().toEpochMilli()

    /** Remaining wrong-PIN lockout in ms at [from], or 0. */
    suspend fun lockoutRemaining(from: PinEntryPoint = PinEntryPoint.APP): Long =
        attemptMutex.withLock { remainingLockout(pinAttempts(from), PinAttemptPolicy) }

    /** Remaining wrong-answer lockout in ms, or 0. */
    suspend fun answerLockoutRemaining(): Long = attemptMutex.withLock { remainingLockout(ANSWER_ATTEMPTS, AnswerAttemptPolicy) }

    /** Wrong answers left before a day's wait, or null when none has been wrong yet. */
    suspend fun answerAttemptsLeft(): Int? = attemptMutex.withLock {
        val failures = store.data.first().readAttempts(ANSWER_ATTEMPTS).failures
        if (failures == 0) null else AnswerAttemptPolicy.attemptsBeforeLockout(failures)
    }

    /** Checks the security answer: three wrong answers, then a day per further one ([AnswerAttemptPolicy]). */
    suspend fun checkAnswer(answer: String): AnswerCheckResult = attemptMutex.withLock {
        val prefs = store.data.first()
        // No answer stored (lost settings): nothing can match, and nothing is counted.
        val stored = prefs[ANSWER_HASH]?.let(HashedSecret::decode)
            ?: return AnswerCheckResult.Wrong(AnswerAttemptPolicy.attemptsBeforeLockout(prefs.readAttempts(ANSWER_ATTEMPTS).failures))
        val r = checkWithLockout(ANSWER_ATTEMPTS, AnswerAttemptPolicy, verify = { hasher.verify(AnswerNormalizer.normalize(answer), stored) })
        when (r) {
            is Attempt.Correct -> AnswerCheckResult.Correct
            is Attempt.Locked -> AnswerCheckResult.LockedOut(r.remainingMs)
            is Attempt.Wrong -> AnswerCheckResult.Wrong(AnswerAttemptPolicy.attemptsBeforeLockout(r.failures))
            is Attempt.Neutral -> AnswerCheckResult.Wrong(0)
        }
    }

    suspend fun setSecurityQuestion(question: String, answer: String) {
        val hashed = withContext(Dispatchers.Default) { hasher.hash(AnswerNormalizer.normalize(answer)) }
        store.edit {
            it[QUESTION] = question.trim()
            it[ANSWER_HASH] = hashed.encode()
        }
    }

    private sealed interface Attempt {
        data object Correct : Attempt
        /** Neither right nor wrong: not counted as a failure. */
        data object Neutral : Attempt
        data class Wrong(val failures: Int) : Attempt
        data class Locked(val remainingMs: Long) : Attempt
    }

    private suspend fun checkWithLockout(
        keys: AttemptKeys,
        policy: AttemptPolicy,
        verify: () -> Boolean,
        neutral: () -> Boolean = { false },
    ): Attempt {
        val now = clock.elapsedRealtime()
        val boot = clock.bootCount()
        val stored = store.data.first().readAttempts(keys)
        val current = policy.clearExpired(policy.rebase(stored, now, boot), now)
        val remaining = policy.remainingLockout(current, now)
        if (remaining > 0) {
            saveAttempts(keys, current)
            return Attempt.Locked(remaining)
        }
        if (withContext(Dispatchers.Default) { verify() }) {
            saveAttempts(keys, policy.onSuccess())
            return Attempt.Correct
        }
        if (withContext(Dispatchers.Default) { neutral() }) {
            saveAttempts(keys, current)
            return Attempt.Neutral
        }
        val failed = policy.onFailure(current, now, boot)
        saveAttempts(keys, failed)
        val lockout = policy.remainingLockout(failed, now)
        return if (lockout > 0) Attempt.Locked(lockout) else Attempt.Wrong(failed.failures)
    }

    private suspend fun remainingLockout(keys: AttemptKeys, policy: AttemptPolicy): Long {
        val now = clock.elapsedRealtime()
        val stored = store.data.first().readAttempts(keys)
        val current = policy.clearExpired(policy.rebase(stored, now, clock.bootCount()), now)
        if (current != stored) saveAttempts(keys, current)
        return policy.remainingLockout(current, now)
    }

    private suspend fun saveAttempts(keys: AttemptKeys, state: PinAttemptState) {
        store.edit { it.writeAttempts(keys, state) }
    }

    /** Persisted wrong-attempt counters; the PIN keeps its original key names. */
    private class AttemptKeys(prefix: String) {
        val failures = intPreferencesKey(prefix + "_failures")
        val lockEnd = longPreferencesKey(prefix + "_lock_end_elapsed")
        val lockDuration = longPreferencesKey(prefix + "_lock_duration")
        val lockBoot = intPreferencesKey(prefix + "_lock_boot")
    }

    private fun Preferences.readAttempts(keys: AttemptKeys) = PinAttemptState(
        failures = this[keys.failures] ?: 0,
        lockoutEndElapsed = this[keys.lockEnd] ?: 0,
        lockoutDurationMs = this[keys.lockDuration] ?: 0,
        bootCount = this[keys.lockBoot] ?: 0,
    )

    private fun MutablePreferences.writeAttempts(keys: AttemptKeys, state: PinAttemptState) {
        this[keys.failures] = state.failures
        this[keys.lockEnd] = state.lockoutEndElapsed
        this[keys.lockDuration] = state.lockoutDurationMs
        this[keys.lockBoot] = state.bootCount
    }

    private fun pinAttempts(from: PinEntryPoint) = when (from) {
        PinEntryPoint.APP -> PIN_ATTEMPTS
        PinEntryPoint.LOCK_SCREEN -> LOCK_SCREEN_PIN_ATTEMPTS
    }

    companion object {
        /** How long a PIN set through the security answer waits, unless the parent's phone approves it. */
        const val PIN_RESET_WAIT_MS = PinResetWait.WAIT_MS

        /** How often a waiting reset's progress is saved, so a reboot loses at most this much of it. */
        const val WAIT_CHECKPOINT_MS = 5 * 60_000L

        /** The shown date (and the parent's notice) changes only when it moves by more than this. */
        private const val SHOWN_DATE_SLACK_MS = 15 * 60_000L

        private const val TAG = "SecurityRepository"

        private val PIN_HASH = stringPreferencesKey("pin_hash")
        private val QUESTION = stringPreferencesKey("security_question")
        private val ANSWER_HASH = stringPreferencesKey("security_answer_hash")
        private val PENDING_PIN_HASH = stringPreferencesKey("pin_pending_hash")
        /** Wall-clock ms the waiting PIN should start working, for display only. */
        private val PENDING_PIN_AT = longPreferencesKey("pin_pending_active_at")
        private val PENDING_WAIT_REMAINING = longPreferencesKey("pin_pending_wait_remaining")
        private val PENDING_WAIT_ELAPSED = longPreferencesKey("pin_pending_wait_elapsed")
        private val PENDING_WAIT_BOOT = intPreferencesKey("pin_pending_wait_boot")
        private val PIN_ATTEMPTS = AttemptKeys("pin")
        private val LOCK_SCREEN_PIN_ATTEMPTS = AttemptKeys("pin_lock_screen")
        private val ANSWER_ATTEMPTS = AttemptKeys("answer")
    }
}
