package com.nourtime.app.remote.parent

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nourtime.app.data.mode.AppMode
import com.nourtime.app.data.mode.AppModeRepository
import com.nourtime.app.feature.parent.ParentNotifications
import dagger.hilt.android.AndroidEntryPoint
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { pushTokens.onNewToken(auth.user.value?.uid, token) }
    }

    /** Called on a background thread; the mode is read from the settings file first. */
    override fun onMessageReceived(message: RemoteMessage) {
        val parent = runBlocking { modes.mode.first() == AppMode.PARENT }
        if (!parent) return
        pushMessageOf(message.data)?.let { ParentNotifications.show(this, it) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
