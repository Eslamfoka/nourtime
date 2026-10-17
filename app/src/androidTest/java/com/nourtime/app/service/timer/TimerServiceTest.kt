package com.nourtime.app.service.timer

import android.app.ActivityManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nourtime.app.core.detection.AppWindow
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.core.timer.TimerCommand
import com.nourtime.app.core.timer.TimerPhase
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.data.settings.ParentSettingsRepository
import com.nourtime.app.service.blocking.LockOverlay
import com.nourtime.app.testing.Device
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The real timer service: started as the app starts it, in the foreground, charging the budget only
 * while a limited app is on screen, and locking when the budget runs out.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TimerServiceTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var tracker: ForegroundAppTracker
    @Inject lateinit var engine: TimeEngine
    @Inject lateinit var settings: ParentSettingsRepository
    @Inject lateinit var overlay: LockOverlay

    private val context get() = Device.context

    @Before
    fun setUp() = runBlocking {
        Device.clearOldTestFiles()
        hilt.inject()
        Device.wakeAndUnlock()
        Device.allowOverlay()
        settings.setAppLimited(LIMITED, true)
        settings.setLockType(LockType.SELECTED_APPS)
        settings.setBudgetMinutes(5)
        Unit
    }

    @After
    fun tearDown() {
        context.stopService(Intent(context, TimerService::class.java))
        Device.waitFor { !TimerService.running }
    }

    @Test
    fun startsInTheForeground() {
        assertTrue(TimerService.start(context))
        assertTrue("service didn't start", Device.waitFor { TimerService.running })
        assertTrue("service isn't in the foreground", Device.waitFor { runningInForeground() })
    }

    @Test
    fun countsOnlyWhileALimitedAppIsOnScreen() {
        startWithDetection()
        show(LIMITED)
        assertTrue("never counted", Device.waitFor { engine.status.value?.counting == true })
        val before = remaining()
        Thread.sleep(4_000)
        val used = before - remaining()
        assertTrue("used $used ms in 4 s of use", used in 2_500..6_000)

        show(OTHER)
        assertTrue(Device.waitFor { engine.status.value?.counting == false })
        val paused = remaining()
        Thread.sleep(3_000)
        assertEquals("time ran without a limited app", paused, remaining())
    }

    @Test
    fun lockPeriod_theServiceCoversTheLimitedApp() = runBlocking {
        startWithDetection()
        show(LIMITED)
        assertTrue(Device.waitFor { engine.status.value?.counting == true })
        // As if the budget had run out (5 minutes would make a slow test; TimeRulesTest covers that).
        engine.apply(TimerCommand.LockNow)
        assertTrue(Device.waitFor { engine.status.value?.phase == TimerPhase.LOCKED })
        assertTrue("the service's coordinator didn't cover the limited app", Device.waitFor { overlay.windowShown })
    }

    @Test
    fun restartingTheService_keepsTheTimeLeft() {
        startWithDetection()
        show(LIMITED)
        assertTrue(Device.waitFor { engine.status.value?.counting == true })
        Thread.sleep(2_000)
        show(OTHER)
        assertTrue(Device.waitFor { engine.status.value?.counting == false })
        val left = remaining()
        context.stopService(Intent(context, TimerService::class.java))
        assertTrue(Device.waitFor { !TimerService.running })
        assertTrue(TimerService.start(context))
        assertTrue(Device.waitFor { TimerService.running })
        assertTrue(Device.waitFor { engine.status.value != null })
        assertTrue("time left changed across a restart: $left → ${remaining()}", remaining() in (left - 1_500)..left)
    }

    /** Starts the service, then plays the Accessibility service's part (it says "disconnected" at start). */
    private fun startWithDetection() {
        assertTrue(TimerService.start(context))
        assertTrue(Device.waitFor { TimerService.running })
        assertTrue(Device.waitFor { engine.status.value != null })
        tracker.onAccessibilityConnected()
    }

    private fun show(pkg: String) {
        tracker.onWindows(listOf(AppWindow(pkg, isApplication = true, isActive = true)))
    }

    private fun remaining(): Long = engine.status.value!!.remainingMs

    @Suppress("DEPRECATION") // Still returns the caller's own services, which is all this needs.
    private fun runningInForeground(): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(50)
            .any { it.service.className == TimerService::class.java.name && it.foreground }

    private companion object {
        const val LIMITED = "com.nourtime.itest.limited"
        const val OTHER = "com.nourtime.itest.other"
    }
}
