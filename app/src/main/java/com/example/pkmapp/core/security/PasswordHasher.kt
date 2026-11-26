package com.example.pkmapp.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 password hashing for offline local account verification.
 * Plaintext passwords are never persisted to disk.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    data class HashedPassword(
        val hashHex: String,
        val saltHex: String
    )

    private val random = SecureRandom()

    fun hash(password: CharArray): HashedPassword {
        require(password.size >= 8) { "密码长度至少需要 8 位" }
        val salt = ByteArray(SALT_BYTES)
        random.nextBytes(salt)
        val derived = derive(password, salt)
        return HashedPassword(
            hashHex = toHex(derived),
            saltHex = toHex(salt)
        )
    }

    fun hash(password: String): HashedPassword {
        val chars = password.toCharArray()
        return try {
            hash(chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun verify(password: String, expectedHashHex: String, saltHex: String): Boolean {
        if (password.isEmpty() || expectedHashHex.isEmpty() || saltHex.isEmpty()) return false
        val chars = password.toCharArray()
        return try {
            val salt = fromHex(saltHex) ?: return false
            val expected = fromHex(expectedHashHex) ?: return false
            val actual = derive(chars, salt)
            MessageDigest.isEqual(actual, expected)
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    internal fun toHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = digits[v ushr 4]
            chars[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(chars)
    }

    internal fun fromHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = hex[i * 2].digitToIntOrNull(16) ?: return null
            val lo = hex[i * 2 + 1].digitToIntOrNull(16) ?: return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
