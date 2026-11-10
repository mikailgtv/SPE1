package ir.guardianlink.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObservabilityUnitTest {
    @Test
    fun `route templates never retain ids or arbitrary package segments`() {
        val route = ObservabilityMetrics.routeTemplate(
            "/v1/families/me/devices/01a42fc4-1dc2-4b4e-91da-a5cb5ec21df2/apps/com.private.app/usage?latitude=51.5",
        )
        assertTrue(route.contains("{id}"))
        assertFalse(route.contains("01a42fc4"))
        assertFalse(route.contains("com.private.app"))
        assertFalse(route.contains("51.5"))
    }

    @Test
    fun `unknown paths collapse to bounded route labels`() {
        assertTrue(ObservabilityMetrics.routeTemplate("/v1/families/me/devices/private-user-input")
            .contains("{id}/other"))
        assertTrue(ObservabilityMetrics.routeTemplate("/unbounded/opaque/path") == "/other")
    }

    @Test
    fun `errors are assigned to fixed safe categories`() {
        assertEquals("AUTH", classifyError(ApiError.unauthorized(), "/v1/auth/login"))
        assertEquals("SECURITY", classifyError(ApiError.rateLimited(), "/v1/pairing/exchange"))
        assertEquals("SOS", classifyError(IllegalStateException("opaque payload"), "/v1/families/me/sos/active"))
        assertEquals("DATABASE", classifyError(java.sql.SQLException("private sql")))
    }

    @Test
    fun `metrics include aggregate latencies and do not expose raw URI`() {
        ObservabilityMetrics.observeHttp("GET", "/v1/families/me/devices/opaque-id/location/history?lat=51.5", 200, 12_000_000)
        val text = ObservabilityMetrics.prometheus(null)
        assertTrue(text.contains("guardianlink_http_requests_total"))
        assertTrue(text.contains("guardianlink_http_duration_ms_recent_quantile"))
        assertTrue(text.contains("guardianlink_db_statement_total"))
        assertFalse(text.contains("opaque-id"))
        assertFalse(text.contains("51.5"))
    }
}
