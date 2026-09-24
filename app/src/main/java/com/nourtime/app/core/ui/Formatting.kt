package com.nourtime.app.core.ui

import android.content.Context
import android.content.Intent
import java.util.Locale

/** "m:ss", or "h:mm:ss" from one hour up. Rounds up so a countdown never shows 0:00 early. */
fun formatCountdown(ms: Long, locale: Locale = Locale.getDefault()): String {
    val totalSeconds = (ms.coerceAtLeast(0) + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%d:%02d", m, s)
}

/** Starts the first intent this device can handle. Returns false if none worked. */
fun Context.startFirstAvailable(intents: List<Intent>): Boolean = intents.any { intent ->
    runCatching { startActivity(intent) }.isSuccess
}
