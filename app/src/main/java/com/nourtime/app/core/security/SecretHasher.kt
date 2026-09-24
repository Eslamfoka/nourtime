package com.nourtime.app.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A salted PBKDF2 hash, serialized as `v1:<iterations>:<salt>:<hash>`. */
class HashedSecret(val salt: ByteArray, val hash: ByteArray, val iterations: Int) {

    fun encode(): String = listOf(
        VERSION,
        iterations.toString(),
        Base64.getEncoder().encodeToString(salt),
        Base64.getEncoder().encodeToString(hash),
    ).joinToString(SEPARATOR)

    companion object {
        private const val VERSION = "v1"
        private const val SEPARATOR = ":"

        fun decode(encoded: String): HashedSecret? {
            val parts = encoded.split(SEPARATOR)
            if (parts.size != 4 || parts[0] != VERSION) return null
            return runCatching {
                HashedSecret(
                    salt = Base64.getDecoder().decode(parts[2]),
                    hash = Base64.getDecoder().decode(parts[3]),
                    iterations = parts[1].toInt(),
                )
            }.getOrNull()
        }
    }
}

/** Hashes the parent PIN and the security answer. Plain values are never stored. */
class SecretHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    fun hash(secret: String): HashedSecret {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return HashedSecret(salt, derive(secret, salt, iterations), iterations)
    }

    fun verify(secret: String, stored: HashedSecret): Boolean =
        MessageDigest.isEqual(derive(secret, stored.salt, stored.iterations), stored.hash)

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 120_000
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
    }
}
