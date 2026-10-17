package com.nourtime.app.service.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nourtime.app.core.detection.AppWindow
import com.nourtime.app.core.detection.DetectionSource
import com.nourtime.app.core.detection.ForegroundAppTracker
import com.nourtime.app.core.timer.TimeEngine
import com.nourtime.app.core.timer.TimerCommand
import com.nourtime.app.data.settings.LockType
import com.nourtime.app.data.settings.ParentSettingsRepository
import com.nourtime.app.service.blocking.BlockCoordinator
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * A limited app's background audio (2026-10-17 audit, M2), with a real media session standing in for
 * YouTube or Spotify: it reaches Nour Time through a media notification, is paused during a lock
 * (also when played again), and counts as use while the screen is on.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LimitedMediaTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var media: MediaWatcher
    @Inject lateinit var coordinator: BlockCoordinator
    @Inject lateinit var tracker: ForegroundAppTracker
    @Inject lateinit var engine: TimeEngine
    @Inject lateinit var settings: ParentSettingsRepository

    private val context get() = Device.context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var session: MediaSession
    private val pauses = AtomicInteger()

    @Before
    fun setUp() = runBlocking {
        Device.clearOldTestFiles()
        hilt.inject()
        Device.wakeAndUnlock()
        Device.allowOverlay()
        settings.setAppLimited(LIMITED, true)
        settings.setLockType(LockType.SELECTED_APPS)
        settings.setBudgetMinutes(5)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            session = MediaSession(context, "itest").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPause() {
                        pauses.incrementAndGet()
                        setPlaying(false)
                    }
                })
                isActive = true
            }
        }
        setPlaying(true)
    }

    @After
    fun tearDown() {
        scope.cancel()
        context.stopService(Intent(context, TimerService::class.java))
        Device.waitFor { !TimerService.running }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { session.release() }
    }

    @Test
    fun aMediaNotification_bringsTheSessionAndWhetherItPlays() {
        deliverMediaNotification()
        assertTrue(Device.waitFor { media.sessions.value.any { it.packageName == LIMITED && it.playing } })
        setPlaying(false)
        assertTrue(Device.waitFor { media.sessions.value.none { it.playing } })
    }

    @Test
    fun anotherNotification_isIgnored() {
        val plain = Notification.Builder(context, channel()).setSmallIcon(android.R.drawable.ic_media_play).setContentText("hello").build()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { media.onMediaNotification(LIMITED, plain) }
        Thread.sleep(500)
        assertTrue(media.sessions.value.isEmpty())
    }

    @Test
    fun duringALock_limitedMediaIsPaused_andPausedAgainWhenPlayedAgain() = runBlocking {
        deliverMediaNotification()
        startCoordinator()
        assertTrue(Device.waitFor { media.sessions.value.any { it.playing } })
        Thread.sleep(2_000)
        assertEquals("paused with time left", 0, pauses.get())

        engine.apply(TimerCommand.LockNow)
        assertTrue("not paused during the lock", Device.waitFor { pauses.get() >= 1 })
        // The child presses play in the notification shade.
        setPlaying(true)
        assertTrue("not paused again", Device.waitFor { pauses.get() >= 2 })
    }

    @Test
    fun anAppAllowedDuringTheLock_keepsPlaying() = runBlocking {
        settings.setAppLimited(LIMITED, false)
        deliverMediaNotification()
        startCoordinator()
        engine.apply(TimerCommand.LockNow)
        Thread.sleep(3_000)
        assertEquals(0, pauses.get())
    }

    @Test
    fun backgroundPlayback_countsWhileTheScreenIsOn() {
        assertTrue(TimerService.start(context))
        assertTrue(Device.waitFor { TimerService.running })
        assertTrue(Device.waitFor { engine.status.value != null })
        tracker.onAccessibilityConnected()
        // Something else is on screen: only the audio is left of the limited app.
        tracker.onWindows(listOf(AppWindow(OTHER, isApplication = true, isActive = true)))
        setPlaying(false)
        deliverMediaNotification()
        assertTrue(Device.waitFor { engine.status.value?.counting == false })

        setPlaying(true)
        assertTrue("background audio wasn't counted", Device.waitFor { engine.status.value?.counting == true })
        assertEquals(setOf(LIMITED), engine.status.value?.appsInUse)
        setPlaying(false)
        assertTrue(Device.waitFor { engine.status.value?.counting == false })
    }

    private suspend fun startCoordinator() {
        tracker.refreshScreen()
        tracker.onAccessibilityConnected()
        engine.update(emptySet(), DetectionSource.ACCESSIBILITY, settings.settings.first())
        scope.launch { coordinator.run() }
    }

    /** What the Accessibility service passes on: the limited app's media-style notification. */
    private fun deliverMediaNotification() {
        val notification = Notification.Builder(context, channel())
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .build()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { media.onMediaNotification(LIMITED, notification) }
    }

    private fun channel(): String {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "itest", NotificationManager.IMPORTANCE_LOW))
        return CHANNEL
    }

    private fun setPlaying(playing: Boolean) {
        val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder().setState(state, 0, 1f).setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PAUSE).build(),
        )
    }

    private companion object {
        // The session is this test's own; Nour Time knows it by the package the notification came from.
        const val LIMITED = "com.nourtime.itest.limited"
        const val OTHER = "com.nourtime.itest.other"
        const val CHANNEL = "itest_media"
    }
}
