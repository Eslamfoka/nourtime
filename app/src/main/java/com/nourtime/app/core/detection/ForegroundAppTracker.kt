package com.nourtime.app.core.detection

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "which app is on screen". The Accessibility service feeds it window
 * lists; while Accessibility is off, the timer service feeds it usage-stats results instead.
 */
@Singleton
class ForegroundAppTracker internal constructor(
    private val readScreen: () -> ScreenState,
    private val readIgnored: () -> Set<String>,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        readScreen = { readScreen(context) },
        readIgnored = { computeIgnored(context) },
    )

    private val _state = MutableStateFlow(ForegroundState(screen = readScreen()))
    val state: StateFlow<ForegroundState> = _state.asStateFlow()

    @Volatile
    var accessibilityConnected: Boolean = false
        private set

    /**
     * Packages whose windows are transparent to detection: system UI, keyboards, and system dialogs
     * shown on behalf of the current app (permission prompts, the share/open-with chooser).
     */
    @Volatile
    var ignoredPackages: Set<String> = readIgnored()
        private set

    /**
     * Source switches and the updates from each source are serialized, so a late window list can't
     * overwrite a disconnect (or a late usage-stats poll a reconnect).
     */
    private val sourceLock = Any()

    fun onAccessibilityConnected() = synchronized(sourceLock) {
        accessibilityConnected = true
        _state.update { it.copy(source = DetectionSource.ACCESSIBILITY) }
    }

    fun onAccessibilityDisconnected(fallbackAvailable: Boolean) = synchronized(sourceLock) {
        accessibilityConnected = false
        _state.update { it.copy(source = if (fallbackAvailable) DetectionSource.USAGE_STATS else DetectionSource.NONE) }
    }

    fun onWindows(windows: List<AppWindow>) = synchronized(sourceLock) {
        if (!accessibilityConnected) return
        _state.update { ForegroundRules.fromWindows(windows, ignoredPackages, it).copy(source = DetectionSource.ACCESSIBILITY) }
    }

    /** Fallback result; ignored while Accessibility is connected. */
    fun onUsageStats(foreground: String?, available: Boolean) = synchronized(sourceLock) {
        if (accessibilityConnected) return
        _state.update {
            if (!available) {
                it.copy(foreground = null, visible = emptySet(), source = DetectionSource.NONE)
            } else {
                val pkg = foreground ?: it.foreground
                it.copy(foreground = pkg, visible = setOfNotNull(pkg), source = DetectionSource.USAGE_STATS)
            }
        }
    }

    /** From the screen and unlock broadcasts: also re-reads the keyboards (one may have been added). */
    fun refreshScreen() {
        val screen = readScreen()
        runCatching { Log.i(TAG, "screen interactive=${screen.interactive} keyguard=${screen.keyguardLocked}") }
        _state.update { it.copy(screen = screen) }
        ignoredPackages = readIgnored()
    }

    /**
     * Called every second: a missed broadcast (seen on Honor) left the phone "locked" after an unlock,
     * with no blocking and no counting until the next screen change. Cheap when nothing changed;
     * returns whether the screen state changed.
     */
    fun syncScreen(): Boolean {
        val screen = readScreen()
        if (screen == _state.value.screen) return false
        runCatching { Log.i(TAG, "screen change without a broadcast: interactive=${screen.interactive} keyguard=${screen.keyguardLocked}") }
        _state.update { it.copy(screen = screen) }
        return true
    }

    private companion object {
        const val TAG = "ForegroundTracker"

        fun readScreen(context: Context) = ScreenState(
            interactive = context.getSystemService(PowerManager::class.java).isInteractive,
            keyguardLocked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
        )

        fun computeIgnored(context: Context): Set<String> {
            val imes = runCatching {
                context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
            }.getOrDefault(emptyList())
            return TRANSPARENT_PACKAGES + imes
        }

        val TRANSPARENT_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "android", // ResolverActivity / ChooserActivity
        )
    }
}
