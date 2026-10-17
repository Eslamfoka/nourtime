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
import org.junit.Assert.assertNull
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
    fun `time in another user is measured once, with the lock left at the switch`() = runTest {
        assertNull(repo.takeOtherUserAway(nowElapsed = 5_000, bootCount = 3, nowWallMs = 0))
        repo.recordOtherUserSince(nowElapsed = 10_000, bootCount = 3, nowWallMs = 1_000_000, lockLeftMs = 7_000)
        assertEquals(OtherUserAway(50_000, 7_000), repo.takeOtherUserAway(nowElapsed = 60_000, bootCount = 3, nowWallMs = 0))
        assertNull(repo.takeOtherUserAway(nowElapsed = 90_000, bootCount = 3, nowWallMs = 0))
    }

    @Test
    fun `a restart in another user is measured in wall time, at most a day`() = runTest {
        val hour = 60 * 60_000L
        repo.recordOtherUserSince(nowElapsed = 10_000, bootCount = 3, nowWallMs = 1_000_000, lockLeftMs = 0)
        assertEquals(OtherUserAway(3 * hour, 0), repo.takeOtherUserAway(nowElapsed = 500, bootCount = 4, nowWallMs = 1_000_000 + 3 * hour))
        repo.recordOtherUserSince(nowElapsed = 10_000, bootCount = 4, nowWallMs = 1_000_000, lockLeftMs = 0)
        assertEquals(
            ProtectionAlertsRepository.MAX_AWAY_AFTER_RESTART_MS,
            repo.takeOtherUserAway(nowElapsed = 500, bootCount = 5, nowWallMs = 1_000_000 + 30 * 24 * hour)?.awayMs,
        )
    }

    @Test
    fun `a PIN reset and notifications turned off are recorded`() = runTest {
        repo.recordPinReset(nowMs = 7_000)
        repo.setNotificationsOff(true)
        assertEquals(ProtectionAlerts(pinResetAtMs = 7_000, notificationsOff = true), repo.alerts.first())
    }

    @Test
    fun `another user and USB debugging are recorded`() = runTest {
        repo.recordOtherUser(nowMs = 4_000)
        repo.setUsbDebugging(true)
        assertEquals(ProtectionAlerts(otherUserAtMs = 4_000, usbDebugging = true), repo.alerts.first())
    }
}
