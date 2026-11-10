package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Phase2Test : IntegrationBase() {
    @BeforeEach
    fun reset() = truncate()

    private suspend fun TestScope.health(id: java.util.UUID, token: String): HealthOut =
        get("/v1/families/me/devices/$id/health", token).json()

    private suspend fun TestScope.events(id: java.util.UUID, token: String): List<EventOut> =
        get("/v1/families/me/devices/$id/events?limit=50", token).json()

    // ---------------- Battery ----------------
    @Test
    fun `battery levels map to bands`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        for ((level, band) in listOf(100 to "normal", 50 to "normal", 30 to "low", 15 to "low", 5 to "critical")) {
            post("/v1/device/heartbeat", HeartbeatReq(level, null, null, null, "wifi"), t)
            val h = health(p.device.deviceId, p.parentAccess)
            assertEquals(band, h.batteryBand, "level=$level")
            assertEquals(level, h.batteryLevel)
        }
    }

    @Test
    fun `invalid battery rejected`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        assertEquals(HttpStatusCode.BadRequest, post("/v1/device/heartbeat", HeartbeatReq(101, null, null, null, "wifi"), t).status)
        assertEquals(HttpStatusCode.BadRequest, post("/v1/device/heartbeat", HeartbeatReq(-1, null, null, null, "wifi"), t).status)
    }

    @Test
    fun `battery events emitted on band transition only`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        post("/v1/device/heartbeat", HeartbeatReq(80, null, null, null, "wifi"), t)
        post("/v1/device/heartbeat", HeartbeatReq(20, null, null, null, "wifi"), t) // -> low
        post("/v1/device/heartbeat", HeartbeatReq(22, null, null, null, "wifi"), t) // same band
        val evs = events(p.device.deviceId, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.BATTERY_LOW })
        post("/v1/device/heartbeat", HeartbeatReq(10, null, null, null, "wifi"), t) // -> critical
        val evs2 = events(p.device.deviceId, p.parentAccess)
        assertEquals(1, evs2.count { it.eventType == EventType.BATTERY_CRITICAL })
        val meta = evs2.first { it.eventType == EventType.BATTERY_CRITICAL }.metadata
        assertEquals("10", meta["battery_level"])
    }

    @Test
    fun `thresholds configurable per family`() = withApp {
        val p = fullPairing()
        val put = put("/v1/families/me/settings", SettingsUpdateReq(50, 40, null, null, null), p.parentAccess)
        assertEquals(HttpStatusCode.OK, put.status)
        post("/v1/device/heartbeat", HeartbeatReq(45, null, null, null, "wifi"), p.deviceTokens.accessToken)
        val h = health(p.device.deviceId, p.parentAccess)
        assertEquals("low", h.batteryBand)
        val set: SettingsOut = get("/v1/families/me/settings", p.parentAccess).json()
        assertEquals(50, set.batteryLowPct)
        assertEquals(40, set.batteryCriticalPct)
    }

    // ---------------- Heartbeat / Health ----------------
    @Test
    fun `successful heartbeat makes health healthy and updates last sync fields`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        post("/v1/device/sync", SyncReq(77, "wifi", "0.2.0", "16", "Pixel 7", "Google", mapOf("location" to "not_configured"), 0), t)
        val h = health(p.device.deviceId, p.parentAccess)
        assertEquals(Health.HEALTHY, h.health)
        assertNotNull(h.lastHeartbeat)
        assertNotNull(h.lastSync)
        assertEquals("wifi", h.connectionState)
    }

    @Test
    fun `delayed heartbeat yields delayed health and SYNC_DELAYED event`() = withApp {
        val p = fullPairing()
        post("/v1/device/heartbeat", HeartbeatReq(90, null, null, null, "wifi"), p.deviceTokens.accessToken)
        sql("UPDATE device_status SET last_heartbeat = now() - interval '40 minutes'")
        val h = health(p.device.deviceId, p.parentAccess)
        assertEquals(Health.DELAYED, h.health)
        val evs = events(p.device.deviceId, p.parentAccess)
        assertTrue(evs.any { it.eventType == EventType.SYNC_DELAYED })
    }

    @Test
    fun `missing heartbeat beyond offline threshold yields offline and DEVICE_DISCONNECTED`() = withApp {
        val p = fullPairing()
        post("/v1/device/heartbeat", HeartbeatReq(90, null, null, null, "wifi"), p.deviceTokens.accessToken)
        sql("UPDATE device_status SET last_heartbeat = now() - interval '2 hours'")
        val h = health(p.device.deviceId, p.parentAccess)
        assertEquals(Health.OFFLINE, h.health)
        val evs = events(p.device.deviceId, p.parentAccess)
        assertTrue(evs.any { it.eventType == EventType.DEVICE_DISCONNECTED })
    }

    @Test
    fun `duplicate heartbeat does not duplicate events`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        post("/v1/device/heartbeat", HeartbeatReq(90, null, null, null, "wifi"), t)
        post("/v1/device/heartbeat", HeartbeatReq(90, null, null, null, "wifi"), t)
        val evs = events(p.device.deviceId, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.DEVICE_CONNECTED })
    }

    @Test
    fun `unauthorized heartbeat rejected`() = withApp {
        val r = post("/v1/device/heartbeat", HeartbeatReq(90, null, null, null, "wifi"))
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    // ---------------- Offline / sync ----------------
    @Test
    fun `sync after offline brings DEVICE_CONNECTED and data applies`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        post("/v1/device/sync", SyncReq(60, "wifi", "0.2.0", "16", "Pixel 7", "Google", null, 0), t)
        sql("UPDATE device_status SET last_heartbeat = now() - interval '3 hours'")
        health(p.device.deviceId, p.parentAccess) // lazy transition to offline
        post("/v1/device/sync", SyncReq(61, "cellular", "0.2.1", "16", "Pixel 7", "Google", null, 0), t)
        val h = health(p.device.deviceId, p.parentAccess)
        assertEquals(Health.HEALTHY, h.health)
        val evs = events(p.device.deviceId, p.parentAccess)
        assertTrue(evs.any { it.eventType == EventType.DEVICE_CONNECTED })
        assertTrue(evs.any { it.eventType == EventType.APP_UPDATED })
        val d: DeviceOut = get("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess).json()
        assertEquals("0.2.1", d.appVersion)
        assertEquals(61, d.batteryLevel)
    }

    @Test
    fun `bad connection state and bad permission payloads rejected`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        assertEquals(HttpStatusCode.BadRequest, post("/v1/device/sync", SyncReq(50, "satellite", null, null, null, null, null, 0), t).status)
        assertEquals(HttpStatusCode.BadRequest, post("/v1/device/status", StatusReq(null, null, null, null, mapOf("BAD KEY" to "x")), t).status)
        assertEquals(HttpStatusCode.BadRequest, post("/v1/device/status", StatusReq(null, null, null, null, mapOf("location" to "maybe")), t).status)
    }

    // ---------------- Security ----------------
    @Test
    fun `revoked device cannot sync`() = withApp {
        val p = fullPairing()
        delete("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess)
        val r = post("/v1/device/sync", SyncReq(50, "wifi", null, null, null, null, null, 0), p.deviceTokens.accessToken)
        assertTrue(r.status == HttpStatusCode.Gone || r.status == HttpStatusCode.Unauthorized)
    }

    @Test
    fun `wrong family cannot read health or events`() = withApp {
        val p = fullPairing()
        val other = register("other2@test.ir")
        createFamily(other.accessToken, "Other")
        assertEquals(HttpStatusCode.NotFound, get("/v1/families/me/devices/${p.device.deviceId}/health", other.accessToken).status)
        assertEquals(HttpStatusCode.NotFound, get("/v1/families/me/devices/${p.device.deviceId}/events", other.accessToken).status)
        assertEquals(HttpStatusCode.NotFound, get("/v1/families/me/devices/${p.device.deviceId}/permissions", other.accessToken).status)
    }

    @Test
    fun `expired session rejected on monitoring endpoints`() = withApp(mapOf("app.accessTokenTtlSec" to "10")) {
        val p = fullPairing()
        // Complete registration/pairing first; avoid a one-second token expiring during Argon2 + HTTP setup.
        Thread.sleep(10_300)
        val r = get("/v1/families/me/devices/${p.device.deviceId}/health", p.parentAccess)
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertEquals(ErrCode.SESSION_EXPIRED, errorCode(r))
    }

    @Test
    fun `invalid device id rejected with validation`() = withApp {
        val p = fullPairing()
        val r = get("/v1/families/me/devices/not-a-uuid/health", p.parentAccess)
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    // ---------------- Permissions ----------------
    @Test
    fun `permission granted revoked and status sync`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        post("/v1/device/status", StatusReq("0.2.0", "16", "Pixel 7", "Google", mapOf("battery_optimization" to "optimized", "location" to "not_configured")), t)
        var perms: Map<String, Any> = get("/v1/families/me/devices/${p.device.deviceId}/permissions", p.parentAccess).json()
        @Suppress("UNCHECKED_CAST")
        val m1 = perms["permissions"] as Map<String, String>
        assertEquals("optimized", m1["battery_optimization"])
        var evs = events(p.device.deviceId, p.parentAccess)
        assertTrue(evs.any { it.eventType == EventType.PERMISSION_CHANGED })

        // revoke/change
        post("/v1/device/status", StatusReq("0.2.0", "16", "Pixel 7", "Google", mapOf("battery_optimization" to "unrestricted", "location" to "not_configured")), t)
        perms = get("/v1/families/me/devices/${p.device.deviceId}/permissions", p.parentAccess).json()
        @Suppress("UNCHECKED_CAST")
        val m2 = perms["permissions"] as Map<String, String>
        assertEquals("unrestricted", m2["battery_optimization"])
        evs = events(p.device.deviceId, p.parentAccess)
        val changed = evs.filter { it.eventType == EventType.PERMISSION_CHANGED }
        assertTrue(changed.any { it.metadata["key"] == "battery_optimization" && it.metadata["to"] == "unrestricted" })

        // sync همان مقدار ⇒ رویداد جدید نه
        val before = changed.size
        post("/v1/device/status", StatusReq("0.2.0", "16", "Pixel 7", "Google", mapOf("battery_optimization" to "unrestricted", "location" to "not_configured")), t)
        val after = events(p.device.deviceId, p.parentAccess).count { it.eventType == EventType.PERMISSION_CHANGED }
        assertEquals(before, after)
    }
}
