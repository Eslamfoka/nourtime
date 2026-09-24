package com.nourtime.app.core.permissions

import android.content.ComponentName
import android.content.Intent
import android.os.Build

/**
 * Some OEMs (Xiaomi, Oppo, Vivo, Huawei, ...) block apps from starting after a reboot unless the user
 * allows "Autostart" in a vendor screen. There is no API to check it, so we can only guide the parent.
 */
object OemAutostart {

    private val components = mapOf(
        "xiaomi" to listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        "oppo" to listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        ),
        "vivo" to listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        ),
        "huawei" to listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        ),
        "honor" to listOf(
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        ),
        "oneplus" to listOf(
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        ),
    )

    private val aliases = mapOf(
        "redmi" to "xiaomi",
        "poco" to "xiaomi",
        "realme" to "oppo",
        "iqoo" to "vivo",
    )

    private fun vendor(manufacturer: String = Build.MANUFACTURER): String? {
        val m = manufacturer.lowercase()
        return aliases[m] ?: m.takeIf { it in components }
    }

    fun isRelevant(manufacturer: String = Build.MANUFACTURER): Boolean = vendor(manufacturer) != null

    val manufacturerName: String get() = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    fun intents(): List<Intent> = components[vendor()].orEmpty().map { (pkg, cls) ->
        Intent().setComponent(ComponentName(pkg, cls))
    }
}
