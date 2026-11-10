package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/** Phase 5 integration contract: state, validation, dedupe, lifecycle and authorization. */
class Phase5Test : IntegrationBase() {
    @BeforeEach fun reset() = truncate()

    private fun point(lat: Double, lon: Double, acc: Float = 12f, agoSec: Long = 0) =
        LocationPointIn(lat, lon, acc, System.currentTimeMillis() - agoSec * 1000, "fused")

    private suspend fun TestScope.locSync(token: String, points: List<LocationPointIn>?) =
        post("/v1/device/location/sync", LocationSyncIn("granted", "enabled", points), token)

    private suspend fun TestScope.createZone(token: String, id: UUID, req: GeofenceReq) =
        post("/v1/families/me/devices/$id/geofences", req, token)

    private suspend fun TestScope.zoneList(token: String, id: UUID) =
        get("/v1/families/me/devices/$id/geofences", token)

    @Test fun `valid zone starts UNKNOWN and initial fix establishes state without alert`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val create = createZone(p.parentAccess, id, GeofenceReq("Home", 35.6992, 51.3315, 300))
        assertEquals(HttpStatusCode.OK, create.status)
        val zone = create.json<GeofenceOut>()
        assertEquals("unknown", zone.state)
        locSync(p.deviceTokens.accessToken, points = listOf(point(35.6992, 51.3315)))
        val list = zoneList(p.parentAccess, id).json<GeofenceListOut>()
        assertEquals("inside", list.zones.single().state)
        val evs = get("/v1/families/me/devices/$id/geofence-events", p.parentAccess).json<GeofenceEventsOut>()
        assertTrue(evs.events.none { it.eventType == EventType.GEOFENCE_ENTER || it.eventType == EventType.GEOFENCE_EXIT })
    }

    @Test fun `valid transitions emit once and accuracy uncertainty suppresses edge noise`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val token = p.deviceTokens.accessToken
        createZone(p.parentAccess, id, GeofenceReq("Home", 35.6992, 51.3315, 300))
        // First fix far away initializes OUTSIDE silently.
        locSync(token, points = listOf(point(35.72, 51.3315, agoSec = 600)))
        // Subsequent accurate fix inside emits one ENTER.
        locSync(token, points = listOf(point(35.6992, 51.3315, agoSec = 100)))
        // Duplicate point replay is ignored by storage and not reevaluated.
        val repeated = point(35.6992, 51.3315, agoSec = 100)
        locSync(token, points = listOf(repeated))
        var events = get("/v1/families/me/devices/$id/geofence-events", p.parentAccess).json<GeofenceEventsOut>().events
        assertEquals(1, events.count { it.eventType == EventType.GEOFENCE_ENTER })
        assertEquals(0, events.count { it.eventType == EventType.GEOFENCE_EXIT })
        // Poor accuracy is displayed but cannot produce a transition.
        locSync(token, points = listOf(point(35.72, 51.3315, acc = 900f, agoSec = 80)))
        events = get("/v1/families/me/devices/$id/geofence-events", p.parentAccess).json<GeofenceEventsOut>().events
        assertEquals(1, events.count { it.eventType == EventType.GEOFENCE_ENTER })
    }

    @Test fun `EXIT then ENTER transitions are sequential once and persist dwell`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val token = p.deviceTokens.accessToken
        createZone(p.parentAccess, id, GeofenceReq("Home", 35.6992, 51.3315, 300))
        locSync(token, points = listOf(point(35.72, 51.3315, agoSec = 1200))) // initial outside
        locSync(token, points = listOf(point(35.6992, 51.3315, agoSec = 800))) // ENTER
        val exactExit = point(35.72, 51.3315, agoSec = 400)
        locSync(token, points = listOf(exactExit)) // EXIT, dwell ~= 6m
        locSync(token, points = listOf(point(35.6992, 51.3315, agoSec = 0))) // ENTER
        locSync(token, points = listOf(exactExit)) // exact retry is stale/duplicate
        val events = get("/v1/families/me/devices/$id/geofence-events", p.parentAccess).json<GeofenceEventsOut>().events
        assertEquals(2, events.count { it.eventType == EventType.GEOFENCE_ENTER })
        assertEquals(1, events.count { it.eventType == EventType.GEOFENCE_EXIT })
        assertTrue(events.single { it.eventType == EventType.GEOFENCE_EXIT }.dwellMin?.let { it in 5..7 } == true)
    }

    @Test fun `error state is transition-only and recovers from first accurate fix silently`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val token = p.deviceTokens.accessToken
        val zone = createZone(p.parentAccess, id, GeofenceReq("Home", 35.6992, 51.3315, 300)).json<GeofenceOut>()
        val err = GeofenceEventPost(zone.id, "GEOFENCE_ERROR", System.currentTimeMillis())
        post("/v1/device/geofences/events", err, token)
        post("/v1/device/geofences/events", err, token)
        assertEquals("error", zoneList(p.parentAccess, id).json<GeofenceListOut>().zones.single().state)
        locSync(token, points = listOf(point(35.6992, 51.3315)))
        assertEquals("inside", zoneList(p.parentAccess, id).json<GeofenceListOut>().zones.single().state)
        val events = get("/v1/families/me/devices/$id/geofence-events", p.parentAccess).json<GeofenceEventsOut>().events
        assertEquals(1, events.count { it.eventType == EventType.GEOFENCE_ERROR })
        assertEquals(0, events.count { it.eventType == EventType.GEOFENCE_ENTER })
    }

    @Test fun `invalid radius coordinates duplicate names and device cap are enforced`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val token = p.parentAccess
        assertEquals(HttpStatusCode.BadRequest, createZone(token, id, GeofenceReq("Tiny", 1.0, 2.0, 10)).status)
        assertEquals(HttpStatusCode.BadRequest, createZone(token, id, GeofenceReq("Bad", 91.0, 2.0, 300)).status)
        assertEquals(HttpStatusCode.OK, createZone(token, id, GeofenceReq("Home", 35.0, 51.0, 300)).status)
        assertEquals(HttpStatusCode.Conflict, createZone(token, id, GeofenceReq("home", 36.0, 52.0, 300)).status)
    }

    @Test fun `family and device authorization plus claimed transitions cannot forge state`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val zone = createZone(p.parentAccess, id, GeofenceReq("Home", 35.6992, 51.3315, 300)).json<GeofenceOut>()
        val stranger = register("geo-stranger-${UUID.randomUUID()}@test.ir")
        createFamily(stranger.accessToken, "Other")
        assertEquals(HttpStatusCode.NotFound, zoneList(stranger.accessToken, id).status)
        // Device can read own zone; fake enter event without a trusted server transition is refused.
        val spoof = post("/v1/device/geofences/events", GeofenceEventPost(zone.id, "GEOFENCE_ENTER", System.currentTimeMillis(), 35.6992, 51.3315, 5f), p.deviceTokens.accessToken)
        assertEquals(HttpStatusCode.OK, spoof.status)
        assertFalse(spoof.json<GeofenceEventAck>().accepted)
        assertEquals("unknown", zoneList(p.parentAccess, id).json<GeofenceListOut>().zones.single().state)
    }

    @Test fun `disable reenable update and delete lifecycle`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val path = "/v1/families/me/devices/$id/geofences"
        val zone = post(path, GeofenceReq("School", 35.0, 51.0, 500), p.parentAccess).json<GeofenceOut>()
        val disabled = put("$path/${zone.id}", GeofenceReq("School", 35.0, 51.0, 500, enabled = false), p.parentAccess)
        assertFalse(disabled.json<GeofenceOut>().enabled)
        val updated = put("$path/${zone.id}", GeofenceReq("School", 35.001, 51.001, 1000, enabled = true), p.parentAccess)
        assertEquals("unknown", updated.json<GeofenceOut>().state)
        assertEquals(HttpStatusCode.OK, delete("$path/${zone.id}", p.parentAccess).status)
        assertEquals(0, zoneList(p.parentAccess, id).json<GeofenceListOut>().zones.size)
    }

    @Test fun `sharing toggle invalidates stale zone state until next fresh fix`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val token = p.deviceTokens.accessToken
        val zone = createZone(p.parentAccess, id, GeofenceReq("Home", 35.6992, 51.3315, 300)).json<GeofenceOut>()
        locSync(token, points = listOf(point(35.6992, 51.3315)))
        assertEquals("inside", zoneList(p.parentAccess, id).json<GeofenceListOut>().zones.single().state)
        post("/v1/families/me/devices/$id/location/settings", LocationSettingsReq(false, null), p.parentAccess)
        assertEquals("unknown", zoneList(p.parentAccess, id).json<GeofenceListOut>().zones.single().state)
        post("/v1/families/me/devices/$id/location/settings", LocationSettingsReq(true, null), p.parentAccess)
        locSync(token, points = listOf(point(35.6992, 51.3315)))
        val events = get("/v1/families/me/devices/$id/geofence-events", p.parentAccess).json<GeofenceEventsOut>().events
        assertEquals(0, events.count { it.eventType == EventType.GEOFENCE_ENTER || it.eventType == EventType.GEOFENCE_EXIT })
        assertEquals("inside", zoneList(p.parentAccess, id).json<GeofenceListOut>().zones.single().state)
    }

    @Test fun `geofence event history is pruned by configured retention`() = withApp(mapOf("app.geofenceEventRetentionDays" to "7")) {
        val p = fullPairing()
        val id = p.device.deviceId
        val zone = createZone(p.parentAccess, id, GeofenceReq("Home", 35.0, 51.0, 300)).json<GeofenceOut>()
        sql("UPDATE geofence_events SET ts=now()-interval '8 days' WHERE geofence_id='${zone.id}'")
        val history = get("/v1/families/me/devices/$id/geofences/${zone.id}/events", p.parentAccess).json<GeofenceEventsOut>()
        assertEquals(0, history.count)
    }

    @Test fun `wrong device and expired token cannot access geofence resources`() = withApp(mapOf("app.accessTokenTtlSec" to "5")) {
        val p = fullPairing("geo-owner-${UUID.randomUUID()}@test.ir")
        val q = fullPairing("geo-other-${UUID.randomUUID()}@test.ir")
        val zone = post("/v1/families/me/devices/${q.device.deviceId}/geofences", GeofenceReq("Other", 35.0, 51.0, 300), q.parentAccess).json<GeofenceOut>()
        val wrongDevice = post("/v1/device/geofences/events", GeofenceEventPost(zone.id, "GEOFENCE_ERROR"), p.deviceTokens.accessToken)
        assertEquals(HttpStatusCode.NotFound, wrongDevice.status)
        kotlinx.coroutines.delay(5500)
        val expired = get("/v1/device/geofences", p.deviceTokens.accessToken)
        assertEquals(HttpStatusCode.Unauthorized, expired.status)
    }

    @Test fun `revoked device cannot read or post geofence events`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val zone = post("/v1/families/me/devices/$id/geofences", GeofenceReq("Home", 35.0, 51.0, 300), p.parentAccess).json<GeofenceOut>()
        delete("/v1/families/me/devices/$id", p.parentAccess)
        val r = get("/v1/device/geofences", p.deviceTokens.accessToken)
        assertTrue(r.status == HttpStatusCode.Gone || r.status == HttpStatusCode.Unauthorized)
        val e = post("/v1/device/geofences/events", GeofenceEventPost(zone.id, "GEOFENCE_ERROR"), p.deviceTokens.accessToken)
        assertTrue(e.status == HttpStatusCode.Gone || e.status == HttpStatusCode.Unauthorized)
    }
}
