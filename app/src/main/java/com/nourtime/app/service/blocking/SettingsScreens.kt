package com.nourtime.app.service.blocking

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.nourtime.app.core.blocking.BlockPolicy

/**
 * The apps that open Android's settings screens on this phone. Phone makers move them into their own
 * packages (security centres, battery managers), which a fixed list can miss (2026-10-12 audit).
 */
object SettingsScreens {

    fun resolve(context: Context): Set<String> {
        val own = context.packageName
        val packageUri = Uri.parse("package:$own")
        val intents = listOf(
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, own),
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN),
            Intent(Intent.ACTION_DELETE, packageUri),
        )
        val found = intents.mapNotNull { intent ->
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull()
        }
        val defaultHome = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        }.getOrNull()
        val screens = filter(found, own, defaultHome)
        Log.i("SettingsScreens", "covered on this phone: $screens")
        return screens
    }

    /**
     * Never the default home screen or System UI (a launcher that also opens app info would lock the
     * home screen), nor the system chooser ("android"), the phone app or Nour Time itself. Only the
     * default home: Android's Settings declares a fallback home screen of its own.
     */
    fun filter(found: Collection<String>, own: String, defaultHome: String?): Set<String> =
        found.toSet() - own - "android" - "com.android.systemui" - BlockPolicy.PHONE_PACKAGES - setOfNotNull(defaultHome)
}
