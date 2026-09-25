package com.nourtime.app.service.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Turns the screen off through Device admin's force-lock policy (brief §3). */
@Singleton
class ScreenLocker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Returns false when Device admin isn't active (the overlay still covers the phone). */
    fun lockNow(): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isAdminActive(ComponentName(context, NourDeviceAdminReceiver::class.java))) return false
        return runCatching { dpm.lockNow() }
            .onFailure { Log.w("ScreenLocker", "lockNow failed", it) }
            .isSuccess
    }
}
