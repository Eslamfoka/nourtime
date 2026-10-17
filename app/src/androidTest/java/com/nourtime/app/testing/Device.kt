package com.nourtime.app.testing

import android.app.KeyguardManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** The test phone (a dedicated emulator): screen on and unlocked, Nour Time allowed to draw over apps. */
object Device {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context get() = instrumentation.targetContext

    fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).let { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes().decodeToString() }.also { pfd.close() }
        }

    /**
     * Screen on, no lock screen, and it stays on while the tests run. Waits until that's true: Nour Time
     * never draws over the lock screen, and a slow emulator (CI) dismisses it a moment later.
     */
    fun wakeAndUnlock() {
        shell("svc power stayon true")
        shell("settings put system screen_off_timeout 1800000")
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val usable = waitFor(timeoutMs = 20_000) {
            if (!power.isInteractive) shell("input keyevent KEYCODE_WAKEUP")
            if (keyguard.isKeyguardLocked) shell("wm dismiss-keyguard")
            power.isInteractive && !keyguard.isKeyguardLocked
        }
        check(usable) { "the test phone's screen stayed off or locked" }
    }

    /** The "display over other apps" overlay: the lock screen's window while Accessibility is off. */
    fun allowOverlay() {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
    }

    /** Removes data files earlier tests left (see [TestFiles]); the open ones belong to finished tests. */
    fun clearOldTestFiles() {
        File(context.filesDir, "datastore").listFiles { f -> f.name.startsWith(TestFiles.PREFIX) }?.forEach { it.delete() }
    }

    /** Waits up to [timeoutMs] for [condition]; false if it never held. */
    fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }
}
