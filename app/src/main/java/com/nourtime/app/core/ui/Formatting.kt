package com.nourtime.app.core.ui

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import com.nourtime.app.R
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

/** "45 minutes", "1 hour 30 minutes", "2 hours" (localized, with plurals). */
fun Resources.formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val hours = if (h > 0) getQuantityString(R.plurals.duration_hours, h, h) else null
    val mins = if (m > 0 || h == 0) getQuantityString(R.plurals.duration_minutes, m, m) else null
    return listOfNotNull(hours, mins).joinToString(getString(R.string.duration_joiner))
}
