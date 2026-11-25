package com.darryl.unscroll.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2 hash of the lock password. Only the hash and salt are stored. */
data class LockSecret(val hash: String, val salt: String)

object PasswordLock {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun create(password: String, random: SecureRandom = SecureRandom()): LockSecret {
        val salt = ByteArray(16).also(random::nextBytes)
        return LockSecret(encode(derive(password, salt)), encode(salt))
    }

    fun verify(password: String, secret: LockSecret): Boolean {
        val salt = Base64.getDecoder().decode(secret.salt)
        return MessageDigest.isEqual(derive(password, salt), Base64.getDecoder().decode(secret.hash))
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
}
