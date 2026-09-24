package com.nourtime.app.core.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.nourtime.app.R
import com.nourtime.app.service.admin.NourDeviceAdminReceiver
import com.nourtime.app.service.detection.NourAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class NourPermission {
    ACCESSIBILITY,
    USAGE_ACCESS,
    OVERLAY,
    DEVICE_ADMIN,
    NOTIFICATIONS,
    BATTERY,
}

@Singleton
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val packageUri: Uri get() = Uri.parse("package:${context.packageName}")

    fun statusOfAll(): Map<NourPermission, Boolean> = NourPermission.entries.associateWith(::isGranted)

    fun isGranted(permission: NourPermission): Boolean = when (permission) {
        NourPermission.ACCESSIBILITY -> isAccessibilityServiceEnabled()
        NourPermission.USAGE_ACCESS -> hasUsageAccess()
        NourPermission.OVERLAY -> Settings.canDrawOverlays(context)
        NourPermission.DEVICE_ADMIN -> context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent())
        NourPermission.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        NourPermission.BATTERY -> context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Screens that grant [permission], best first. Some OEMs lack the specific ones, so callers try each. */
    fun settingsIntents(permission: NourPermission): List<Intent> = when (permission) {
        NourPermission.ACCESSIBILITY -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        NourPermission.USAGE_ACCESS -> listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
        NourPermission.OVERLAY -> listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        )
        NourPermission.DEVICE_ADMIN -> listOf(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.perm_admin_explanation)),
        )
        NourPermission.NOTIFICATIONS -> listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            appDetailsIntent(),
        )
        NourPermission.BATTERY -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
    }

    fun appDetailsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

    /** Notifications need a runtime request on Android 13+; below that they are on by default. */
    val notificationsNeedRuntimeRequest: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val notificationPermission: String
        get() = Manifest.permission.POST_NOTIFICATIONS

    private fun adminComponent() = ComponentName(context, NourDeviceAdminReceiver::class.java)

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(context, NourAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
