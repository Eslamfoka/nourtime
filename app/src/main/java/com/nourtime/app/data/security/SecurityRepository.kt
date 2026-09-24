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
            it.writeAttempts(PinAttemptPolicy.onSuccess())
        }
    }

    suspend fun verifyPin(pin: String): PinCheckResult = attemptMutex.withLock {
        val prefs = store.data.first()
        val stored = prefs[PIN_HASH]?.let(HashedSecret::decode) ?: return PinCheckResult.NoPin
        val now = clock.elapsedRealtime()
        val boot = clock.bootCount()

        val current = PinAttemptPolicy.clearExpired(PinAttemptPolicy.rebase(prefs.readAttempts(), now, boot), now)
        val remaining = PinAttemptPolicy.remainingLockout(current, now)
        if (remaining > 0) {
            saveAttempts(current)
            return PinCheckResult.LockedOut(remaining)
        }

        val correct = withContext(Dispatchers.Default) { hasher.verify(pin, stored) }
        if (correct) {
            saveAttempts(PinAttemptPolicy.onSuccess())
            return PinCheckResult.Success
        }

        val failed = PinAttemptPolicy.onFailure(current, now, boot)
        saveAttempts(failed)
        val lockout = PinAttemptPolicy.remainingLockout(failed, now)
        if (lockout > 0) PinCheckResult.LockedOut(lockout)
        else PinCheckResult.Wrong(PinAttemptPolicy.attemptsBeforeLockout(failed.failures))
    }

    /** Remaining wrong-PIN lockout in ms, or 0. */
    suspend fun lockoutRemaining(): Long = attemptMutex.withLock {
        val now = clock.elapsedRealtime()
        val stored = store.data.first().readAttempts()
        val current = PinAttemptPolicy.clearExpired(PinAttemptPolicy.rebase(stored, now, clock.bootCount()), now)
        if (current != stored) saveAttempts(current)
        PinAttemptPolicy.remainingLockout(current, now)
    }

    suspend fun setSecurityQuestion(question: String, answer: String) {
        val hashed = withContext(Dispatchers.Default) { hasher.hash(AnswerNormalizer.normalize(answer)) }
        store.edit {
            it[QUESTION] = question.trim()
            it[ANSWER_HASH] = hashed.encode()
        }
    }

    suspend fun verifyAnswer(answer: String): Boolean {
        val stored = store.data.first()[ANSWER_HASH]?.let(HashedSecret::decode) ?: return false
        return withContext(Dispatchers.Default) { hasher.verify(AnswerNormalizer.normalize(answer), stored) }
    }

    private suspend fun saveAttempts(state: PinAttemptState) {
        store.edit { it.writeAttempts(state) }
    }

    private fun Preferences.readAttempts() = PinAttemptState(
        failures = this[FAILURES] ?: 0,
        lockoutEndElapsed = this[LOCK_END] ?: 0,
        lockoutDurationMs = this[LOCK_DURATION] ?: 0,
        bootCount = this[LOCK_BOOT] ?: 0,
    )

    private fun MutablePreferences.writeAttempts(state: PinAttemptState) {
        this[FAILURES] = state.failures
        this[LOCK_END] = state.lockoutEndElapsed
        this[LOCK_DURATION] = state.lockoutDurationMs
        this[LOCK_BOOT] = state.bootCount
    }

    private companion object {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val QUESTION = stringPreferencesKey("security_question")
        val ANSWER_HASH = stringPreferencesKey("security_answer_hash")
        val FAILURES = intPreferencesKey("pin_failures")
        val LOCK_END = longPreferencesKey("pin_lock_end_elapsed")
        val LOCK_DURATION = longPreferencesKey("pin_lock_duration")
        val LOCK_BOOT = intPreferencesKey("pin_lock_boot")
    }
}
