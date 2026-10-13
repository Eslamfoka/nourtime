package com.nourtime.app.remote.parent

import com.nourtime.app.data.protection.ProtectionAlert

/**
 * A push from the server to the parent's phone (firebase/functions/src/send.ts). Data only: the text
 * is built on the phone, in the app's language.
 */
sealed interface PushMessage {
    val deviceId: String
    val deviceName: String

    /** What the notification replaces: one per child's phone and kind. */
    val notificationId: Int get() = deviceId.hashCode() * 31 + kind.hashCode()

    val kind: String

    data class Ask(override val deviceId: String, override val deviceName: String) : PushMessage {
        override val kind get() = "ASK"
    }

    data class Alert(override val deviceId: String, override val deviceName: String, val alert: ProtectionAlert) : PushMessage {
        override val kind get() = alert.name
    }

    data class PinResetPending(override val deviceId: String, override val deviceName: String, val untilMs: Long) : PushMessage {
        override val kind get() = "PIN_RESET_PENDING"
    }
}

/** Null for anything this version doesn't know (a newer server), or without a device. */
fun pushMessageOf(data: Map<String, String>): PushMessage? {
    val id = data["deviceId"]?.takeIf { it.isNotBlank() } ?: return null
    val name = data["deviceName"].orEmpty()
    return when (val type = data["type"]) {
        "ASK" -> PushMessage.Ask(id, name)
        "PIN_RESET_PENDING" -> data["pendingUntil"]?.toLongOrNull()?.let { PushMessage.PinResetPending(id, name, it) }
        else -> ProtectionAlert.entries.firstOrNull { it.name == type }?.let { PushMessage.Alert(id, name, it) }
    }
}
