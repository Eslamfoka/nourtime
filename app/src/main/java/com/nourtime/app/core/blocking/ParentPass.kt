package com.nourtime.app.core.blocking

import com.nourtime.app.core.time.DeviceClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A short, in-memory permission the parent grants from a lock screen or by unlocking Nour Time.
 * It ends after [DURATION_MS] or when the screen turns off, so it can't be left open for the child.
 * - Device pass (PIN): lifts a whole-device lock, and settings protection outside lock periods.
 * - Full pass (PIN + security answer during a lock period): also opens limited apps and Settings.
 */
@Singleton
class ParentPass @Inject constructor(
    private val clock: DeviceClock,
) {
    data class State(val deviceUntil: Long = 0, val fullUntil: Long = 0)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun grantDevice() {
        val until = clock.elapsedRealtime() + DURATION_MS
        _state.update { it.copy(deviceUntil = until) }
    }

    fun grantFull() {
        val until = clock.elapsedRealtime() + DURATION_MS
        _state.value = State(deviceUntil = until, fullUntil = until)
    }

    fun revoke() {
        _state.update { State() }
    }

    fun deviceActive(): Boolean = clock.elapsedRealtime() < maxOf(_state.value.deviceUntil, _state.value.fullUntil)

    fun fullActive(): Boolean = clock.elapsedRealtime() < _state.value.fullUntil

    companion object {
        const val DURATION_MS = 15 * 60_000L
    }
}
