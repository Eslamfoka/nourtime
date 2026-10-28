package com.nourtime.app.service.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nourtime.app.MainActivity
import com.nourtime.app.R
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.core.timer.TimerStatus
import com.nourtime.app.core.ui.formatDuration
import com.nourtime.app.data.protection.ProtectionAlert

/** The silent "protection is on" notification required for the foreground service, plus alerts. */
object ProtectionNotifications {
    const val STATUS_ID = 1
    private const val ALERT_ID = 2
    private const val ADMIN_ALERT_ID = 3
    private const val NEW_APPS_ID = 4
    private const val PROTECTION_ALERT_ID = 5
    private const val STOPPED_ID = 9
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
        notify(context, ALERT_ID, n)
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
        notify(context, ADMIN_ALERT_ID, n)
    }

    /** Newly installed apps now share the time budget; the parent can untick them in Apps. */
    fun showNewAppsLimited(context: Context, labels: List<String>) {
        val text = context.getString(R.string.notif_new_apps_limited, labels.joinToString(context.getString(R.string.list_separator)))
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_new_apps_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        notify(context, NEW_APPS_ID, n)
    }

    /** Protection was off (a restart without Nour Time, another user) or can be turned off (USB debugging). */
    fun showProtectionAlert(context: Context, alert: ProtectionAlert) {
        val text = context.getString(alert.message)
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.status_degraded_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .build()
        notify(context, PROTECTION_ALERT_ID + alert.ordinal, n)
    }

    /** The watchdog couldn't restart protection from the background: opening Nour Time restarts it. */
    fun showProtectionStopped(context: Context) {
        val text = context.getString(R.string.notif_protection_stopped)
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.status_degraded_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        notify(context, STOPPED_ID, n)
    }

    /** Whether the parent and child can see Nour Time's notifications (the app's and the status channel). */
    fun visible(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_STATUS)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun clearDegraded(context: Context) {
        NotificationManagerCompat.from(context).cancel(ALERT_ID)
    }

    /** Posts only when allowed: the parent may have skipped the notifications step. */
    fun notify(context: Context, id: Int, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun minutesUp(ms: Long): Int = ((ms + 59_999) / 60_000).toInt()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** What the parent reads about [ProtectionAlert], on both phones. */
val ProtectionAlert.message: Int
    get() = when (this) {
        ProtectionAlert.USB_DEBUGGING -> R.string.alert_usb_debugging
        ProtectionAlert.MISSED_RESTART -> R.string.alert_missed_restart
        ProtectionAlert.OTHER_USER -> R.string.alert_other_user
        ProtectionAlert.NOTIFICATIONS_OFF -> R.string.alert_notifications_off
        ProtectionAlert.PIN_RESET -> R.string.alert_pin_reset
        ProtectionAlert.PROTECTION_DEGRADED -> R.string.alert_protection_degraded
    }
