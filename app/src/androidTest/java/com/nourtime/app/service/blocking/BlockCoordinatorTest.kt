package com.nourtime.app.service.blocking

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nourtime.app.core.blocking.BlockReason
import com.nourtime.app.core.blocking.ParentPass
import com.nourtime.app.core.detection.AppWindow
import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.core.timer.TimerCommand
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.data.settings.ParentSettingsRepository
import com.nourtime.app.testing.Device
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The real coordinator on a real phone: the window lists Accessibility would deliver go into the
 * tracker, the timer is the real engine, and the check is the lock screen's window itself (the
 * "display over other apps" kind, as with Accessibility off).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BlockCoordinatorTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var coordinator: BlockCoordinator
    @Inject lateinit var tracker: ForegroundAppTracker
    @Inject lateinit var engine: TimeEngine
    @Inject lateinit var settings: ParentSettingsRepository
    @Inject lateinit var overlay: LockOverlay
    @Inject lateinit var pass: ParentPass

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() = runBlocking {
        Device.clearOldTestFiles()
        hilt.inject()
        Device.wakeAndUnlock()
        Device.allowOverlay()
        // Read once here: without the timer service nothing re-reads it in these tests.
        tracker.refreshScreen()
        check(tracker.state.value.screen.usable) { "detection sees a locked or dark screen" }
        settings.setAppLimited(LIMITED, true)
        settings.setLockType(LockType.SELECTED_APPS)
        tracker.onAccessibilityConnected()
        // The timer service would do this every second; once is enough for a status to exist.
        engine.update(emptySet(), DetectionSource.ACCESSIBILITY, settings.settings.first())
        scope.launch { coordinator.run() }
        Unit
    }

    @After
    fun tearDown() {
        scope.cancel()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { overlay.render(null) }
    }

    @Test
    fun timeLeft_nothingCoversTheLimitedApp() {
        show(LIMITED)
        Thread.sleep(2_500)
        assertNull(overlay.requested.value)
        assertFalse(overlay.windowShown)
    }

    @Test
    fun lockPeriod_coversTheLimitedApp_withTheTimesUpScreen() = runBlocking {
        show(LIMITED)
        engine.apply(TimerCommand.LockNow)
        assertTrue("lock screen didn't open", Device.waitFor { overlay.windowShown })
        assertEquals(BlockReason.TIME_UP, overlay.requested.value?.decision?.reason)
        assertEquals(false, overlay.requested.value?.decision?.wholeDevice)
    }

    @Test
    fun lockPeriod_selectedApps_leavingTheLimitedAppLiftsTheCover() = runBlocking {
        show(LIMITED)
        engine.apply(TimerCommand.LockNow)
        assertTrue(Device.waitFor { overlay.windowShown })
        show(OTHER)
        assertTrue("lock screen stayed over an unlimited app", Device.waitFor { !overlay.windowShown })
    }

    @Test
    fun lockPeriod_wholeDevice_coversAnyApp() = runBlocking {
        settings.setLockType(LockType.WHOLE_DEVICE)
        show(OTHER)
        engine.apply(TimerCommand.LockNow)
        assertTrue("whole-phone lock didn't open", Device.waitFor { overlay.windowShown })
        assertEquals(true, overlay.requested.value?.decision?.wholeDevice)
    }

    @Test
    fun parentPass_liftsTheCover() = runBlocking {
        show(LIMITED)
        engine.apply(TimerCommand.LockNow)
        assertTrue(Device.waitFor { overlay.windowShown })
        pass.grantFull()
        assertTrue("the parent's pass didn't lift the lock screen", Device.waitFor { !overlay.windowShown })
    }

    @Test
    fun endOfLock_liftsTheCover() = runBlocking {
        show(LIMITED)
        engine.apply(TimerCommand.LockNow)
        assertTrue(Device.waitFor { overlay.windowShown })
        engine.apply(TimerCommand.EndLock)
        assertTrue("ending the lock didn't lift the lock screen", Device.waitFor { !overlay.windowShown })
    }

    @Test
    fun settings_areCoveredForThePin_evenWithTimeLeft() = runBlocking {
        settings.setProtectSystemSettings(true)
        show(SETTINGS)
        assertTrue("Settings weren't covered", Device.waitFor { overlay.windowShown })
        assertEquals(BlockReason.SYSTEM_SETTINGS, overlay.requested.value?.decision?.reason)
    }

    /** What Accessibility would report: [pkg] focused and full screen. */
    private fun show(pkg: String) {
        tracker.onWindows(listOf(AppWindow(pkg, isApplication = true, isActive = true)))
    }

    private companion object {
        // Not installed: detection and the coordinator only see package names.
        const val LIMITED = "com.nourtime.itest.limited"
        const val OTHER = "com.nourtime.itest.other"
        const val SETTINGS = "com.android.settings"
    }
}
