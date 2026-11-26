package com.example.pkmapp.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun hashAndVerify_usesUniqueRandomSaltAndConstantTimeCheck() {
        val first = PasswordHasher.hash("ForestPass2026")
        val second = PasswordHasher.hash("ForestPass2026")

        assertNotEquals(first.saltHex, second.saltHex)
        assertNotEquals(first.hashHex, second.hashHex)
        assertTrue(PasswordHasher.verify("ForestPass2026", first.hashHex, first.saltHex))
        assertTrue(PasswordHasher.verify("ForestPass2026", second.hashHex, second.saltHex))
        assertFalse(PasswordHasher.verify("WrongPass2026", first.hashHex, first.saltHex))
        assertFalse(PasswordHasher.verify("", first.hashHex, first.saltHex))
    }

    @Test(expected = IllegalArgumentException::class)
    fun hash_rejectsShortPassword() {
        PasswordHasher.hash("1234567")
    }
}
