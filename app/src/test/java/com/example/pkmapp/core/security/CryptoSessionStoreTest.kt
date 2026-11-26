package com.example.pkmapp.core.security

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
}
