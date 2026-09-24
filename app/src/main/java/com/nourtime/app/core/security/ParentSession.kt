package com.nourtime.app.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the parent has entered the PIN in this app session. Leaving the app briefly (for example to
 * grant a permission in Settings) keeps the session; staying away longer than [graceMs] locks it again.
 * Times are elapsedRealtime values.
 */
@Singleton
class ParentSession(private val graceMs: Long) {

    @Inject
    constructor() : this(DEFAULT_GRACE_MS)

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var backgroundedAt: Long? = null

    fun unlock() {
        backgroundedAt = null
        _isUnlocked.value = true
    }

    fun lock() {
        backgroundedAt = null
        _isUnlocked.value = false
    }

    fun onBackground(nowElapsed: Long) {
        if (_isUnlocked.value) backgroundedAt = nowElapsed
    }

    fun onForeground(nowElapsed: Long) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (nowElapsed - since > graceMs) _isUnlocked.value = false
    }

    companion object {
        const val DEFAULT_GRACE_MS = 2 * 60_000L
    }
}
