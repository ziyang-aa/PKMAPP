package com.example.pkmapp.core.security

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.pkmapp.core.datastore.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class CryptoSessionStoreTest {

    @Test
    fun normalizeServerBaseUrl_handlesFullWidthPunctuationAndDuplicateSchemes() {
        assertEquals("", CryptoSessionStore.normalizeServerBaseUrl("   "))
        assertEquals(
            "http://127.0.0.1:8080",
            CryptoSessionStore.normalizeServerBaseUrl("127.0.0.1:8080")
        )
        assertEquals(
            "http://127.0.0.1:8080",
            CryptoSessionStore.normalizeServerBaseUrl("http：//127.0.0.1:8080/")
        )
        assertEquals(
            "http://127.0.0.1:8080",
            CryptoSessionStore.normalizeServerBaseUrl("http://http：//127.0.0.1：8080")
        )
        assertEquals(
            "https://api.example.com",
            CryptoSessionStore.normalizeServerBaseUrl("http://https://api.example.com///")
        )
        assertEquals(
            "http://10.0.2.2:8080",
            CryptoSessionStore.normalizeServerBaseUrl("http:/10。0。2。2：8080")
        )
    }

    @Test
    fun mapPreferences_usesDefaultServerUrlWhenUnset_andHonorsExplicitEmptyOverride() {
        val defaultUrl = "http://8.136.157.206:8080"
        val unsetSnap = AppPreferences.mapPreferences(emptyPreferences(), defaultUrl)
        assertEquals(defaultUrl, unsetSnap.serverBaseUrl)

        val explicitlyCleared = mutablePreferencesOf(
            stringPreferencesKey("server_base_url") to ""
        )
        val clearedSnap = AppPreferences.mapPreferences(explicitlyCleared, defaultUrl)
        assertEquals("", clearedSnap.serverBaseUrl)
    }
}
