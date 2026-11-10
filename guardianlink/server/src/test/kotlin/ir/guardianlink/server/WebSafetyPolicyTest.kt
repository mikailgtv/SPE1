package ir.guardianlink.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

class WebSafetyPolicyTest {
    @Test fun `parent actor maps to server event origin allowed by schema`() {
        assertEquals("server", webEventStorageSource("parent"))
        assertEquals("server", webEventStorageSource("server"))
        assertEquals("device", webEventStorageSource("device"))
        assertTrue(runCatching { webEventStorageSource("unknown") }.isFailure)
    }

    @Test fun `normalizes scheme case idn and wildcard host`() {
        assertEquals("example.com", WebSafetyPolicy.normalizeDomain("HTTPS://Example.COM/", "EXACT_HOST").first)
        assertEquals("xn--bcher-kva.de", WebSafetyPolicy.normalizeDomain("bücher.de", "DOMAIN_AND_SUBDOMAINS").first)
        assertEquals("DOMAIN_AND_SUBDOMAINS", WebSafetyPolicy.normalizeDomain("*.example.com", "EXACT_HOST").second)
    }

    @Test fun `rejects URL details that cannot safely map to domain policy`() {
        listOf("https://user:pass@example.com", "https://example.com:8443", "https://example.com/path", "https://127.0.0.1").forEach {
            assertTrue(runCatching { WebSafetyPolicy.normalizeDomain(it, "DOMAIN_AND_SUBDOMAINS") }.isFailure, it)
        }
    }

    @Test fun `matching respects host boundaries and exact scope`() {
        assertTrue(WebSafetyPolicy.matchesDomain("example.com", "DOMAIN_AND_SUBDOMAINS", "a.example.com"))
        assertTrue(WebSafetyPolicy.matchesDomain("example.com", "DOMAIN_AND_SUBDOMAINS", "example.com"))
        assertFalse(WebSafetyPolicy.matchesDomain("example.com", "DOMAIN_AND_SUBDOMAINS", "evil-example.com"))
        assertFalse(WebSafetyPolicy.matchesDomain("example.com", "EXACT_HOST", "a.example.com"))
    }

    @Test fun `more specific action wins and allow wins exact tie`() {
        val rules = listOf(
            WebSafetyPolicy.HostRule("example.com", "DOMAIN_AND_SUBDOMAINS", "BLOCK"),
            WebSafetyPolicy.HostRule("safe.example.com", "DOMAIN_AND_SUBDOMAINS", "ALLOW"),
        )
        assertEquals("ALLOW", WebSafetyPolicy.resolveDomainAction("safe.example.com", rules))
        assertEquals("BLOCK", WebSafetyPolicy.resolveDomainAction("x.example.com", rules))
        assertEquals("ALLOW", WebSafetyPolicy.resolveDomainAction("example.com", listOf(
            rules[0], WebSafetyPolicy.HostRule("example.com", "DOMAIN_AND_SUBDOMAINS", "ALLOW"),
        )))
    }

    @Test fun `Chrome URL policy compiler maps broad and exact host scopes`() {
        val result = WebSafetyPolicy.compileChromePolicy("BLOCK_LISTED", listOf(
            WebSafetyPolicy.HostRule("example.com", "DOMAIN_AND_SUBDOMAINS", "BLOCK"),
            WebSafetyPolicy.HostRule("private.example", "EXACT_HOST", "ALLOW"),
        ))
        assertEquals(listOf("example.com"), result.blocklist)
        assertEquals(listOf(".private.example"), result.allowlist)
    }

    @Test fun `allowlisted only blocks star and adds allow exceptions`() {
        val result = WebSafetyPolicy.compileChromePolicy("ALLOW_LISTED_ONLY", listOf(
            WebSafetyPolicy.HostRule("example.com", "DOMAIN_AND_SUBDOMAINS", "ALLOW"),
            WebSafetyPolicy.HostRule("bad.example", "DOMAIN_AND_SUBDOMAINS", "BLOCK"),
        ))
        assertEquals(listOf("*"), result.blocklist)
        assertEquals(listOf("example.com"), result.allowlist)
    }

    @Test fun `overnight schedule belongs to start day and continues after midnight`() {
        val schedule = WebSafetyPolicy.Schedule(1, LocalTime.of(22, 0), LocalTime.of(6, 0), "BLOCK", "UTC")
        assertEquals("BLOCK", WebSafetyPolicy.scheduleAction(listOf(schedule), java.time.ZonedDateTime.parse("2026-09-28T23:00:00Z")))
        assertEquals("BLOCK", WebSafetyPolicy.scheduleAction(listOf(schedule), java.time.ZonedDateTime.parse("2026-09-29T05:59:00Z")))
        assertEquals(null, WebSafetyPolicy.scheduleAction(listOf(schedule), java.time.ZonedDateTime.parse("2026-09-29T06:00:00Z")))
    }
}
