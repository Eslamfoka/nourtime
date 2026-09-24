package com.nourtime.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretHasherTest {

    private val hasher = SecretHasher(iterations = 1_000)

    @Test
    fun `verifies the original secret`() {
        val stored = hasher.hash("4827")
        assertTrue(hasher.verify("4827", stored))
    }

    @Test
    fun `rejects a different secret`() {
        val stored = hasher.hash("4827")
        assertFalse(hasher.verify("4828", stored))
    }

    @Test
    fun `uses a fresh salt every time`() {
        val a = hasher.hash("4827")
        val b = hasher.hash("4827")
        assertNotEquals(a.encode(), b.encode())
    }

    @Test
    fun `encoded form round-trips and still verifies`() {
        val encoded = hasher.hash("4827").encode()
        val decoded = HashedSecret.decode(encoded)!!
        assertEquals(1_000, decoded.iterations)
        assertTrue(hasher.verify("4827", decoded))
    }

    @Test
    fun `verifies with the iteration count stored in the hash`() {
        val stored = SecretHasher(iterations = 2_000).hash("4827")
        assertTrue(hasher.verify("4827", stored))
    }

    @Test
    fun `decode rejects malformed input`() {
        assertNull(HashedSecret.decode(""))
        assertNull(HashedSecret.decode("v2:1000:AAAA:AAAA"))
        assertNull(HashedSecret.decode("v1:abc:AAAA:AAAA"))
        assertNull(HashedSecret.decode("v1:1000:AAAA"))
    }
}
