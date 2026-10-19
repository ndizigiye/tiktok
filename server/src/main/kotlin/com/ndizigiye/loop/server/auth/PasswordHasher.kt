package com.ndizigiye.loop.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 password hashing with a random per-user salt. */
object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    data class Hashed(val hash: String, val salt: String)

    fun hash(password: String): Hashed {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return Hashed(encoder.encodeToString(derive(password, salt)), encoder.encodeToString(salt))
    }

    fun verify(password: String, hash: String, salt: String): Boolean {
        val expected = decoder.decode(hash)
        val actual = derive(password, decoder.decode(salt))
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
