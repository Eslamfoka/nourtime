package com.nourtime.app.core.permissions

import android.content.ComponentName
import android.content.Intent
import android.os.Build

/** Phone makers whose battery managers stop apps; each gets its own instructions. */
enum class OemBrand { XIAOMI, OPPO, VIVO, HONOR_HUAWEI, ONEPLUS, SAMSUNG }

/**
 * Some OEMs (Xiaomi, Oppo, Vivo, Huawei, ...) block apps from starting after a reboot unless the user
 * allows "Autostart" in a vendor screen, and Samsung puts unused apps to sleep. There is no API to
 * check either, so we can only guide the parent.
 */
object OemAutostart {

    private val components = mapOf(
        OemBrand.XIAOMI to listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        OemBrand.OPPO to listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        ),
        OemBrand.VIVO to listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        ),
        OemBrand.HONOR_HUAWEI to listOf(
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        ),
        OemBrand.ONEPLUS to listOf(
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        ),
        OemBrand.SAMSUNG to listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
        ),
    )

    private val brands = mapOf(
        "xiaomi" to OemBrand.XIAOMI,
        "redmi" to OemBrand.XIAOMI,
        "poco" to OemBrand.XIAOMI,
        "oppo" to OemBrand.OPPO,
        "realme" to OemBrand.OPPO,
        "vivo" to OemBrand.VIVO,
        "iqoo" to OemBrand.VIVO,
        "huawei" to OemBrand.HONOR_HUAWEI,
        "honor" to OemBrand.HONOR_HUAWEI,
        "oneplus" to OemBrand.ONEPLUS,
        "samsung" to OemBrand.SAMSUNG,
    )

    fun brand(manufacturer: String = Build.MANUFACTURER): OemBrand? = brands[manufacturer.lowercase()]

    fun isRelevant(manufacturer: String = Build.MANUFACTURER): Boolean = brand(manufacturer) != null

    val manufacturerName: String get() = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    fun intents(): List<Intent> = components[brand()].orEmpty().map { (pkg, cls) ->
        Intent().setComponent(ComponentName(pkg, cls))
    }
}
