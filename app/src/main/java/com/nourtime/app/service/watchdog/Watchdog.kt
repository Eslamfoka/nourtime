package com.nourtime.app.service.watchdog

import com.nourtime.app.data.mode.AppMode

enum class WatchdogAction { NONE, START }

/**
 * Whether the watchdog job should start protection: Android can kill Nour Time (a slow start at boot,
 * low memory) and then nothing starts it again until the app is opened.
 */
object Watchdog {
    fun action(mode: AppMode?, setupComplete: Boolean, timerRunning: Boolean): WatchdogAction =
        if (mode == AppMode.CHILD && setupComplete && !timerRunning) WatchdogAction.START else WatchdogAction.NONE
}
