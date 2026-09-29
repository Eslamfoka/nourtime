package com.nourtime.app.feature.pin

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Forgot PIN?" tapped on a lock overlay: the overlay opens Nour Time, whose unlock screen then starts
 * PIN recovery (also when the app was already open). A request only counts for a minute, so it can't
 * surprise someone later.
 */
object ForgotPinRequest {
    private const val VALID_MS = 60_000L

    private val _requestedAt = MutableStateFlow<Long?>(null)
    val requestedAt: StateFlow<Long?> = _requestedAt.asStateFlow()

    fun request() {
        _requestedAt.value = SystemClock.elapsedRealtime()
    }

    /** True once for a recent request. */
    fun consume(): Boolean {
        val at = _requestedAt.value ?: return false
        _requestedAt.value = null
        return SystemClock.elapsedRealtime() - at <= VALID_MS
    }
}
