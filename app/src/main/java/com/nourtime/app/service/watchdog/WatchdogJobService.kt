package com.nourtime.app.service.watchdog

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nourtime.app.data.mode.AppModeRepository
import com.nourtime.app.data.onboarding.OnboardingRepository
import com.nourtime.app.service.timer.ProtectionNotifications
import com.nourtime.app.service.timer.TimerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Every 15 minutes, and after a reboot (the job is persisted), starts protection if it isn't running.
 * If Android doesn't allow that from the background, it tells the parent instead of failing silently.
 */
@AndroidEntryPoint
class WatchdogJobService : JobService() {

    @Inject lateinit var modes: AppModeRepository
    @Inject lateinit var onboarding: OnboardingRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                val action = Watchdog.action(modes.mode.first(), onboarding.isComplete.first(), TimerService.running)
                if (action == WatchdogAction.START) {
                    Log.w(TAG, "protection wasn't running; starting it")
                    if (!TimerService.start(this@WatchdogJobService)) {
                        ProtectionNotifications.showProtectionStopped(this@WatchdogJobService)
                    }
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Watchdog"
        private const val JOB_ID = 4801
        private const val PERIOD_MS = 15 * 60_000L

        /** Idempotent: an existing schedule is kept, so its timing isn't reset on every start. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, WatchdogJobService::class.java))
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build()
            runCatching { scheduler.schedule(job) }.onFailure { Log.w(TAG, "couldn't schedule the watchdog", it) }
        }
    }
}
