package com.nourtime.app.feature.parent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nourtime.app.MainActivity
import com.nourtime.app.R
import com.nourtime.app.remote.parent.PushMessage
import com.nourtime.app.service.timer.message

/** Pushes on the parent's phone: the child asks for more time, an alert, a new PIN waiting for approval. */
object ParentNotifications {
    const val CHANNEL = "parent_updates"

    /** Pushes show only with the app's notifications on and their channel not muted (null: not created yet). */
    fun pushesOn(appEnabled: Boolean, channelImportance: Int?): Boolean =
        appEnabled && channelImportance != NotificationManager.IMPORTANCE_NONE

    /** Whether a push would show now, for the parent's home screen. */
    fun pushesOn(context: Context): Boolean {
        val importance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL)?.importance
        } else {
            null
        }
        return pushesOn(NotificationManagerCompat.from(context).areNotificationsEnabled(), importance)
    }

    /** True when only the channel is muted (the app's notifications are on). */
    fun channelMuted(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() && !pushesOn(context)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // Creating an existing channel keeps what the parent set for it.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.parent_push_channel), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun show(context: Context, message: PushMessage) {
        ensureChannel(context)
        val name = message.deviceName.ifBlank { context.getString(R.string.ask_parent_your_child) }
        val (title, body) = when (message) {
            is PushMessage.Ask -> context.getString(R.string.parent_push_ask_title) to context.getString(R.string.parent_push_ask_body, name)
            is PushMessage.Alert -> context.getString(R.string.parent_push_alert_title, name) to context.getString(message.alert.message)
            is PushMessage.PinResetPending -> context.getString(R.string.device_pin_reset_title) to
                context.getString(R.string.parent_push_pin_body, name, time(context, message.untilMs))
            is PushMessage.Quiet -> context.getString(R.string.parent_push_quiet_title, name) to
                context.getString(R.string.parent_push_quiet_body, name, time(context, message.lastSeenMs))
        }
        val open = PendingIntent.getActivity(
            context,
            message.notificationId,
            Intent(context, MainActivity::class.java)
                .putExtra(OpenDeviceRequest.EXTRA_DEVICE_ID, message.deviceId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Without the permission (Android 13+) nothing shows; the parent's home screen says so.
        runCatching { NotificationManagerCompat.from(context).notify(message.notificationId, notification) }
    }

    private fun time(context: Context, ms: Long): String = DateUtils.formatDateTime(
        context,
        ms,
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL,
    )
}
