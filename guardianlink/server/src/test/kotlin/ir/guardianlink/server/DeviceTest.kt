package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceTest : IntegrationBase() {
    @BeforeEach
    fun reset() = truncate()

    @Test
    fun `device registration stores base info`() = withApp {
        val p = fullPairing()
        val d: DeviceOut = get("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess).json()
        assertEquals("Google", d.manufacturer)
        assertEquals("Pixel 7", d.model)
        assertEquals("16", d.androidVersion)
        assertEquals("0.1.0", d.appVersion)
        assertEquals("active", d.status)
        assertNotNull(d.createdAt)
    }

    @Test
    fun `heartbeat updates battery and last seen and presence online`() = withApp {
        val p = fullPairing()
        val hb = post(
            "/v1/device/heartbeat",
            HeartbeatReq(77, "0.1.0", "16", "Pixel 7"),
            p.deviceTokens.accessToken,
        )
        assertEquals(HttpStatusCode.OK, hb.status)
        val out = hb.json<HeartbeatOut>()
        assertEquals("online", out.presence)
        val d: DeviceOut = get("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess).json()
        assertEquals(77, d.batteryLevel)
        assertNotNull(d.lastSeen)
        assertEquals("online", d.presence)
    }

    @Test
    fun `battery update only changes battery when others null`() = withApp {
        val p = fullPairing()
        post("/v1/device/heartbeat", HeartbeatReq(41, null, null, null), p.deviceTokens.accessToken)
        val d: DeviceOut = get("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess).json()
        assertEquals(41, d.batteryLevel)
        assertEquals("Pixel 7", d.model)
    }

    @Test
    fun `offline when heartbeat older than lease`() = withApp(mapOf("app.presenceLeaseSec" to "1")) {
        val p = fullPairing()
        post("/v1/device/heartbeat", HeartbeatReq(50, null, null, null), p.deviceTokens.accessToken)
        Thread.sleep(1300)
        val d: DeviceOut = get("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess).json()
        assertEquals("offline", d.presence)
    }

    @Test
    fun `heartbeat validation`() = withApp {
        val p = fullPairing()
        val r = post("/v1/device/heartbeat", HeartbeatReq(150, null, null, null), p.deviceTokens.accessToken)
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun `remove device revokes everything`() = withApp {
        val p = fullPairing()
        val del = delete("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess)
        assertEquals(HttpStatusCode.NoContent, del.status)
        // heartbeat با توکن قبلی ⇒ Gone/Unauthorized
        val hb = post("/v1/device/heartbeat", HeartbeatReq(10, null, null, null), p.deviceTokens.accessToken)
        assertTrue(hb.status == HttpStatusCode.Gone || hb.status == HttpStatusCode.Unauthorized)
        // refresh دستگاه هم باطل
        val rf = post("/v1/device/refresh", DeviceRefreshReq(p.device.deviceId, p.deviceTokens.refreshToken))
        assertEquals(HttpStatusCode.Unauthorized, rf.status)
        // لیست خالی
        val list: List<DeviceOut> = get("/v1/families/me/devices", p.parentAccess).json()
        assertTrue(list.isEmpty())
    }
}
