package com.nourtime.app.core.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Tamper-resistant time source. Uses [SystemClock.elapsedRealtime] (not the wall clock, which the
 * child could change) plus the boot count, so callers can tell when a reboot reset elapsed time.
 */
interface DeviceClock {
    fun elapsedRealtime(): Long
    fun bootCount(): Int
}

class AndroidDeviceClock(private val context: Context) : DeviceClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}
