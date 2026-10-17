package com.nourtime.app.data.security

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
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.WallClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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
    /** The new PIN from "Forgot PIN?", which starts working at [activeAtMs] (wall clock). Not a wrong PIN. */
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
 * and the parent undoes it simply by unlocking (2026-10-13 audit, H1).
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

    /** When the PIN from "Forgot PIN?" starts working (wall-clock ms), or null when none is waiting. */
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

    /** After a correct security answer: the new PIN waits; returns when it starts working (wall-clock ms). */
    suspend fun setPinAfterRecovery(pin: String): Long {
        val hashed = withContext(Dispatchers.Default) { hasher.hash(pin) }
        val activeAt = nowMs() + PIN_RESET_WAIT_MS
        store.edit {
            it[PENDING_PIN_HASH] = hashed.encode()
            it[PENDING_PIN_AT] = activeAt
        }
        return activeAt
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

    private suspend fun activatePendingIfDue() {
        val now = nowMs()
        store.edit { prefs ->
            val pending = prefs.pendingPin() ?: return@edit
            if (now >= pending.activeAtMs) prefs.activate(pending)
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
    }

    private class PendingPin(val hash: String, val activeAtMs: Long)

    private fun Preferences.pendingPin(): PendingPin? {
        val hash = this[PENDING_PIN_HASH] ?: return null
        val at = this[PENDING_PIN_AT] ?: return null
        return PendingPin(hash, at)
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
        const val PIN_RESET_WAIT_MS = 24 * 60 * 60_000L

        private val PIN_HASH = stringPreferencesKey("pin_hash")
        private val QUESTION = stringPreferencesKey("security_question")
        private val ANSWER_HASH = stringPreferencesKey("security_answer_hash")
        private val PENDING_PIN_HASH = stringPreferencesKey("pin_pending_hash")
        private val PENDING_PIN_AT = longPreferencesKey("pin_pending_active_at")
        private val PIN_ATTEMPTS = AttemptKeys("pin")
        private val LOCK_SCREEN_PIN_ATTEMPTS = AttemptKeys("pin_lock_screen")
        private val ANSWER_ATTEMPTS = AttemptKeys("answer")
    }
}
