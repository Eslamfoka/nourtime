package com.nourtime.app.service.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nourtime.app.MainActivity
import com.nourtime.app.R
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.core.timer.TimerStatus
import com.nourtime.app.core.ui.formatDuration

/** The silent "protection is on" notification required for the foreground service, plus alerts. */
object ProtectionNotifications {
    const val STATUS_ID = 1
    private const val ALERT_ID = 2
    private const val ADMIN_ALERT_ID = 3
    private const val CHANNEL_STATUS = "protection_status"
    private const val CHANNEL_ALERTS = "protection_alerts"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, context.getString(R.string.notif_channel_status), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.notif_channel_alerts), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun status(context: Context, status: TimerStatus?): Notification {
        val text = when {
            status == null -> null
            status.phase == TimerPhase.LOCKED ->
                context.getString(R.string.notif_locked, context.resources.formatDuration(minutesUp(status.lockRemainingMs)))
            else -> context.getString(R.string.notif_time_left, context.resources.formatDuration(minutesUp(status.remainingMs)))
        }
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Parent-facing alert while protection is degraded (fail-closed mode). */
    fun showDegraded(context: Context) {
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.status_degraded_title))
            .setContentText(context.getString(R.string.notif_degraded_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_degraded_body)))
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ALERT_ID, n) }
    }

    /** Device admin was turned off, so Nour Time can be uninstalled until the parent turns it back on. */
    fun showAdminDisabled(context: Context) {
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.status_degraded_title))
            .setContentText(context.getString(R.string.notif_admin_disabled))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_admin_disabled)))
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ADMIN_ALERT_ID, n) }
    }

    fun clearDegraded(context: Context) {
        NotificationManagerCompat.from(context).cancel(ALERT_ID)
    }

    private fun minutesUp(ms: Long): Int = ((ms + 59_999) / 60_000).toInt()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
