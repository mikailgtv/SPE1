package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class Phase4Test : IntegrationBase() {
    @BeforeEach
    fun reset() = truncate()

    private fun point(lat: Double = 35.6992, lon: Double = 51.3315, acc: Float = 18f, ageSec: Long = 0, src: String = "fused") =
        LocationPointIn(lat, lon, acc, System.currentTimeMillis() - ageSec * 1000, src)

    private suspend fun TestScope.locSync(
        t: String?,
        perm: String? = "granted",
        provider: String? = "enabled",
        points: List<LocationPointIn>? = null,
    ) = post("/v1/device/location/sync", LocationSyncIn(perm, provider, points), t)

    private suspend fun TestScope.current(id: UUID, token: String): LocationCurrentOut =
        get("/v1/families/me/devices/$id/location/current", token).json()

    private suspend fun TestScope.history(id: UUID, token: String): LocationHistoryOut =
        get("/v1/families/me/devices/$id/location/history", token).json()

    private suspend fun TestScope.events(id: UUID, token: String): List<EventOut> =
        get("/v1/families/me/devices/$id/events?limit=100", token).json()

    private suspend fun TestScope.saveLocSettings(id: UUID, token: String, sharing: Boolean? = null, retention: Int? = null) =
        post("/v1/families/me/devices/$id/location/settings", LocationSettingsReq(sharing, retention), token)

    // ================= Permission / state events =================
    @Test
    fun `permission granted emits LOCATION_PERMISSION_GRANTED once`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val t = p.deviceTokens.accessToken
        locSync(t, perm = "denied", provider = "enabled", points = null)
        locSync(t, perm = "granted", provider = "enabled", points = listOf(point()))
        var evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.LOCATION_PERMISSION_GRANTED })
        // sync تکراری با همان وضعیت ⇒ رویداد تکراری ندارد
        locSync(t, perm = "granted", provider = "enabled", points = listOf(point(ageSec = 1)))
        evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.LOCATION_PERMISSION_GRANTED })
    }

    @Test
    fun `permission revoked emits LOCATION_PERMISSION_REVOKED and health unavailable`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val t = p.deviceTokens.accessToken
        locSync(t, perm = "granted", provider = "enabled", points = listOf(point()))
        locSync(t, perm = "denied", provider = "enabled", points = null)
        val evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.LOCATION_PERMISSION_REVOKED })
        val cur = current(id, p.parentAccess)
        assertEquals("unavailable", cur.health)
        assertEquals("denied", cur.permissionStatus)
    }

    @Test
    fun `location services disabled emits LOCATION_DISABLED and LOCATION_UNAVAILABLE`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val t = p.deviceTokens.accessToken
        locSync(t, perm = "granted", provider = "enabled", points = listOf(point()))
        locSync(t, perm = "granted", provider = "disabled", points = null)
        val evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.LOCATION_DISABLED })
        assertEquals(1, evs.count { it.eventType == EventType.LOCATION_UNAVAILABLE })
        assertEquals("unavailable", current(id, p.parentAccess).health)
    }

    @Test
    fun `re-enable services emits LOCATION_ENABLED and recovery UPDATE_RECEIVED once`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val t = p.deviceTokens.accessToken
        locSync(t, perm = "granted", provider = "enabled", points = listOf(point()))
        locSync(t, perm = "granted", provider = "disabled", points = null)
        locSync(t, perm = "granted", provider = "enabled", points = listOf(point(ageSec = 1)))
        var evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.LOCATION_ENABLED })
        assertEquals(1, evs.count { it.eventType == EventType.LOCATION_UPDATE_RECEIVED })
        // fix تازه‌ی بعدی در وضعیت healthy ⇒ رویداد جدید ندارد (فقط گذار)
        locSync(t, perm = "granted", provider = "enabled", points = listOf(point(ageSec = 2)))
        evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.LOCATION_UPDATE_RECEIVED })
    }

    // ================= Location data =================
    @Test
    fun `valid location stored and returned as current`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val r = locSync(p.deviceTokens.accessToken, points = listOf(point(lat = 35.7, lon = 51.4, acc = 12f)))
        assertEquals(HttpStatusCode.OK, r.status)
        val cur = current(id, p.parentAccess)
        assertNotNull(cur.fix)
        assertEquals(35.7, cur.fix!!.latitude, 1e-9)
        assertEquals(51.4, cur.fix!!.longitude, 1e-9)
        assertEquals(12f, cur.fix!!.accuracyM!!, 0.01f)
        assertTrue(cur.fix!!.isCurrent)
        assertEquals("healthy", cur.health)
        assertTrue(cur.sharingEnabled)
    }

    @Test
    fun `low accuracy reported honestly`() = withApp {
        val p = fullPairing()
        locSync(p.deviceTokens.accessToken, points = listOf(point(acc = 2500f, src = "network")))
        val cur = current(p.device.deviceId, p.parentAccess)
        assertEquals(2500f, cur.fix!!.accuracyM!!, 0.01f)
        assertEquals("network", cur.fix!!.source)
    }

    @Test
    fun `old fix is not presented as current - delayed health and SYNC_DELAYED event`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        // ۲ ساعت قبل ⇒ داخل پنجره‌ی ۴۸ ساعت ولی قدیمی‌تر از staleSec=3600
        locSync(p.deviceTokens.accessToken, points = listOf(point(ageSec = 7200)))
        val cur = current(id, p.parentAccess)
        assertNotNull(cur.fix)
        assertFalse(cur.fix!!.isCurrent, "fix کهنه نباید current باشد")
        assertEquals("delayed", cur.health)
        val evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.LOCATION_SYNC_DELAYED })
    }

    @Test
    fun `missing location yields null fix and unavailable health`() = withApp {
        val p = fullPairing()
        val cur = current(p.device.deviceId, p.parentAccess)
        assertNull(cur.fix)
        assertEquals("unavailable", cur.health)
    }

    @Test
    fun `invalid coordinates and states rejected`() = withApp {
        val t = p0().deviceTokens.accessToken
        suspend fun bad(req: LocationSyncIn) = post("/v1/device/location/sync", req, t).status
        assertEquals(HttpStatusCode.BadRequest, bad(LocationSyncIn("granted", "enabled", listOf(point(lat = 91.0)))))
        assertEquals(HttpStatusCode.BadRequest, bad(LocationSyncIn("granted", "enabled", listOf(point(lon = 181.0)))))
        assertEquals(HttpStatusCode.BadRequest, bad(LocationSyncIn("granted", "enabled", listOf(point(ageSec = 200_000)))))
        assertEquals(HttpStatusCode.BadRequest, bad(LocationSyncIn("granted", "enabled", listOf(point().copy(source = "magic")))))
        assertEquals(HttpStatusCode.BadRequest, bad(LocationSyncIn("rooted", "enabled", null)))
        assertEquals(HttpStatusCode.BadRequest, bad(LocationSyncIn("granted", "broken", null)))
    }

    // ================= Offline batch + duplicates =================
    @Test
    fun `offline batch synced together preserves all points in order`() = withApp {
        val p = fullPairing()
        // صف آفلاین: ۵ نقطه با فاصله‌ی ۱۰ دقیقه
        val batch = (0..4).map { point(lat = 35.0 + it * 0.01, ageSec = (4 - it) * 600L) }
        val r = locSync(p.deviceTokens.accessToken, points = batch)
        val stored: Map<String, Any> = r.json()
        assertEquals(5, (stored["stored"] as Number).toInt())
        val h = history(p.device.deviceId, p.parentAccess)
        assertEquals(5, h.points.size)
        // نزولی بر اساس زمان
        assertTrue(h.points[0].timestamp >= h.points[1].timestamp)
    }

    @Test
    fun `duplicate points rejected by unique device+client_ts`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        val pt = point()
        locSync(t, points = listOf(pt))
        // بازفرستانی همان بسته (retry پس از reconnect)
        val r2: Map<String, Any> = locSync(t, points = listOf(pt)).json()
        assertEquals(0, (r2["stored"] as Number).toInt())
        val h = history(p.device.deviceId, p.parentAccess)
        assertEquals(1, h.points.size)
    }

    @Test
    fun `location embedded in sync request is stored`() = withApp {
        val p = fullPairing()
        val req = SyncReq(
            88, "wifi", null, null, null, null, null, 0, null,
            LocationSyncIn("granted", "enabled", listOf(point(lat = 36.0, lon = 52.0))),
        )
        assertEquals(HttpStatusCode.OK, post("/v1/device/sync", req, p.deviceTokens.accessToken).status)
        val cur = current(p.device.deviceId, p.parentAccess)
        assertEquals(36.0, cur.fix!!.latitude, 1e-9)
    }

    // ================= Sharing toggle =================
    @Test
    fun `sharing off drops coordinates but keeps status - on resumes`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val t = p.deviceTokens.accessToken
        val off = saveLocSettings(id, p.parentAccess, sharing = false)
        assertEquals(HttpStatusCode.OK, off.status)
        val r: Map<String, Any> = locSync(t, points = listOf(point())).json()
        assertEquals(0, (r["stored"] as Number).toInt(), "با sharing خاموش هیچ مختصاتی ذخیره نمی‌شود")
        val cur = current(id, p.parentAccess)
        assertFalse(cur.sharingEnabled)
        assertNull(cur.fix)
        assertEquals("unavailable", cur.health)
        // وضعیت مجوزها همچنان گزارش می‌شود
        assertEquals("granted", cur.permissionStatus)
        // روشن مجدد ⇒ ذخیره از سر گرفته می‌شود
        saveLocSettings(id, p.parentAccess, sharing = true)
        locSync(t, points = listOf(point(ageSec = 1)))
        val cur2 = current(id, p.parentAccess)
        assertTrue(cur2.sharingEnabled)
        assertNotNull(cur2.fix)
    }

    // ================= Retention =================
    @Test
    fun `retention purges points older than policy`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        // رکوردهای قدیمی مستقیم در DB (شبیه‌سازی داده‌های گذشته)
        sql(
            """INSERT INTO locations(device_id,latitude,longitude,accuracy_m,client_ts,source)
               VALUES('$id',35.0,51.0,10,now() - interval '10 days','fused'),
                     ('$id',35.1,51.1,10,now() - interval '20 days','fused')""",
        )
        locSync(p.deviceTokens.accessToken, points = listOf(point()))
        saveLocSettings(id, p.parentAccess, retention = 7)
        // خواندن current ⇒ purge تنبل
        val h = history(id, p.parentAccess)
        assertEquals(1, h.points.size, "فقط نقطه‌ی تازه باید بماند")
        assertEquals(7, h.retentionDays)
    }

    @Test
    fun `retention zero keeps only latest fix`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val t = p.deviceTokens.accessToken
        locSync(t, points = listOf(point(ageSec = 300)))
        locSync(t, points = listOf(point(ageSec = 60)))
        saveLocSettings(id, p.parentAccess, retention = 0)
        locSync(t, points = listOf(point(ageSec = 1)))
        val h = history(id, p.parentAccess)
        assertEquals(1, h.points.size)
    }

    @Test
    fun `invalid retention rejected`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        assertEquals(HttpStatusCode.BadRequest, saveLocSettings(id, p.parentAccess, retention = -1).status)
        assertEquals(HttpStatusCode.BadRequest, saveLocSettings(id, p.parentAccess, retention = 366).status)
        assertEquals(HttpStatusCode.OK, saveLocSettings(id, p.parentAccess, retention = 90).status)
        val s: LocationSettingsOut = get("/v1/families/me/devices/$id/location/settings", p.parentAccess).json()
        assertEquals(90, s.retentionDays)
    }

    // ================= Delete history =================
    @Test
    fun `delete history scopes really remove rows`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        sql(
            """INSERT INTO locations(device_id,latitude,longitude,accuracy_m,client_ts,source)
               VALUES('$id',35.0,51.0,10,now() - interval '2 hours','fused'),
                     ('$id',35.1,51.1,10,now() - interval '3 days','fused'),
                     ('$id',35.2,51.2,10,now() - interval '20 days','fused')""",
        )
        // امروز
        val r1: Map<String, Any> =
            delete("/v1/families/me/devices/$id/location/history?scope=today", p.parentAccess).json()
        assertEquals(1, (r1["deleted"] as Number).toInt())
        // ۷ روز اخیر (۳ روز پیش هم می‌رود)
        val r2: Map<String, Any> =
            delete("/v1/families/me/devices/$id/location/history?scope=last7", p.parentAccess).json()
        assertEquals(1, (r2["deleted"] as Number).toInt())
        // همه
        val r3: Map<String, Any> =
            delete("/v1/families/me/devices/$id/location/history?scope=all", p.parentAccess).json()
        assertEquals(1, (r3["deleted"] as Number).toInt())
        assertEquals(0, history(id, p.parentAccess).points.size)
        val badScope = delete("/v1/families/me/devices/$id/location/history?scope=week", p.parentAccess)
        assertEquals(HttpStatusCode.BadRequest, badScope.status)
    }

    // ================= Security =================
    @Test
    fun `unauthorized location requests rejected`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        assertEquals(HttpStatusCode.Unauthorized, locSync(null, points = listOf(point())).status)
        assertEquals(HttpStatusCode.Unauthorized, locSync("not-a-jwt", points = listOf(point())).status)
        // توکن والد نمی‌تواند موقعیت آپلود کند
        assertEquals(HttpStatusCode.Unauthorized, locSync(p.parentAccess, points = listOf(point())).status)
        // توکن دستگاه نمی‌تواند تاریخچه بخواند
        val r = get("/v1/families/me/devices/$id/location/history", p.deviceTokens.accessToken)
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `wrong family cannot access location endpoints`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val stranger = register("stranger4@test.ir")
        createFamily(stranger.accessToken, "Other Family")
        for (url in listOf("location/current", "location/history", "location/settings")) {
            val r = get("/v1/families/me/devices/$id/$url", stranger.accessToken)
            assertEquals(HttpStatusCode.NotFound, r.status, url)
        }
        val w = post("/v1/families/me/devices/$id/location/settings", LocationSettingsReq(false, null), stranger.accessToken)
        assertEquals(HttpStatusCode.NotFound, w.status)
        val del = delete("/v1/families/me/devices/$id/location/history?scope=all", stranger.accessToken)
        assertEquals(HttpStatusCode.NotFound, del.status)
        // دستگاه بیگانه حتی با id درست خانواده‌ی دیگر قابل خواندن نیست
        val otherDeviceId = UUID.randomUUID()
        val r2 = get("/v1/families/me/devices/$otherDeviceId/location/current", p.parentAccess)
        assertEquals(HttpStatusCode.NotFound, r2.status)
    }

    @Test
    fun `expired token rejected on location endpoints`() = withApp(mapOf("app.accessTokenTtlSec" to "5")) {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        assertEquals(HttpStatusCode.OK, locSync(t, points = listOf(point())).status)
        delay(5500)
        assertEquals(HttpStatusCode.Unauthorized, locSync(t, points = listOf(point())).status)
        val r = get("/v1/families/me/devices/${p.device.deviceId}/location/current", p.parentAccess)
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `removed device cannot sync location`() = withApp {
        val p = fullPairing()
        delete("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess)
        val r = locSync(p.deviceTokens.accessToken, points = listOf(point()))
        assertTrue(r.status == HttpStatusCode.Gone || r.status == HttpStatusCode.Unauthorized)
    }

    // helpers
    private suspend fun TestScope.p0() = fullPairing()
}
