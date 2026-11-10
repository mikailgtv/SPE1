package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/** Phase 8 integration contracts. Execution remains pending until a Gradle/PostgreSQL test environment is available. */
class Phase8Test : IntegrationBase() {
    @BeforeEach fun reset() = truncate()

    @Test fun `actionable event projection is family scoped and read acknowledge resolve lifecycle persists`() = withApp {
        val p=fullPairing()
        val deps=b.application.attributes[AppDepsKey]
        deps.db.tx { c -> deps.events.emit(c,p.device.deviceId,"BATTERY_LOW","{\"battery_level\":12}","phase8-test-${UUID.randomUUID()}") }
        val feed=get("/v1/families/me/notifications",p.parentAccess)
        assertEquals(HttpStatusCode.OK,feed.status)
        val page=feed.json<NotificationPage>()
        assertEquals(1,page.items.size)
        val item=page.items.single()
        assertEquals("BATTERY",item.category)
        assertEquals("WARNING",item.severity)
        assertEquals(1,page.unreadCount)
        assertEquals(HttpStatusCode.OK,post("/v1/families/me/notifications/${item.id}/acknowledge",null,p.parentAccess).status)
        assertEquals(HttpStatusCode.OK,post("/v1/families/me/notifications/${item.id}/resolve",null,p.parentAccess).status)
        val detail=get("/v1/families/me/notifications/${item.id}",p.parentAccess).json<NotificationRow>()
        assertEquals("RESOLVED",detail.status)
        assertTrue(detail.isRead)
    }

    @Test fun `administrative events are not projected as alert notifications`() = withApp {
        val p=fullPairing()
        val deps=b.application.attributes[AppDepsKey]
        deps.db.tx { c -> deps.events.emit(c,p.device.deviceId,"GEOFENCE_CREATED","{}","phase8-audit-${UUID.randomUUID()}") }
        val page=get("/v1/families/me/notifications",p.parentAccess).json<NotificationPage>()
        assertTrue(page.items.isEmpty())
    }

    @Test fun `notification state endpoints hide items outside authenticated family`() = withApp {
        val first=fullPairing()
        val second=fullPairing()
        val deps=b.application.attributes[AppDepsKey]
        deps.db.tx { c -> deps.events.emit(c,first.device.deviceId,"BATTERY_CRITICAL","{}","phase8-scope-${UUID.randomUUID()}") }
        val id=get("/v1/families/me/notifications",first.parentAccess).json<NotificationPage>().items.single().id
        assertEquals(HttpStatusCode.NotFound,post("/v1/families/me/notifications/$id/read",null,second.parentAccess).status)
    }
}
