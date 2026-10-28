package com.nourtime.app.service.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nourtime.app.data.onboarding.OnboardingRepository
import com.nourtime.app.service.timer.TimerService
import com.nourtime.app.service.watchdog.WatchdogJobService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Restarts protection after a reboot or an app update (brief §3). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var onboarding: OnboardingRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (BootStart.shouldStart({ onboarding.isComplete.first() }, { message, e -> Log.w(TAG, message, e) })) {
                    WatchdogJobService.schedule(context)
                    TimerService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
