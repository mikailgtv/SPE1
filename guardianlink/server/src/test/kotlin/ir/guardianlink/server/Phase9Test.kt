package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class Phase9Test:IntegrationBase() {
    @BeforeEach fun reset()=truncate()
    private fun request(id:UUID,tracking:Int=0,location:SosLocationIn?=null)=SosActivateReq(
        id,System.currentTimeMillis(),"child_ui",22,"wifi","connected","granted","enabled",tracking,60,location,
    )
    private fun point(ts:Long)=SosLocationIn(35.6992,51.3315,12f,ts,"gps")

    @Test fun `child activation is idempotent and parent lifecycle is family scoped`()=withApp {
        val p=fullPairing();val deviceId=p.device.deviceId
        val activation=post("/v1/device/sos",request(UUID.randomUUID()),p.deviceTokens.accessToken)
        assertEquals(HttpStatusCode.OK,activation.status)
        val out=activation.json<SosActivateOut>()
        assertEquals("ACTIVE",out.session.status)
        val duplicate=post("/v1/device/sos",request(UUID.randomUUID()),p.deviceTokens.accessToken).json<SosActivateOut>()
        assertTrue(duplicate.alreadyActive);assertEquals(out.session.id,duplicate.session.id)
        assertEquals(1,get("/v1/families/me/sos/active",p.parentAccess).json<List<SosSessionOut>>().size)
        val other=fullPairing()
        assertEquals(HttpStatusCode.NotFound,get("/v1/families/me/sos/${out.session.id}",other.parentAccess).status)
        assertEquals(HttpStatusCode.OK,post("/v1/families/me/sos/${out.session.id}/acknowledge",emptyMap<String,String>(),p.parentAccess).status)
        assertEquals(HttpStatusCode.OK,post("/v1/families/me/sos/${out.session.id}/resolve",SosResolveReq("Situation handled"),p.parentAccess).status)
        val details=get("/v1/families/me/sos/${out.session.id}",p.parentAccess).json<SosSessionOut>()
        assertEquals("RESOLVED",details.status);assertEquals(p.device.deviceId,details.deviceId)
        val feed=get("/v1/families/me/notifications?category=EMERGENCY",p.parentAccess).json<NotificationPage>()
        assertTrue(feed.items.any { it.eventType=="SOS_ACTIVATED" })
    }

    @Test fun `offline replay keeps original trigger timestamp in the audit timeline`()=withApp {
        val p=fullPairing();val triggered=System.currentTimeMillis()-5*60_000L
        val out=post("/v1/device/sos",request(UUID.randomUUID()).copy(clientTriggeredAt=triggered),p.deviceTokens.accessToken).json<SosActivateOut>()
        val events=get("/v1/families/me/sos/${out.session.id}/events",p.parentAccess).json<List<SosEventOut>>()
        val activation=events.single { it.eventType=="SOS_ACTIVATED" }
        assertTrue(activation.metadata.contains("queued_delay_sec"))
        assertTrue(activation.metadata.contains("delayed_sync"))
    }

    @Test fun `tracking updates are validated deduplicated and appear in SOS timeline`()=withApp {
        val p=fullPairing();val ts=System.currentTimeMillis()
        val trackingRequest = request(UUID.randomUUID(),15,point(ts)).copy(permissionStatus="background_granted")
        val activated=post("/v1/device/sos",trackingRequest,p.deviceTokens.accessToken).json<SosActivateOut>()
        assertTrue(activated.session.trackingEnabled)
        val sessionId=activated.session.id
        val first=post("/v1/device/sos/$sessionId/locations",SosLocationBatchReq(listOf(point(ts+1000))),p.deviceTokens.accessToken).json<Map<String,Int>>()
        val replay=post("/v1/device/sos/$sessionId/locations",SosLocationBatchReq(listOf(point(ts+1000))),p.deviceTokens.accessToken).json<Map<String,Int>>()
        assertEquals(1,first["stored"]);assertEquals(0,replay["stored"])
        val locations=get("/v1/families/me/sos/$sessionId/location",p.parentAccess).json<List<SosLocationOut>>()
        assertEquals(2,locations.size)
        val events=get("/v1/families/me/sos/$sessionId/events",p.parentAccess).json<List<SosEventOut>>()
        assertTrue(events.any { it.eventType=="SOS_LOCATION_UPDATED" })
        assertFalse(events.any { it.metadata.contains("35.6992") })
    }

    @Test fun `expiration is persisted as terminal state and audit event`()=withApp {
        val p=fullPairing()
        val activated=post("/v1/device/sos",request(UUID.randomUUID()),p.deviceTokens.accessToken).json<SosActivateOut>()
        val deps=b.application.attributes[AppDepsKey]
        deps.db.exec("UPDATE sos_sessions SET expires_at=now()-interval '1 minute' WHERE id=?",activated.session.id)
        val history=get("/v1/families/me/devices/${p.device.deviceId}/sos/history",p.parentAccess).json<List<SosSessionOut>>()
        assertEquals("EXPIRED",history.single().status)
        val events=get("/v1/families/me/sos/${activated.session.id}/events",p.parentAccess).json<List<SosEventOut>>()
        assertTrue(events.any { it.eventType=="SOS_EXPIRED" })
    }

    @Test fun `child cancellation and duplicate active index preserve one open session`()=withApp {
        val p=fullPairing()
        val one=post("/v1/device/sos",request(UUID.randomUUID()),p.deviceTokens.accessToken).json<SosActivateOut>()
        val two=post("/v1/device/sos",request(UUID.randomUUID()),p.deviceTokens.accessToken).json<SosActivateOut>()
        assertEquals(one.session.id,two.session.id)
        val cancelled=post("/v1/device/sos/${one.session.id}/cancel",SosCancelReq("False alarm"),p.deviceTokens.accessToken).json<SosSessionOut>()
        assertEquals("CANCELLED",cancelled.status)
        assertTrue(get("/v1/families/me/sos/active",p.parentAccess).json<List<SosSessionOut>>().isEmpty())
    }
}
