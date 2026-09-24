package com.nourtime.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.ui.graphics.vector.ImageVector
import com.nourtime.app.R
import com.nourtime.app.core.permissions.NourPermission

/** Icon and copy for each permission, shared by the disclosure, the grant steps and home. */
data class PermissionUi(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val short: Int,
    @StringRes val why: Int,
    @StringRes val how: Int,
)

val NourPermission.ui: PermissionUi
    get() = when (this) {
        NourPermission.ACCESSIBILITY -> PermissionUi(
            Icons.Rounded.AccessibilityNew, R.string.perm_accessibility_title, R.string.perm_accessibility_short,
            R.string.perm_accessibility_why, R.string.perm_accessibility_how,
        )
        NourPermission.USAGE_ACCESS -> PermissionUi(
            Icons.Rounded.QueryStats, R.string.perm_usage_title, R.string.perm_usage_short,
            R.string.perm_usage_why, R.string.perm_usage_how,
        )
        NourPermission.OVERLAY -> PermissionUi(
            Icons.Rounded.Layers, R.string.perm_overlay_title, R.string.perm_overlay_short,
            R.string.perm_overlay_why, R.string.perm_overlay_how,
        )
        NourPermission.DEVICE_ADMIN -> PermissionUi(
            Icons.Rounded.AdminPanelSettings, R.string.perm_admin_title, R.string.perm_admin_short,
            R.string.perm_admin_why, R.string.perm_admin_how,
        )
        NourPermission.NOTIFICATIONS -> PermissionUi(
            Icons.Rounded.NotificationsActive, R.string.perm_notif_title, R.string.perm_notif_short,
            R.string.perm_notif_why, R.string.perm_notif_how,
        )
        NourPermission.BATTERY -> PermissionUi(
            Icons.Rounded.BatteryChargingFull, R.string.perm_battery_title, R.string.perm_battery_short,
            R.string.perm_battery_why, R.string.perm_battery_how,
        )
    }
