package com.nourtime.app.service.media

import android.app.Notification
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nourtime.app.core.media.MediaSessionInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Limited apps' media sessions, learned from their media notifications (the Accessibility service
 * passes on only the session handle of limited apps, never a notification's text). Tracks whether
 * each one plays, and pauses it on request. Media of other apps is never touched.
 */
@Singleton
class MediaWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())
    private val controllers = mutableMapOf<String, Watched>()

    private val _sessions = MutableStateFlow<List<MediaSessionInfo>>(emptyList())
    val sessions: StateFlow<List<MediaSessionInfo>> = _sessions.asStateFlow()

    private inner class Watched(val packageName: String, val controller: MediaController) : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = synchronized(controllers) { publish() }
        override fun onSessionDestroyed() = forget(packageName, controller.sessionToken)
    }

    /** A media notification of a limited app; call on the main thread. */
    fun onMediaNotification(packageName: String, notification: Notification) {
        sessionToken(notification)?.let { onSession(packageName, it) }
    }

    /** Starts following [token] for [packageName] (replacing an older session of the app). */
    fun onSession(packageName: String, token: MediaSession.Token) = main.runOrPost {
        val current = controllers[packageName]
        if (current?.controller?.sessionToken == token) return@runOrPost
        current?.let { it.controller.unregisterCallback(it) }
        val controller = runCatching { MediaController(context, token) }.getOrNull() ?: return@runOrPost
        val watched = Watched(packageName, controller)
        controller.registerCallback(watched, main)
        controllers[packageName] = watched
        publish()
    }

    /** Pauses [packageName]'s session; true when there was one to ask. */
    fun pause(packageName: String): Boolean {
        val controller = synchronized(controllers) { controllers[packageName]?.controller } ?: return false
        return runCatching { controller.transportControls.pause() }
            .onFailure { Log.w(TAG, "couldn't pause $packageName", it) }
            .isSuccess
    }

    private fun forget(packageName: String, token: MediaSession.Token) = main.runOrPost {
        val current = controllers[packageName] ?: return@runOrPost
        if (current.controller.sessionToken != token) return@runOrPost
        current.controller.unregisterCallback(current)
        controllers.remove(packageName)
        publish()
    }

    /** Under the controllers' lock. */
    private fun publish() {
        _sessions.value = controllers.map { (pkg, w) -> MediaSessionInfo(pkg, w.controller.playbackState.isPlaying()) }
    }

    private fun PlaybackState?.isPlaying(): Boolean = when (this?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
        -> true
        else -> false
    }

    private inline fun Handler.runOrPost(crossinline block: () -> Unit) {
        val run = Runnable { synchronized(controllers) { block() } }
        if (Looper.myLooper() == looper) run.run() else post(run)
    }

    private companion object {
        const val TAG = "MediaWatcher"

        /** The media session handle a media-style notification carries, or null for any other notification. */
        fun sessionToken(notification: Notification): MediaSession.Token? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            } else {
                @Suppress("DEPRECATION")
                notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
            }
    }
}
