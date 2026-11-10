package ir.guardianlink.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** تست چرخش refresh روی 401 بدون نیاز به دستگاه. */
class RefreshAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var creds: MemCreds
    private lateinit var api: ApiClient

    class MemCreds : CredentialStore {
        @Volatile var c: CredentialStore.Creds? = null
        override fun get() = c
        override fun put(cc: CredentialStore.Creds) { c = cc }
        override fun clear() { c = null }
    }

    @Before
    fun up() {
        server = MockWebServer()
        server.start()
        creds = MemCreds()
        api = ApiClient(server.url("/").toString().removeSuffix("/"), creds)
    }

    @After
    fun down() = server.shutdown()

    @Test
    fun refreshOn401ThenSuccess() = runBlocking {
        creds.put(CredentialStore.Creds("old-access", "old-refresh", "user"))
        // 1) درخواست اصلی 401
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHORIZED","message":"x"}"""))
        // 2) refresh موفق
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":900,"token_type":"Bearer"}""",
            ),
        )
        // 3) retry درخواست اصلی موفق
        server.enqueue(MockResponse().setBody("""{"id":"u1","email":"a@b.ir","created_at":null}"""))
        val me = api.me()
        assertEquals("u1", me.id)
        assertEquals("new-access", creds.get()?.access)
        assertEquals("new-refresh", creds.get()?.refresh)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun refreshFailureClearsCreds() = runBlocking {
        creds.put(CredentialStore.Creds("old-access", "old-refresh", "user"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHORIZED","message":"x"}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"SESSION_REVOKED","message":"x"}"""))
        try {
            api.me()
        } catch (e: ApiException) {
            // انتظار: پس از شکست refresh، credential پاک شده
        }
        assertNull(creds.get())
    }
}
