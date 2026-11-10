package ir.guardianlink.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnitTest {
    @Test
    fun `redact never leaks raw secrets`() {
        val token = "super-secret-refresh-token-value-1234567890"
        val r = Redact.token(token)
        assertFalse(r.contains(token))
        assertEquals("<redacted>", r)
        val e = Redact.email("ali.rezaei@example.com")
        assertFalse(e.contains("ali.rezaei"))
        val c = Redact.code("ABCD1234")
        assertFalse(c.contains("ABCD1234"))
    }

    @Test
    fun `pairing code format is crockford 8`() {
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        repeat(50) {
            val c = TokenService.pairingCode()
            assertEquals(8, c.length)
            assertTrue(c.all { it in alphabet })
        }
    }

    @Test
    fun `sha256 stable and not plaintext`() {
        val h = Digest.sha256("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h)
        assertNotEquals("abc", h)
    }

    @Test
    fun `rate limiter window works`() {
        val rl = RateLimiter()
        assertTrue(rl.allow("k", 2, 60))
        assertTrue(rl.allow("k", 2, 60))
        assertFalse(rl.allow("k", 2, 60))
        assertTrue(rl.allow("other", 2, 60))
    }

    @Test
    fun `argon2 verify roundtrip`() {
        val h = PasswordHasher.hash("guardian-test-password")
        assertTrue(PasswordHasher.verify(h, "guardian-test-password"))
        assertFalse(PasswordHasher.verify(h, "password124"))
        assertFalse(h.contains("guardian-test-password"))
    }
}
