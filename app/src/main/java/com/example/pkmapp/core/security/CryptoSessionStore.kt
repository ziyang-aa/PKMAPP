package com.example.pkmapp.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.pkmapp.core.database.AccountScope
import com.example.pkmapp.core.datastore.AppPreferences
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts session JWT tokens with Android Keystore AES-256-GCM before storing in DataStore.
 */
interface SessionKeyCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String?
}

object AndroidKeystoreAesGcmCipher : SessionKeyCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "pkmapp_v4_session_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_SEPARATOR = ":"

    override fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return "${PasswordHasher.toHex(iv)}$IV_SEPARATOR${PasswordHasher.toHex(encrypted)}"
    }

    override fun decrypt(ciphertext: String): String? {
        if (ciphertext.isEmpty()) return null
        val sep = ciphertext.indexOf(IV_SEPARATOR)
        if (sep <= 0 || sep >= ciphertext.length - 1) return null
        val iv = PasswordHasher.fromHex(ciphertext.substring(0, sep)) ?: return null
        val payload = PasswordHasher.fromHex(ciphertext.substring(sep + 1)) ?: return null
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(payload), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}

data class SessionCredentials(
    val ownerId: String = AccountScope.GUEST_OWNER_ID,
    val accessToken: String = "",
    val refreshToken: String = "",
    val serverBaseUrl: String = ""
) {
    val isGuest: Boolean
        get() = ownerId == AccountScope.GUEST_OWNER_ID || ownerId.isBlank()
}

class CryptoSessionStore(
    private val preferences: AppPreferences,
    private val cipher: SessionKeyCipher = AndroidKeystoreAesGcmCipher
) {
    fun currentSession(): SessionCredentials {
        val snap = preferences.current
        val ownerId = snap.activeOwnerId.ifBlank { AccountScope.GUEST_OWNER_ID }
        val access = if (snap.encryptedAccessToken.isNotEmpty()) {
            cipher.decrypt(snap.encryptedAccessToken).orEmpty()
        } else {
            ""
        }
        val refresh = if (snap.encryptedRefreshToken.isNotEmpty()) {
            cipher.decrypt(snap.encryptedRefreshToken).orEmpty()
        } else {
            ""
        }
        return SessionCredentials(
            ownerId = ownerId,
            accessToken = access,
            refreshToken = refresh,
            serverBaseUrl = snap.serverBaseUrl
        )
    }

    fun saveSession(ownerId: String, accessToken: String, refreshToken: String) {
        val safeOwner = ownerId.trim().ifEmpty { AccountScope.GUEST_OWNER_ID }
        val encAccess = if (accessToken.isNotEmpty()) cipher.encrypt(accessToken) else ""
        val encRefresh = if (refreshToken.isNotEmpty()) cipher.encrypt(refreshToken) else ""
        preferences.setSessionState(safeOwner, encAccess, encRefresh)
    }

    fun switchToGuest() {
        preferences.setSessionState(AccountScope.GUEST_OWNER_ID, "", "")
    }

    fun saveServerBaseUrl(url: String) {
        preferences.setServerBaseUrl(normalizeServerBaseUrl(url))
    }

    fun currentServerBaseUrl(): String = preferences.current.serverBaseUrl

    companion object {
        private val SCHEME_PREFIX = Regex("^(https?):/+", RegexOption.IGNORE_CASE)

        @JvmStatic
        fun normalizeServerBaseUrl(url: String): String {
            var s = url
                .replace('\uFF1A', ':') // 中文全角冒号 ：
                .replace('\uFF0F', '/') // 中文全角斜杠 ／
                .replace('\u3002', '.') // 中文句号 。
                .replace('\uFF0E', '.') // 全角点 ．
                .replace(Regex("[\\s\\u200B\\uFEFF]+"), "")
                .trimEnd('/')
            if (s.isEmpty()) return ""

            var scheme = "http"
            while (true) {
                val match = SCHEME_PREFIX.find(s) ?: break
                scheme = match.groupValues[1].lowercase()
                s = s.substring(match.range.last + 1).trimStart('/')
            }
            if (s.isEmpty()) return ""
            return "$scheme://$s"
        }

        @JvmStatic
        fun get(context: Context): CryptoSessionStore =
            CryptoSessionStore(AppPreferences.getInstance(context))
    }
}
