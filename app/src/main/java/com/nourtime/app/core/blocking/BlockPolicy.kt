package com.nourtime.app.core.blocking

import com.nourtime.app.core.detection.ForegroundState
import com.nourtime.app.data.settings.Bedtime
import com.nourtime.app.data.settings.LockType
import java.time.LocalTime

enum class BlockReason {
    /** Budget used up; the lock period is running. */
    TIME_UP,
    /** Inside the bedtime window (brief §5). */
    BEDTIME,
    /** Accessibility is off: fail-closed blocking of the limited apps. */
    PROTECTION,
    /** The phone's Settings/uninstall screens, which could disable Nour Time (brief §3). */
    SYSTEM_SETTINGS,
}

data class BlockDecision(
    val reason: BlockReason,
    /** Covers every app, not just the limited ones. Only the parent PIN lifts it. */
    val wholeDevice: Boolean,
    /** During a lock period the PIN alone isn't enough to open limited apps or settings (brief §3). */
    val needsSecurityAnswer: Boolean,
)

data class BlockInput(
    val foreground: ForegroundState,
    val limitedApps: Set<String>,
    val lockType: LockType,
    val timeUp: Boolean,
    val bedtime: Boolean,
    val protectionDegraded: Boolean,
    val protectSystemSettings: Boolean,
    val ownPackage: String,
    val devicePass: Boolean,
    val fullPass: Boolean,
    /** The phone is ringing: nothing but Settings is covered, so the child can answer. */
    val phoneRinging: Boolean = false,
    /** A call is in progress: the call screen stays usable. */
    val inCall: Boolean = false,
    /** Without device admin the Play Store can uninstall Nour Time, so it is covered like Settings. */
    val deviceAdminActive: Boolean = true,
    /** The default phone app, which may be a third-party dialer. */
    val dialerPackage: String? = null,
    /** Educational apps the parent allows during lock periods and bedtime (never limited). */
    val allowedDuringLock: Set<String> = emptySet(),
)

object BlockPolicy {

    /** Packages whose screens can remove or disable Nour Time. Matched by package only. */
    val SYSTEM_SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.google.android.settings.intelligence",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.huawei.systemmanager",
        "com.hihonor.systemmanager",
        "com.vivo.permissionmanager",
        "com.iqoo.secure",
        "com.oneplus.security",
        "com.samsung.android.lool",
        "com.samsung.android.sm_cn",
    )

    /** App stores that can uninstall apps themselves, without the package installer's screen. */
    val APP_STORE_PACKAGES = setOf("com.android.vending")

    /** Phone and in-call screens; never covered, so the child can call a parent or answer. */
    val PHONE_PACKAGES = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.emergency",
    )

    fun decide(input: BlockInput): BlockDecision? {
        val fg = input.foreground
        // Never draw over the lock screen: emergency calls must stay reachable.
        if (!fg.screen.usable) return null

        val lockPeriod = input.timeUp || input.bedtime
        val lockReason = if (input.bedtime) BlockReason.BEDTIME else BlockReason.TIME_UP
        val onScreen = fg.visible + listOfNotNull(fg.foreground)

        val storeUnguarded = !input.deviceAdminActive && fg.foreground in APP_STORE_PACKAGES
        if (input.protectSystemSettings && (fg.foreground in SYSTEM_SETTINGS_PACKAGES || storeUnguarded)) {
            val allowed = input.fullPass || (input.devicePass && !lockPeriod)
            if (!allowed) return BlockDecision(BlockReason.SYSTEM_SETTINGS, wholeDevice = false, needsSecurityAnswer = lockPeriod)
        }

        val phoneInFront = fg.foreground in PHONE_PACKAGES || (fg.foreground != null && fg.foreground == input.dialerPackage)
        // Nour Time's screen is drawn above the notifications, so it steps aside while the phone rings
        // (the call can only be answered from its notification). Settings stay covered (above): a call
        // from another phone mustn't give the child time to switch Nour Time off.
        if (input.phoneRinging) return null
        // During a call only the phone's own screens are exempt; elsewhere the lock screen offers
        // "Back to the call" (LockScreenState.inCall).
        if (input.inCall && phoneInFront) return null
        // Nour Time's own screens (the parent's, behind the PIN) and the phone app are never covered for
        // themselves, only for a limited app still on screen next to them (picture in picture, split screen).
        if (phoneInFront || fg.foreground == input.ownPackage) {
            return limitedAppBlock(input, onScreen, lockPeriod, lockReason)
        }

        // Whole-phone lock: only allowed apps stay usable, and only while nothing else shares the
        // screen (split screen or picture-in-picture with another app would be a way around it).
        val onlyAllowedOnScreen = onScreen.isNotEmpty() && onScreen.all { it in input.allowedDuringLock }
        if (lockPeriod && input.lockType == LockType.WHOLE_DEVICE && !input.devicePass && !input.fullPass && !onlyAllowedOnScreen) {
            return BlockDecision(lockReason, wholeDevice = true, needsSecurityAnswer = false)
        }

        return limitedAppBlock(input, onScreen, lockPeriod, lockReason)
    }

    /** A limited app anywhere on screen, during a lock period or while detection is degraded. */
    private fun limitedAppBlock(input: BlockInput, onScreen: Set<String>, lockPeriod: Boolean, lockReason: BlockReason): BlockDecision? {
        val limitedOnScreen = onScreen.any { it in input.limitedApps }
        if (!limitedOnScreen || !(lockPeriod || input.protectionDegraded) || input.fullPass) return null
        val reason = if (lockPeriod) lockReason else BlockReason.PROTECTION
        return BlockDecision(reason, wholeDevice = false, needsSecurityAnswer = lockPeriod)
    }
}

/** Bedtime window check; the window may cross midnight (e.g. 21:00–07:00). */
fun Bedtime.isActive(now: LocalTime): Boolean {
    if (!enabled || startMinute == endMinute) return false
    val m = now.hour * 60 + now.minute
    return if (startMinute < endMinute) m in startMinute until endMinute else m >= startMinute || m < endMinute
}

/** Minutes from [now] until the bedtime window ends (0 when not active). */
fun Bedtime.minutesUntilEnd(now: LocalTime): Int {
    if (!isActive(now)) return 0
    val m = now.hour * 60 + now.minute
    return ((endMinute - m) + 24 * 60) % (24 * 60)
}
