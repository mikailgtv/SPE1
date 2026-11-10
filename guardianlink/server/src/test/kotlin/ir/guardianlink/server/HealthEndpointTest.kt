package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HealthEndpointTest : IntegrationBase() {
    @BeforeEach
    fun reset() = truncate()

    @Test
    fun `liveness does not require dependency checks`() = withApp {
        val response = get("/health/live")
        assertEquals(HttpStatusCode.OK, response.status)
        val body: Map<String, Any?> = response.json()
        assertEquals("live", body["status"])
    }

    @Test
    fun `readiness requires database and writable data directory`() = withApp {
        val response = get("/health/ready")
        assertEquals(HttpStatusCode.OK, response.status)
        val body: Map<String, Any?> = response.json()
        assertEquals("ready", body["status"])
        val checks = body["checks"] as Map<*, *>
        assertEquals("healthy", checks["database"])
        assertEquals("healthy", checks["storage"])
        assertEquals("not_configured", checks["push_notifications"])
    }

    @Test
    fun `request id is returned and invalid supplied id is replaced`() = withApp {
        val valid = UUID.randomUUID().toString()
        val response = client.get("/health/live") { header("X-Request-ID", valid) }
        assertTrue(runCatching { UUID.fromString(response.headers["X-Request-ID"]) }.isSuccess)
        assertTrue(response.headers["X-Request-ID"] != valid)
        val replaced = client.get("/health/live") { header("X-Request-ID", "not-a-uuid") }
        assertTrue(runCatching { UUID.fromString(replaced.headers["X-Request-ID"]) }.isSuccess)
        assertTrue(replaced.headers["X-Request-ID"] != "not-a-uuid")
    }

    @Test
    fun `metrics endpoint exports aggregate counters only`() = withApp {
        get("/health/live")
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        assertTrue(text.contains("guardianlink_http_requests_total"))
        assertTrue(text.contains("guardianlink_db_pool_active"))
        assertTrue(!text.contains("X-Request-ID"))
    }

    @Test
    fun `legacy health endpoint remains a readiness alias`() = withApp {
        val response = get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body: Map<String, Any?> = response.json()
        assertEquals("ready", body["status"])
        assertTrue(body.containsKey("checks"))
    }
}
