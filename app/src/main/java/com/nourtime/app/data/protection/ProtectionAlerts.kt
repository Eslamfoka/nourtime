package com.nourtime.app.data.protection

/** Ways protection was switched off that the parent should hear about (2026-10-12 audit). */
enum class ProtectionAlert { USB_DEBUGGING, MISSED_RESTART, OTHER_USER, NOTIFICATIONS_OFF, PIN_RESET, PROTECTION_DEGRADED }

/**
 * Uploaded to the parent's phone as `devices/{id}/meta/alerts` (and, for older parent apps, `devices/{id}.alerts`). Times are wall-clock ms when the child's
 * phone noticed the event.
 */
data class ProtectionAlerts(
    /** A restart Nour Time didn't run in, e.g. safe mode (noticed at the next normal start). */
    val missedRestartAtMs: Long? = null,
    /** The phone switched to another user (a guest, a second account), where Nour Time doesn't run. */
    val otherUserAtMs: Long? = null,
    /** While it's on, a computer can turn Nour Time's permissions off. */
    val usbDebugging: Boolean = false,
    /** The PIN was replaced through the security question ("Forgot PIN?"). */
    val pinResetAtMs: Long? = null,
    /** Nour Time's notifications are off (they can be switched off from the notification shade). */
    val notificationsOff: Boolean = false,
    /** When the PIN from "Forgot PIN?" starts working (the parent can approve it sooner), or null. */
    val pinResetPendingUntilMs: Long? = null,
    /** Detection is degraded (Accessibility off): limited apps are blocked, Settings may not be covered. */
    val protectionDegraded: Boolean = false,
) {
    /** The waiting PIN's start time while it still waits, for the parent's "approve" card. */
    fun pendingPinReset(nowMs: Long): Long? = pinResetPendingUntilMs?.takeIf { it > nowMs }

    /** What to show now: USB debugging while it's on, events for a week. */
    fun visible(nowMs: Long): List<ProtectionAlert> = buildList {
        if (protectionDegraded) add(ProtectionAlert.PROTECTION_DEGRADED)
        if (usbDebugging) add(ProtectionAlert.USB_DEBUGGING)
        if (notificationsOff) add(ProtectionAlert.NOTIFICATIONS_OFF)
        if (missedRestartAtMs.isRecent(nowMs)) add(ProtectionAlert.MISSED_RESTART)
        if (otherUserAtMs.isRecent(nowMs)) add(ProtectionAlert.OTHER_USER)
        if (pinResetAtMs.isRecent(nowMs)) add(ProtectionAlert.PIN_RESET)
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "missedRestartAt" to missedRestartAtMs,
        "otherUserAt" to otherUserAtMs,
        "usbDebugging" to usbDebugging,
        "pinResetAt" to pinResetAtMs,
        "notificationsOff" to notificationsOff,
        "pinResetPendingUntil" to pinResetPendingUntilMs,
        "protectionDegraded" to protectionDegraded,
    )

    companion object {
        const val SHOW_FOR_MS = 7 * 24 * 60 * 60_000L

        fun fromMap(m: Map<String, Any?>?): ProtectionAlerts {
            if (m == null) return ProtectionAlerts()
            return ProtectionAlerts(
                missedRestartAtMs = (m["missedRestartAt"] as? Number)?.toLong(),
                otherUserAtMs = (m["otherUserAt"] as? Number)?.toLong(),
                usbDebugging = m["usbDebugging"] as? Boolean ?: false,
                pinResetAtMs = (m["pinResetAt"] as? Number)?.toLong(),
                notificationsOff = m["notificationsOff"] as? Boolean ?: false,
                pinResetPendingUntilMs = (m["pinResetPendingUntil"] as? Number)?.toLong(),
                protectionDegraded = m["protectionDegraded"] as? Boolean ?: false,
            )
        }

        private fun Long?.isRecent(nowMs: Long) = this != null && nowMs - this < SHOW_FOR_MS
    }
}

/** Android counts boots (Settings.Global.BOOT_COUNT); a number Nour Time never saw is a restart without it. */
object RestartCheck {
    fun missedBoots(lastSeen: Int?, current: Int): Int =
        if (lastSeen == null || current <= lastSeen) 0 else current - lastSeen - 1
}
