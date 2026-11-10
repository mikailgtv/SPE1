package ir.guardianlink.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {
    @Test
    fun emailValidation() {
        assertTrue(Validators.isValidEmail("ali@test.ir"))
        assertTrue(Validators.isValidEmail("a.b+c@sub.example.com"))
        assertFalse(Validators.isValidEmail("ali@test"))
        assertFalse(Validators.isValidEmail("ali test.ir"))
        assertFalse(Validators.isValidEmail(""))
    }

    @Test
    fun passwordValidation() {
        assertFalse(Validators.isValidPassword("short"))
        assertTrue(Validators.isValidPassword("guardian-test-password"))
    }

    @Test
    fun pairingCodeNormalization() {
        assertEquals("ABCD1234", Validators.normalizePairingCode(" abcd-1234 "))
        assertTrue(Validators.isValidPairingCode("abcd1234"))
        assertTrue(Validators.isValidPairingCode("ABCD-1234"))
        assertFalse(Validators.isValidPairingCode("ABCD123"))      // short
        assertFalse(Validators.isValidPairingCode("ABCD12345"))     // long
        assertFalse(Validators.isValidPairingCode("IOU12345"))      // I,O,U not in Crockford
    }

    @Test
    fun redactNeverLeaks() {
        val t = "very-secret-refresh-token-abcdef"
        assertEquals("<redacted>", Redact.token(t))
        assertEquals("<redacted>", Redact.email("ali.rezaei@example.com"))
        assertEquals("<redacted>", Redact.code("ABCD1234"))
    }
}
