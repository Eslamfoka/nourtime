package com.nourtime.app.remote.parent

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.nourtime.app.R
import com.nourtime.app.remote.FirebaseModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ParentUser(val uid: String, val email: String?, val name: String?)

/**
 * The parent's Google sign-in (Phase 2). Debug builds that use the Firebase emulator can also sign
 * in with a test account, because the emulator accepts an unsigned Google token.
 */
@Singleton
class ParentAuth @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val _user = MutableStateFlow(current())
    val user: StateFlow<ParentUser?> = _user.asStateFlow()

    init {
        auth.addAuthStateListener { _user.value = current() }
    }

    /** Only Google accounts count: an anonymous session belongs to a child's phone. */
    private fun current(): ParentUser? = auth.currentUser
        ?.takeIf { user -> user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } }
        ?.let { ParentUser(it.uid, it.email, it.displayName) }

    /** Shows the Google account picker; [activityContext] must be an Activity. */
    suspend fun signInWithGoogle(activityContext: Context) {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activityContext.getString(R.string.default_web_client_id))
            .build()
        val response = CredentialManager.create(activityContext)
            .getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        _user.value = current()
    }

    val testAccountAvailable: Boolean get() = FirebaseModule.usesEmulator

    /** Debug + emulator only: signs in as a made-up Google account. */
    suspend fun signInTestAccount(email: String, name: String) {
        check(testAccountAvailable) { "Test accounts only work with the Firebase emulator" }
        val token = JSONObject()
            .put("sub", "test-" + email.substringBefore('@'))
            .put("email", email)
            .put("email_verified", true)
            .put("name", name)
            .toString()
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        _user.value = current()
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
    }
}
