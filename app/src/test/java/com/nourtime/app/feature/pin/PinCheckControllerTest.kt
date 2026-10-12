package com.nourtime.app.feature.pin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.data.security.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PinCheckControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeClock : DeviceClock {
        override fun elapsedRealtime() = 10_000L
        override fun bootCount() = 1
    }

    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val security by lazy {
        val store = PreferenceDataStoreFactory.create(scope = io) { tmp.newFile("c.preferences_pb") }
        SecurityRepository(store, SecretHasher(iterations = 1_000), FakeClock())
    }

    @Volatile private var opened = false

    @After
    fun tearDown() = io.cancel()

    @Test
    fun `a missing PIN doesn't open the parent's screens`() = runTest {
        // Found in the 2026-10-12 audit: with the PIN gone (both settings files lost), any four digits
        // opened everything. The PIN screens only run after setup, where a PIN always exists.
        val check = PinCheckController(io, security, FakeClock()) { opened = true }
        "1234".forEach(check::onDigit)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { check.state.first { !it.checking && it.entered == 0 } }
            delay(200)
        }
        assertFalse(opened)
    }
}
