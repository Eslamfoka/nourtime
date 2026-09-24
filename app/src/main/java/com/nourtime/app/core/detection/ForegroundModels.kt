package com.nourtime.app.core.detection

/** Time only counts while the screen is on and unlocked. */
data class ScreenState(val interactive: Boolean, val keyguardLocked: Boolean) {
    val usable: Boolean get() = interactive && !keyguardLocked
}

enum class DetectionSource {
    /** Accessibility window list: sees picture-in-picture and split screen. */
    ACCESSIBILITY,
    /** Usage-stats polling fallback while Accessibility is off (protection is degraded). */
    USAGE_STATS,
    /** Nothing can see the foreground app. */
    NONE,
}

data class ForegroundState(
    /** The focused, full-screen app. */
    val foreground: String? = null,
    /** Every app with a visible window, including picture-in-picture and split screen. */
    val visible: Set<String> = emptySet(),
    val screen: ScreenState = ScreenState(interactive = true, keyguardLocked = false),
    val source: DetectionSource = DetectionSource.NONE,
) {
    /** Packages from [limited] visible on a usable screen. */
    fun limitedInUse(limited: Set<String>): Set<String> =
        if (!screen.usable) emptySet() else (visible + listOfNotNull(foreground)).filterTo(mutableSetOf()) { it in limited }
}

/**
 * One on-screen window as seen by the Accessibility service. Only the owning package and the
 * window type are read; never the window's text or content.
 */
data class AppWindow(
    val packageName: String?,
    val isApplication: Boolean,
    val isActive: Boolean,
)

object ForegroundRules {

    /**
     * Foreground and visible apps from the accessibility window list (top-most first). Keyboards and
     * system UI are ignored; when nothing else is left (for example while the notification shade is
     * open) the previous apps are kept, so glancing at a notification doesn't pause the timer.
     * Nour Time's own screens (settings, the "Time's up" screen) count like any non-limited app.
     */
    fun fromWindows(windows: List<AppWindow>, ignored: Set<String>, previous: ForegroundState): ForegroundState {
        val apps = windows.filter { it.isApplication && it.packageName != null && it.packageName !in ignored }
        if (apps.isEmpty()) return previous
        val foreground = (apps.firstOrNull { it.isActive } ?: apps.first()).packageName
        return previous.copy(foreground = foreground, visible = apps.mapNotNullTo(linkedSetOf()) { it.packageName })
    }

    /** Most recent app that came to the foreground in a list of (package, isResumed) usage events, oldest first. */
    fun lastResumed(events: List<Pair<String, Boolean>>, ignored: Set<String>): String? =
        events.lastOrNull { (pkg, resumed) -> resumed && pkg !in ignored }?.first
}
