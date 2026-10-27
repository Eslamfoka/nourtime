package com.nourtime.app.core.crash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "An FCM token may exist on this phone" (or FCM auto-init was turned on, which makes one): set before
 * pushes are turned on or a token is asked for or arrives, cleared only once auto-init is off and the
 * token deleted ([FirebaseCollectionController]). Null until this version first runs ([FcmTokenCleanup]).
 */
@Singleton
class FcmTokenFlag @Inject constructor(private val store: DataStore<Preferences>) {

    suspend fun read(): Boolean? = store.data.first()[MAY_EXIST]

    suspend fun set(mayExist: Boolean) {
        store.edit { it[MAY_EXIST] = mayExist }
    }

    private companion object {
        val MAY_EXIST = booleanPreferencesKey("fcm_token_may_exist")
    }
}
