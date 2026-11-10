package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class PairingTest : IntegrationBase() {
    @BeforeEach
    fun reset() = truncate()

    @Test
    fun `valid pairing code full flow`() = withApp {
        val p = fullPairing()
        // دستگاه در لیست خانواده است
        val devices: List<DeviceOut> = get("/v1/families/me/devices", p.parentAccess).json()
        assertEquals(1, devices.size)
        assertEquals("Test Device", devices[0].deviceName)
        assertEquals("Pixel 7", devices[0].model)
        // کد مصرف شده
        val reuse = exchange(p.pairing.code, UUID.randomUUID().toString())
        assertEquals(HttpStatusCode.Conflict, reuse.status)
        assertEquals(ErrCode.PAIRING_USED, errorCode(reuse))
    }

    @Test
    fun `invalid code rejected`() = withApp {
        val r = exchange("ZZZZZZZZ")
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertEquals(ErrCode.PAIRING_INVALID, errorCode(r))
    }

    @Test
    fun `expired code rejected`() = withApp(mapOf("app.pairingTtlSec" to "1")) {
        val t = register("p@test.ir")
        createFamily(t.accessToken)
        val ps = createPairing(t.accessToken)
        Thread.sleep(1300)
        val r = exchange(ps.code)
        assertEquals(HttpStatusCode.Gone, r.status)
        assertEquals(ErrCode.PAIRING_EXPIRED, errorCode(r))
    }

    @Test
    fun `already used code rejected on exchange`() = withApp {
        val p = fullPairing()
        val r = exchange(p.pairing.code)
        assertEquals(HttpStatusCode.Conflict, r.status)
        assertEquals(ErrCode.PAIRING_USED, errorCode(r))
    }

    @Test
    fun `replay of exchange token rejected`() = withApp {
        val t = register("p@test.ir")
        createFamily(t.accessToken)
        val ps = createPairing(t.accessToken)
        val install = UUID.randomUUID().toString()
        val ex = exchange(ps.code, install).json<ExchangeOut>()
        confirm(ex.exchangeToken, install)
        // replay همان exchange token
        val r = post(
            "/v1/pairing/confirm",
            ConfirmReq(ex.exchangeToken, UUID.randomUUID().toString(), "D2", "G", "M", "16", "1"),
        )
        assertEquals(HttpStatusCode.Conflict, r.status)
        assertEquals(ErrCode.PAIRING_USED, errorCode(r))
    }

    @Test
    fun `device already connected rejected`() = withApp {
        val p = fullPairing()
        // همان install_id دوباره با کد جدید
        val t2 = register("p2@test.ir")
        createFamily(t2.accessToken)
        val ps2 = createPairing(t2.accessToken)
        val r = exchange(ps2.code, p.installId)
        assertEquals(HttpStatusCode.Conflict, r.status)
        assertEquals(ErrCode.DEVICE_ALREADY_CONNECTED, errorCode(r))
    }

    @Test
    fun `wrong family cannot see device`() = withApp {
        val p = fullPairing()
        val other = register("other@test.ir")
        createFamily(other.accessToken, "Other Family")
        val list: List<DeviceOut> = get("/v1/families/me/devices", other.accessToken).json()
        assertTrue(list.isEmpty())
        val r = get("/v1/families/me/devices/${p.device.deviceId}", other.accessToken)
        assertEquals(HttpStatusCode.NotFound, r.status)
    }

    @Test
    fun `pairing requires auth and family`() = withApp {
        val anon = post("/v1/pairing/sessions")
        assertEquals(HttpStatusCode.Unauthorized, anon.status)
        val t = register("nofamily@test.ir")
        val noFam = post("/v1/pairing/sessions", null, t.accessToken)
        assertEquals(HttpStatusCode.NotFound, noFam.status)
    }
}
