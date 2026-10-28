package com.nourtime.app.service.blocking

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nourtime.app.core.blocking.BlockReason
import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.data.settings.ParentSettingsRepository
import com.nourtime.app.feature.lock.TemplateKind
import com.nourtime.app.service.detection.NourAccessibilityService
import com.nourtime.app.service.timer.TimerService
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Accessibility switched off for real (the secure setting, as a computer or a reset would): Nour Time's
 * service is unbound, detection falls back, and a limited app gets the "Protection is off" notice in
 * the "display over other apps" window. The phone's Accessibility settings are put back afterwards.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProtectionLossTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var coordinator: BlockCoordinator
    @Inject lateinit var tracker: ForegroundAppTracker
    @Inject lateinit var engine: TimeEngine
    @Inject lateinit var settings: ParentSettingsRepository
    @Inject lateinit var overlay: LockOverlay

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var servicesBefore = "null"
    private var enabledBefore = "null"

    private val service get() = "${Device.context.packageName}/${NourAccessibilityService::class.java.name}"

    @Before
    fun setUp() = runBlocking {
        Device.clearOldTestFiles()
        hilt.inject()
        Device.wakeAndUnlock()
        Device.allowOverlay()
        tracker.refreshScreen()
        check(tracker.state.value.screen.usable) { "detection sees a locked or dark screen" }
        settings.setAppLimited(LIMITED, true)
        settings.setLockType(LockType.SELECTED_APPS)
        servicesBefore = Device.shell("settings get secure enabled_accessibility_services").trim()
        enabledBefore = Device.shell("settings get secure accessibility_enabled").trim()
        scope.launch { coordinator.run() }
        Unit
    }

    @After
    fun tearDown() {
        restore("enabled_accessibility_services", servicesBefore)
        restore("accessibility_enabled", enabledBefore)
        Device.waitFor { !tracker.accessibilityConnected }
        scope.cancel()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { overlay.render(null) }
        // The service may have started the timer service when it connected.
        Device.context.stopService(Intent(Device.context, TimerService::class.java))
        Device.waitFor { !TimerService.running }
    }

    @Test
    fun accessibilityTurnedOff_limitedAppGetsTheProtectionNotice() = runBlocking {
        Device.shell("settings put secure enabled_accessibility_services $service")
        Device.shell("settings put secure accessibility_enabled 1")
        assertTrue("Nour Time's Accessibility service never connected", Device.waitFor(timeoutMs = 20_000) { tracker.accessibilityConnected })
        engine.update(emptySet(), tracker.state.value.source, settings.settings.first())
        assertFalse(engine.status.value!!.protectionDegraded)

        Device.shell("settings delete secure enabled_accessibility_services")
        assertTrue("the loss wasn't seen", Device.waitFor { !tracker.accessibilityConnected })
        assertNotEquals(DetectionSource.ACCESSIBILITY, tracker.state.value.source)
        // The timer service's part, every second: the engine takes the source from detection.
        engine.update(emptySet(), tracker.state.value.source, settings.settings.first())
        assertTrue(engine.status.value!!.protectionDegraded)

        // What the usage-stats fallback reports while a limited app is in front.
        tracker.onUsageStats(LIMITED, available = true)
        assertTrue("the notice didn't open", Device.waitFor { overlay.windowShown })
        val shown = overlay.requested.value!!
        assertEquals(BlockReason.PROTECTION, shown.decision.reason)
        assertEquals(TemplateKind.PROTECTION_OFF, shown.template)
        assertFalse(shown.decision.wholeDevice)

        // An app the parent didn't limit stays usable.
        tracker.onUsageStats(OTHER, available = true)
        assertTrue("the notice stayed over an unlimited app", Device.waitFor { !overlay.windowShown })
    }

    private fun restore(key: String, value: String) {
        if (value == "null") {
            Device.shell("settings delete secure $key")
        } else {
            Device.shell("settings put secure $key $value")
        }
    }

    private companion object {
        // Not installed: detection and the coordinator only see package names.
        const val LIMITED = "com.nourtime.itest.limited"
        const val OTHER = "com.nourtime.itest.other"
    }
}
