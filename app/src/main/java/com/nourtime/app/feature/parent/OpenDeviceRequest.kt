package com.nourtime.app.feature.parent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A tapped push names a child's phone: the parent's screen opens it (also when the app was already open). */
object OpenDeviceRequest {
    const val EXTRA_DEVICE_ID = "com.nourtime.app.OPEN_DEVICE"

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

    fun request(id: String) {
        _deviceId.value = id
    }

    /** The requested phone, once. */
    fun consume(): String? = _deviceId.value.also { _deviceId.value = null }
}
