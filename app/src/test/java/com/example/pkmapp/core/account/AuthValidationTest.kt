package com.example.pkmapp.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {

    @Test
    fun normalizeIdentifier_acceptsPhoneAndEmailAndRejectsInvalid() {
        assertEquals("13800138000", AuthRepository.normalizeIdentifier(" 138-0013-8000 "))
        assertEquals("13800138000", AuthRepository.normalizeIdentifier("+86 13800138000"))
        assertEquals("user@example.com", AuthRepository.normalizeIdentifier(" User@Example.COM "))
        assertEquals("+14155552671", AuthRepository.normalizeIdentifier("+1 415 555 2671"))

        assertNull(AuthRepository.normalizeIdentifier(""))
        assertNull(AuthRepository.normalizeIdentifier("12345"))
        assertNull(AuthRepository.normalizeIdentifier("not-an-email"))
        assertNull(AuthRepository.normalizeIdentifier("user@domain"))
    }
}
