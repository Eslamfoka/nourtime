package com.nourtime.app.remote.parent

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nourtime.app.core.crash.FcmTokenFlag
import com.nourtime.app.data.mode.AppMode
import com.nourtime.app.data.mode.AppModeRepository
import com.nourtime.app.feature.parent.ParentNotifications
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** Pushes for the parent's phone (a child's phone never registers a token, so it gets none). */
@AndroidEntryPoint
class ParentMessagingService : FirebaseMessagingService() {

    @Inject lateinit var modes: AppModeRepository
    @Inject lateinit var auth: ParentAuth
    @Inject lateinit var pushTokens: PushTokens
    @Inject lateinit var tokenFlag: FcmTokenFlag

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Deprecated with the token calls (see FirebasePushTokenSource); still delivered.
    @Deprecated("FCM token registration; see FirebasePushTokenSource")
    override fun onNewToken(token: String) {
        scope.launch {
            // So it's deleted when pushes go off, however it was made. A settings error mustn't crash this.
            runCatching { tokenFlag.set(true) }.onFailure { if (it is CancellationException) throw it }
            pushTokens.onNewToken(auth.user.value?.uid, token)
        }
    }

    /** Called on a background thread; the mode is read from the settings file first. */
    override fun onMessageReceived(message: RemoteMessage) {
        val parent = runBlocking { modes.mode.first() == AppMode.PARENT }
        if (!parent || !pushIsFor(message.data, auth.user.value?.uid)) return
        pushMessageOf(message.data)?.let { ParentNotifications.show(this, it) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
