package com.nourtime.app.remote.parent

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/** Seen on the API 35 emulator: a phone without a Google account was told to check the internet. */
class SignInFailureTest {

    @Test
    fun `cancelling the account picker is not an error`() {
        assertNull(SignInFailure.of(GetCredentialCancellationException()))
    }

    @Test
    fun `no Google account on the phone`() {
        assertEquals(SignInFailure.NO_ACCOUNT, SignInFailure.of(NoCredentialException()))
    }

    @Test
    fun `network problems`() {
        // FirebaseNetworkException can't be built in a JVM test (its constructor calls Android code).
        assertEquals(SignInFailure.OFFLINE, SignInFailure.of(IOException("reset")))
    }

    @Test
    fun `anything else`() {
        assertEquals(SignInFailure.OTHER, SignInFailure.of(GetCredentialUnknownException("28433")))
        assertEquals(SignInFailure.OTHER, SignInFailure.of(IllegalStateException("Unexpected credential type")))
    }
}
