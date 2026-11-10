package ir.guardianlink.server

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RequestGuardRegressionTest : IntegrationBase() {
    @BeforeEach fun reset() = truncate()

    @Test
    fun `normal JSON body passes ingress guard and oversized body is 413`() = withApp {
        val normal = login("missing-user@test.ir", "guardian-test-password")
        assertEquals(HttpStatusCode.Unauthorized, normal.status)
        val oversizedJson = "{\"email\":\"missing-user@test.ir\",\"password\":\"${"x".repeat(1_048_600)}\"}"
        val oversized = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(oversizedJson)
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, oversized.status)
        assertEquals("PAYLOAD_TOO_LARGE", errorCode(oversized))
    }
}
