package com.nourtime.app.remote.parent

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
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
        auth.signInWithCredential(googleCredential(activityContext)).await()
        _user.value = current()
    }

    private suspend fun googleCredential(activityContext: Context): AuthCredential {
        val clientId = activityContext.getString(R.string.default_web_client_id)
        val manager = CredentialManager.create(activityContext)
        val picker = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .build()
        val credential = try {
            manager.getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(picker).build()).credential
        } catch (e: NoCredentialException) {
            // No Google account on this phone yet: Google's own "Sign in with Google" screen can add one.
            Log.i(TAG, "No Google account on the phone; showing Sign in with Google")
            val button = GetSignInWithGoogleOption.Builder(clientId).build()
            manager.getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(button).build()).credential
        }
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type ${credential.type}"
        }
        return GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
    }

    val testAccountAvailable: Boolean get() = FirebaseModule.usesEmulator

    /** Debug + emulator only: signs in as a made-up Google account. */
    suspend fun signInTestAccount(email: String, name: String) {
        auth.signInWithCredential(testCredential(email, name)).await()
        _user.value = current()
    }

    private fun testCredential(email: String, name: String?): AuthCredential {
        check(testAccountAvailable) { "Test accounts only work with the Firebase emulator" }
        val token = JSONObject()
            .put("sub", "test-" + email.substringBefore('@'))
            .put("email", email)
            .put("email_verified", true)
            .put("name", name)
            .toString()
        return GoogleAuthProvider.getCredential(token, null)
    }

    /**
     * Account deletion: deletes the Firebase account. Firebase asks for a recent sign-in first, so
     * the Google account picker may appear once more. Call after the data is gone.
     */
    suspend fun deleteAccount(activityContext: Context) {
        val user = auth.currentUser ?: return
        try {
            user.delete().await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            val email = user.email
            // The emulator only knows test accounts; a real project needs a fresh Google token.
            val credential = if (testAccountAvailable && email != null) testCredential(email, user.displayName) else googleCredential(activityContext)
            user.reauthenticate(credential).await()
            user.delete().await()
        }
        runCatching { CredentialManager.create(activityContext).clearCredentialState(ClearCredentialStateRequest()) }
        _user.value = null
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
    }

    private companion object {
        const val TAG = "ParentAuth"
    }
}
