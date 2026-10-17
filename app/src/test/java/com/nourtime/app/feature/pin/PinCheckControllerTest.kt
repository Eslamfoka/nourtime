package com.nourtime.app.feature.pin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.WallClock
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import com.nourtime.app.data.security.PinEntryPoint
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        val wall = WallClock { ZonedDateTime.ofInstant(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC) }
        SecurityRepository(store, SecretHasher(iterations = 1_000), FakeClock(), wall)
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

    @Test
    fun `a child's wrong PINs on the lock screen leave the app's pad open`() = runTest {
        security.setPin("4827")
        val lockScreen = PinCheckController(io, security, FakeClock(), PinEntryPoint.LOCK_SCREEN) {}
        repeat(5) {
            "0000".forEach(lockScreen::onDigit)
            settle(lockScreen)
        }
        withContext(Dispatchers.Default) { withTimeout(5_000) { lockScreen.state.first { it.lockoutRemainingMs > 0 } } }

        val app = PinCheckController(io, security, FakeClock()) { opened = true }
        delay(100)
        assertEquals(0L, app.state.value.lockoutRemainingMs)
        "4827".forEach(app::onDigit)
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (!opened) delay(20) } }
        assertTrue(opened)
    }

    @Test
    fun `the answer screen says how many wrong answers are left, also on a later visit`() = runTest {
        security.setSecurityQuestion("Colour?", "blue")
        val first = AnswerCheckController(io, security, FakeClock()) {}
        withContext(Dispatchers.Default) { withTimeout(5_000) { first.state.first { it.question.isNotEmpty() } } }
        assertEquals(null, first.state.value.attemptsLeft)
        first.onAnswerChange("red")
        first.submit()
        val wrong = withContext(Dispatchers.Default) { withTimeout(5_000) { first.state.first { it.wrong && !it.checking } } }
        assertEquals(2, wrong.attemptsLeft)

        val later = AnswerCheckController(io, security, FakeClock()) {}
        val shown = withContext(Dispatchers.Default) { withTimeout(5_000) { later.state.first { it.question.isNotEmpty() } } }
        assertEquals(2, shown.attemptsLeft)
    }

    private suspend fun settle(check: PinCheckController) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { check.state.first { !it.checking && it.entered == 0 } }
    }
}
