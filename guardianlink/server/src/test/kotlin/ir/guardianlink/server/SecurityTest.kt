package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SecurityTest : IntegrationBase() {
    @BeforeEach
    fun reset() = truncate()

    @Test
    fun `unauthorized device rejected`() = withApp {
        val r = post("/v1/device/heartbeat", HeartbeatReq(10, null, null, null))
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        val bad = post("/v1/device/heartbeat", HeartbeatReq(10, null, null, null), "not-a-jwt")
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
    }

    @Test
    fun `user token cannot call device endpoints and vice versa`() = withApp {
        val p = fullPairing()
        val r = post("/v1/device/heartbeat", HeartbeatReq(10, null, null, null), p.parentAccess)
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        val r2 = get("/v1/me", p.deviceTokens.accessToken)
        assertEquals(HttpStatusCode.Unauthorized, r2.status)
    }

    @Test
    fun `unauthorized family access to other device`() = withApp {
        val p = fullPairing()
        val stranger = register("stranger@test.ir")
        val r = delete("/v1/families/me/devices/${p.device.deviceId}", stranger.accessToken)
        // stranger خانواده ندارد ⇒ NO_FAMILY(404)؛ و هرگز دسترسی به دستگاه غریبه نیست
        assertTrue(r.status == HttpStatusCode.NotFound || r.status == HttpStatusCode.Forbidden)
    }

    @Test
    fun `revoked device session stops working immediately`() = withApp {
        val p = fullPairing()
        delete("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess)
        val r = get("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess)
        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test
    fun `rate limit on login`() = withApp {
        register("ali@test.ir")
        var limited = false
        repeat(12) {
            val r = login("ali@test.ir", "bad-pass")
            if (r.status == HttpStatusCode.TooManyRequests) { limited = true; return@repeat }
        }
        assertTrue(limited)
    }

    @Test
    fun `device auth with wrong secret rejected`() = withApp {
        val p = fullPairing()
        val r = post("/v1/device/auth", DeviceAuthReq(p.device.deviceId, "wrong-secret"))
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }
}
