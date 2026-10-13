package com.nourtime.app.remote.parent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The parent's phone gets pushes only for the parent signed in on it (a phone may change hands). */
class PushTokensTest {

    private class FakeSource(var current: String = "tokA") : PushTokenSource {
        override suspend fun token() = current
        override suspend fun delete() {
            current = "tokB"
        }
    }

    private class FakeStore : TokenStore {
        val docs = mutableSetOf<Pair<String, String>>()
        override suspend fun put(uid: String, token: String) {
            docs += uid to token
        }
        override suspend fun remove(uid: String, token: String) {
            docs -= uid to token
        }
    }

    private val source = FakeSource()
    private val store = FakeStore()
    private val tokens = PushTokens(source, store)

    @Test
    fun `signing in registers this phone's token for that parent`() = runTest {
        tokens.register("p1")
        assertEquals(setOf("p1" to "tokA"), store.docs)
    }

    @Test
    fun `signing out removes it and gets a fresh token, so the next parent doesn't share it`() = runTest {
        tokens.register("p1")
        tokens.unregister("p1")
        tokens.register("p2")
        assertEquals(setOf("p2" to "tokB"), store.docs)
    }

    @Test
    fun `a new token from FCM is stored only while a parent is signed in`() = runTest {
        tokens.onNewToken(null, "tokC")
        assertEquals(emptySet<Pair<String, String>>(), store.docs)
        tokens.onNewToken("p1", "tokC")
        assertEquals(setOf("p1" to "tokC"), store.docs)
    }

    @Test
    fun `a failing store doesn't break sign-in`() = runTest {
        val broken = object : TokenStore {
            override suspend fun put(uid: String, token: String) = throw IllegalStateException("offline")
            override suspend fun remove(uid: String, token: String) = throw IllegalStateException("offline")
        }
        PushTokens(source, broken).register("p1")
        PushTokens(source, broken).unregister("p1")
    }
}
