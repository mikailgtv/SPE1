package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/** Integration contract for family isolation, versioned domain rules, overrides and honest capability reports. */
class Phase7Test : IntegrationBase() {
    @BeforeEach fun reset() = truncate()

    @Test fun `web settings and rules are family scoped and optimistic`() = withApp {
        val pairing = fullPairing()
        val deviceId = pairing.device.deviceId
        val initial = get("/v1/families/me/devices/$deviceId/web-safety", pairing.parentAccess)
            .json<WebSafetySettingsOut>()
        assertFalse(initial.enabled)
        assertEquals("UNKNOWN", initial.reportedStatus)
        val configured = put(
            "/v1/families/me/devices/$deviceId/web-safety",
            WebSafetySettingsReq(true, "BLOCK_LISTED", "MANAGED_DEVICE", true, initial.ruleVersion), pairing.parentAccess,
        ).json<WebSafetySettingsOut>()
        assertTrue(configured.enabled)
        assertEquals("MANAGED_DEVICE", configured.requestedMode)

        val created = post(
            "/v1/families/me/devices/$deviceId/web-safety/rules",
            WebSafetyRuleReq(
                domain = "HTTPS://Example.COM/", action = "BLOCK",
                schedules = listOf(WebSafetyScheduleReq(1, "22:00", "06:00", "ALLOW")),
            ), pairing.parentAccess,
        ).json<WebSafetyRuleOut>()
        assertEquals("example.com", created.domain)
        assertFalse(created.categoryEnforceable)
        assertEquals(1, created.schedules.size)
        assertEquals("ALLOW", created.schedules.single().action)
        assertEquals(
            HttpStatusCode.Conflict,
            post(
                "/v1/families/me/devices/$deviceId/web-safety/rules",
                WebSafetyRuleReq(domain = "example.com", action = "BLOCK"), pairing.parentAccess,
            ).status,
        )

        val stale = put(
            "/v1/families/me/devices/$deviceId/web-safety/rules/${created.id}",
            WebSafetyRuleReq(domain = "example.com", action = "ALLOW", expectedVersion = 0), pairing.parentAccess,
        )
        assertEquals(HttpStatusCode.Conflict, stale.status)

        val updated = put(
            "/v1/families/me/devices/$deviceId/web-safety/rules/${created.id}",
            WebSafetyRuleReq(domain = "example.com", action = "ALLOW", expectedVersion = created.version), pairing.parentAccess,
        ).json<WebSafetyRuleOut>()
        assertEquals("ALLOW", updated.action)
        assertTrue(updated.version > created.version)
        val temporary = post(
            "/v1/families/me/devices/$deviceId/web-safety/overrides",
            WebSafetyOverrideReq("temporary.example", durationSeconds = 900), pairing.parentAccess,
        ).json<WebSafetyOverrideOut>()
        assertTrue(get("/v1/families/me/devices/$deviceId/web-safety", pairing.parentAccess)
            .json<WebSafetySettingsOut>().activeOverrides.any { it.id == temporary.id })
        assertEquals(HttpStatusCode.OK, delete(
            "/v1/families/me/devices/$deviceId/web-safety/overrides/${temporary.id}", pairing.parentAccess,
        ).status)

        val foreign = fullPairing()
        assertEquals(
            HttpStatusCode.NotFound,
            get("/v1/families/me/devices/$deviceId/web-safety", foreign.parentAccess).status,
        )
        assertEquals(
            HttpStatusCode.NotFound,
            post("/v1/families/me/devices/$deviceId/web-safety/rules", WebSafetyRuleReq(domain = "foreign.example", action = "BLOCK"), foreign.parentAccess).status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            post("/v1/families/me/devices/$deviceId/web-safety/rules", WebSafetyRuleReq(domain = "invalid domain", action = "DROP"), pairing.parentAccess).status,
        )
    }

    @Test fun `child capability report is scoped to authenticated device and creates no blocked visit`() = withApp {
        val pairing = fullPairing()
        val deviceId = pairing.device.deviceId
        val child = pairing.deviceTokens.accessToken
        val rules = post(
            "/v1/families/me/devices/$deviceId/web-safety/rules",
            WebSafetyRuleReq(domain = "example.org", action = "BLOCK"), pairing.parentAccess,
        ).json<WebSafetyRuleOut>()
        val event = WebSafetyEventIn(UUID.randomUUID().toString(), "WEB_FILTERING_UNAVAILABLE", "managed_device_required", System.currentTimeMillis())
        repeat(2) {
            post(
                "/v1/device/web-safety/sync",
                WebSafetySyncIn("NORMAL", "UNSUPPORTED", "managed_device_required", events = listOf(event)), child,
            )
        }
        val settings = get("/v1/families/me/devices/$deviceId/web-safety", pairing.parentAccess).json<WebSafetySettingsOut>()
        assertEquals("UNSUPPORTED", settings.reportedStatus)
        assertTrue(settings.rules.any { it.id == rules.id })
        val events = get("/v1/families/me/devices/$deviceId/web-safety/events", pairing.parentAccess).json<WebSafetyEventsOut>()
        assertEquals(1, events.events.count { it.eventType == "WEB_FILTERING_UNAVAILABLE" })
        assertTrue(events.events.none { it.eventType in setOf("WEB_RULE_TRIGGERED", "WEB_CATEGORY_BLOCKED") })
    }
}
