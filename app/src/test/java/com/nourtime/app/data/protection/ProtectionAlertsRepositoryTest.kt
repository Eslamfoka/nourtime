package com.nourtime.app.data.protection

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProtectionAlertsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repo by lazy {
        ProtectionAlertsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("p.preferences_pb") })
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `boots in order raise nothing`() = runTest {
        assertEquals(false, repo.recordBoot(bootCount = 5, nowMs = 1_000))
        assertEquals(false, repo.recordBoot(bootCount = 6, nowMs = 2_000))
        assertEquals(ProtectionAlerts(), repo.alerts.first())
    }

    @Test
    fun `a skipped boot is recorded with the time it was noticed`() = runTest {
        repo.recordBoot(bootCount = 5, nowMs = 1_000)
        assertEquals(true, repo.recordBoot(bootCount = 7, nowMs = 9_000))
        assertEquals(9_000L, repo.alerts.first().missedRestartAtMs)
    }

    @Test
    fun `another user and USB debugging are recorded`() = runTest {
        repo.recordOtherUser(nowMs = 4_000)
        repo.setUsbDebugging(true)
        assertEquals(ProtectionAlerts(otherUserAtMs = 4_000, usbDebugging = true), repo.alerts.first())
    }
}
