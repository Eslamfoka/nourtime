package com.nourtime.app.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nourtime.app.core.security.AnswerNormalizer
import com.nourtime.app.core.security.HashedSecret
import com.nourtime.app.core.security.PinAttemptPolicy
import com.nourtime.app.core.security.PinAttemptState
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
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
    data object Wrong : AnswerCheckResult
    data class LockedOut(val remainingMs: Long) : AnswerCheckResult
}

sealed interface PinCheckResult {
    data object Success : PinCheckResult
    data class Wrong(val attemptsBeforeLockout: Int) : PinCheckResult
    data class LockedOut(val remainingMs: Long) : PinCheckResult
    data object NoPin : PinCheckResult
}

/** Parent PIN and security question. Only salted hashes are stored. */
@Singleton
class SecurityRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val hasher: SecretHasher,
    private val clock: DeviceClock,
) {
    private val attemptMutex = Mutex()

    val hasPin: Flow<Boolean> = store.data.map { it[PIN_HASH] != null }.distinctUntilChanged()

    val securityQuestion: Flow<String?> = store.data.map { it[QUESTION] }.distinctUntilChanged()

    suspend fun setPin(pin: String) {
        val hashed = withContext(Dispatchers.Default) { hasher.hash(pin) }
        store.edit {
            it[PIN_HASH] = hashed.encode()
            it.writeAttempts(PIN_ATTEMPTS, PinAttemptPolicy.onSuccess())
        }
    }

    suspend fun verifyPin(pin: String): PinCheckResult = attemptMutex.withLock {
        val stored = store.data.first()[PIN_HASH]?.let(HashedSecret::decode) ?: return PinCheckResult.NoPin
        when (val r = checkWithLockout(PIN_ATTEMPTS) { hasher.verify(pin, stored) }) {
            is Attempt.Correct -> PinCheckResult.Success
            is Attempt.Locked -> PinCheckResult.LockedOut(r.remainingMs)
            is Attempt.Wrong -> PinCheckResult.Wrong(PinAttemptPolicy.attemptsBeforeLockout(r.failures))
        }
    }

    /** Remaining wrong-PIN lockout in ms, or 0. */
    suspend fun lockoutRemaining(): Long = attemptMutex.withLock { remainingLockout(PIN_ATTEMPTS) }

    /** Remaining wrong-answer lockout in ms, or 0. */
    suspend fun answerLockoutRemaining(): Long = attemptMutex.withLock { remainingLockout(ANSWER_ATTEMPTS) }

    /** Checks the security answer with the same escalating delay as the PIN, so it can't be guessed. */
    suspend fun checkAnswer(answer: String): AnswerCheckResult = attemptMutex.withLock {
        val stored = store.data.first()[ANSWER_HASH]?.let(HashedSecret::decode) ?: return AnswerCheckResult.Wrong
        when (val r = checkWithLockout(ANSWER_ATTEMPTS) { hasher.verify(AnswerNormalizer.normalize(answer), stored) }) {
            is Attempt.Correct -> AnswerCheckResult.Correct
            is Attempt.Locked -> AnswerCheckResult.LockedOut(r.remainingMs)
            is Attempt.Wrong -> AnswerCheckResult.Wrong
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
        data class Wrong(val failures: Int) : Attempt
        data class Locked(val remainingMs: Long) : Attempt
    }

    private suspend fun checkWithLockout(keys: AttemptKeys, verify: () -> Boolean): Attempt {
        val now = clock.elapsedRealtime()
        val boot = clock.bootCount()
        val stored = store.data.first().readAttempts(keys)
        val current = PinAttemptPolicy.clearExpired(PinAttemptPolicy.rebase(stored, now, boot), now)
        val remaining = PinAttemptPolicy.remainingLockout(current, now)
        if (remaining > 0) {
            saveAttempts(keys, current)
            return Attempt.Locked(remaining)
        }
        if (withContext(Dispatchers.Default) { verify() }) {
            saveAttempts(keys, PinAttemptPolicy.onSuccess())
            return Attempt.Correct
        }
        val failed = PinAttemptPolicy.onFailure(current, now, boot)
        saveAttempts(keys, failed)
        val lockout = PinAttemptPolicy.remainingLockout(failed, now)
        return if (lockout > 0) Attempt.Locked(lockout) else Attempt.Wrong(failed.failures)
    }

    private suspend fun remainingLockout(keys: AttemptKeys): Long {
        val now = clock.elapsedRealtime()
        val stored = store.data.first().readAttempts(keys)
        val current = PinAttemptPolicy.clearExpired(PinAttemptPolicy.rebase(stored, now, clock.bootCount()), now)
        if (current != stored) saveAttempts(keys, current)
        return PinAttemptPolicy.remainingLockout(current, now)
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

    private companion object {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val QUESTION = stringPreferencesKey("security_question")
        val ANSWER_HASH = stringPreferencesKey("security_answer_hash")
        val PIN_ATTEMPTS = AttemptKeys("pin")
        val ANSWER_ATTEMPTS = AttemptKeys("answer")
    }
}
