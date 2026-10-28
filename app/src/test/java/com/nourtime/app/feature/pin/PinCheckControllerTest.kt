package com.nourtime.app.feature.pin

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nourtime.app.core.security.SecretHasher
import com.nourtime.app.core.time.DeviceClock
import com.nourtime.app.core.time.WallClock
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
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
    fun `wrong PINs on the lock screen and in the app use the same free tries`() = runTest {
        security.setPin("4827")
        val lockScreen = PinCheckController(io, security, FakeClock()) {}
        val app = PinCheckController(io, security, FakeClock()) { opened = true }
        "0000".forEach(lockScreen::onDigit)
        assertEquals(3, settle(lockScreen).attemptsLeft)
        "1111".forEach(app::onDigit)
        assertEquals(2, settle(app).attemptsLeft)
        "2222".forEach(lockScreen::onDigit)
        assertEquals(1, settle(lockScreen).attemptsLeft)
        "3333".forEach(app::onDigit)
        assertEquals(0, settle(app).attemptsLeft)
    }

    @Test
    fun `a wait started on the lock screen also holds in the app`() = runTest {
        security.setPin("4827")
        val lockScreen = PinCheckController(io, security, FakeClock()) {}
        repeat(5) {
            "0000".forEach(lockScreen::onDigit)
            settle(lockScreen)
        }
        withContext(Dispatchers.Default) { withTimeout(5_000) { lockScreen.state.first { it.lockoutRemainingMs > 0 } } }

        // Nour Time's pad, opened now, shows the same wait and takes no digits, not even the right PIN.
        val app = PinCheckController(io, security, FakeClock()) { opened = true }
        val shown = withContext(Dispatchers.Default) { withTimeout(5_000) { app.state.first { it.lockoutRemainingMs > 0 } } }
        assertEquals(30_000L, shown.lockoutRemainingMs)
        "4827".forEach(app::onDigit)
        withContext(Dispatchers.Default) { delay(200) }
        assertEquals(0, app.state.value.entered)
        assertFalse(opened)
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
