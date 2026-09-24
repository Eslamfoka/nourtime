package com.nourtime.app.core.blocking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a lock period (time up or bedtime) is running right now. Written by the block
 * coordinator; read by the app's unlock screen, which then also asks the security question.
 */
@Singleton
class LockPeriodState @Inject constructor() {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun set(active: Boolean) {
        _active.value = active
    }
}
