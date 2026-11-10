package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthTest : IntegrationBase() {
    @BeforeEach
    fun reset() = truncate()

    @Test
    fun `valid login returns tokens and me works`() = withApp {
        val reg = register("ali@test.ir")
        assertTrue(reg.accessToken.isNotBlank())
        assertTrue(reg.refreshToken.isNotBlank())
        val login = login("ali@test.ir", "guardian-test-password")
        assertEquals(HttpStatusCode.OK, login.status)
        val t = login.json<TokenResp>()
        val me: UserOut = get("/v1/me", t.accessToken).json()
        assertEquals("ali@test.ir", me.email)
    }

    @Test
    fun `invalid login rejected with generic error`() = withApp {
        register("ali@test.ir")
        val r = login("ali@test.ir", "wrong-guardian-password")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertEquals(ErrCode.UNAUTHORIZED, errorCode(r))
        val r2 = login("nosuch@test.ir", "guardian-test-password")
        assertEquals(HttpStatusCode.Unauthorized, r2.status)
    }

    @Test
    fun `expired access token yields SESSION_EXPIRED`() = withApp(mapOf("app.accessTokenTtlSec" to "1")) {
        val t = register("ali@test.ir")
        Thread.sleep(1300)
        val r = get("/v1/me", t.accessToken)
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertEquals(ErrCode.SESSION_EXPIRED, errorCode(r))
    }

    @Test
    fun `refresh rotates and old refresh is revoked`() = withApp {
        val t = register("ali@test.ir")
        val r1: TokenResp = post("/v1/auth/refresh", RefreshReq(t.refreshToken)).json()
        assertNotEquals(t.refreshToken, r1.refreshToken)
        // replay توکن refresh قبلی ⇒ SESSION_REVOKED
        val r2 = post("/v1/auth/refresh", RefreshReq(t.refreshToken))
        assertEquals(HttpStatusCode.Unauthorized, r2.status)
        assertEquals(ErrCode.SESSION_REVOKED, errorCode(r2))
        // توکن جدید کار می‌کند
        val me: UserOut = get("/v1/me", r1.accessToken).json()
        assertEquals("ali@test.ir", me.email)
    }

    @Test
    fun `validation errors on bad input`() = withApp {
        val r = post("/v1/auth/register", RegisterReq("not-an-email", "x"))
        assertEquals(HttpStatusCode.BadRequest, r.status)
        val r2 = post("/v1/auth/register", RegisterReq("a@test.ir", "short"))
        assertEquals(HttpStatusCode.BadRequest, r2.status)
    }

    @Test
    fun `logout revokes session immediately`() = withApp {
        val t = register("ali@test.ir")
        val lo = post("/v1/auth/logout", LogoutReq(t.refreshToken))
        assertEquals(HttpStatusCode.NoContent, lo.status)
        val r = post("/v1/auth/refresh", RefreshReq(t.refreshToken))
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `family create and duplicate guard`() = withApp {
        val t = register("ali@test.ir")
        val f = createFamily(t.accessToken)
        assertTrue(f.id.toString().isNotBlank())
        val dup = post("/v1/families", CreateFamilyReq("Second"), t.accessToken)
        assertEquals(HttpStatusCode.Conflict, dup.status)
    }
}
