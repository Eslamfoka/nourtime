package com.nourtime.app.service.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.nourtime.app.R
import com.nourtime.app.service.timer.ProtectionNotifications

/**
 * While active, Android won't uninstall Nour Time (brief §3). The screen that deactivates it lives in
 * the phone's Settings, which the lock overlay protects with the parent PIN.
 */
class NourDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    override fun onDisabled(context: Context, intent: Intent) {
        ProtectionNotifications.ensureChannels(context)
        ProtectionNotifications.showAdminDisabled(context)
    }
}
