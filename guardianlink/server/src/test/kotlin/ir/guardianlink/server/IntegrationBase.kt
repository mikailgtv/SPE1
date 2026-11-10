package ir.guardianlink.server

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Tag
import org.testcontainers.containers.PostgreSQLContainer
import java.nio.file.Files
import java.sql.DriverManager
import java.util.UUID

@Tag("integration")
abstract class IntegrationBase {
    companion object {
        private val externalJdbcUrl = System.getenv("GL_TEST_DB_URL").orEmpty()
        private val externalDbUser = System.getenv("GL_TEST_DB_USER").orEmpty()
        private val externalDbPass = System.getenv("GL_TEST_DB_PASS").orEmpty()
        private val pg: PostgreSQLContainer<*>? = if (externalJdbcUrl.isBlank()) {
            PostgreSQLContainer("postgres:17-alpine").apply { start() }
        } else null
        private val jdbcUrl = externalJdbcUrl.ifBlank { pg!!.jdbcUrl }
        private val dbUser = externalDbUser.ifBlank { pg!!.username }
        private val dbPass = externalDbPass.ifBlank { pg!!.password }
        val dataDir: String = Files.createTempDirectory("gl-test").toString()

        init {
            org.flywaydb.core.Flyway.configure()
                .dataSource(jdbcUrl, dbUser, dbPass)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            Runtime.getRuntime().addShutdownHook(Thread {
                pg?.stop()
                runCatching { Files.walk(java.nio.file.Path.of(dataDir)).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { path -> Files.deleteIfExists(path) } } }
            })
        }

        fun sql(stmt: String) {
            DriverManager.getConnection(jdbcUrl, dbUser, dbPass).use { c ->
                c.createStatement().execute(stmt)
            }
        }

        fun scalarInt(stmt: String): Int = DriverManager.getConnection(jdbcUrl, dbUser, dbPass).use { c ->
            c.createStatement().executeQuery(stmt).use { rs -> check(rs.next()); rs.getInt(1) }
        }

        fun scalarUuid(stmt: String): UUID = DriverManager.getConnection(jdbcUrl, dbUser, dbPass).use { c ->
            c.createStatement().executeQuery(stmt).use { rs -> check(rs.next()); rs.getObject(1, UUID::class.java) }
        }

        fun truncate() {
            DriverManager.getConnection(jdbcUrl, dbUser, dbPass).use { c ->
                c.createStatement().execute(
                    "TRUNCATE users, families, family_members, devices, pairing_sessions, user_sessions, device_sessions, device_status, device_events, family_settings CASCADE"
                )
            }
        }
    }

    fun appConfig(extra: Map<String, String> = emptyMap()): MapApplicationConfig =
        MapApplicationConfig(
            listOf(
                "app.dbUrl" to jdbcUrl,
                "app.dbUser" to dbUser,
                "app.dbPass" to dbPass,
                "app.dataDir" to dataDir,
                "app.accessTokenTtlSec" to "900",
                "app.refreshTokenTtlSec" to "3600",
                "app.pairingTtlSec" to "300",
                "app.exchangeTtlSec" to "120",
                "app.presenceLeaseSec" to "1200",
                "app.defaultBatteryLowPct" to "30",
                "app.defaultBatteryCriticalPct" to "15",
                "app.defaultSyncDelayedSec" to "1800",
                "app.defaultOfflineSec" to "3600",
                "app.defaultHeartbeatIntervalSec" to "900",
                "app.defaultLocationStaleSec" to "3600",
                "app.geofenceCooldownSec" to "300",
                "app.geofenceMinAccuracyM" to "500",
                "app.geofenceMaxPerDevice" to "50",
                "app.geofenceMinRadiusM" to "50",
                "app.geofenceMaxRadiusM" to "5000",
                "app.geofenceEventRetentionDays" to "30",
            ) + extra.toList()
        )

    fun withApp(extraConfig: Map<String, String> = emptyMap(), block: suspend TestScope.() -> Unit) {
        testApplication {
            environment { config = appConfig(extraConfig) }
            application { module() }
            block(TestScope(this))
        }
    }

    class TestScope(val b: ApplicationTestBuilder) {
        val client: HttpClient by lazy {
            b.createClient {
                install(ClientContentNegotiation) {
                    jackson {
                        registerModule(kotlinModule())
                        registerModule(JavaTimeModule())
                        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
                        configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
            }
        }

        private fun HttpRequestBuilder.bearer(token: String) = header(HttpHeaders.Authorization, "Bearer $token")
        private fun HttpRequestBuilder.json(body: Any?) {
            contentType(ContentType.Application.Json)
            if (body != null) setBody(body)
        }

        suspend fun post(url: String, body: Any? = null, token: String? = null): HttpResponse =
            client.post(url) {
                json(body)
                if (token != null) bearer(token)
            }

        suspend fun get(url: String, token: String? = null): HttpResponse =
            client.get(url) { if (token != null) bearer(token) }

        suspend fun delete(url: String, token: String? = null, body: Any? = null): HttpResponse =
            client.delete(url) {
                if (token != null) bearer(token)
                if (body != null) json(body)
            }

        suspend fun put(url: String, body: Any? = null, token: String? = null): HttpResponse =
            client.put(url) {
                json(body)
                if (token != null) bearer(token)
            }

        suspend inline fun <reified T> HttpResponse.json(): T = body()

        suspend fun errorCode(r: HttpResponse): String {
            val text = r.bodyAsText()
            return Regex("\"code\"\\s*:\\s*\"([A-Z_]+)\"").find(text)?.groupValues?.get(1) ?: "?"
        }

        // ---------- flow helpers ----------
        suspend fun register(email: String, password: String = "guardian-test-password"): TokenResp =
            post("/v1/auth/register", RegisterReq(email, password)).json()

        suspend fun login(email: String, password: String): HttpResponse =
            post("/v1/auth/login", LoginReq(email, password))

        suspend fun createFamily(token: String, name: String = "Test Family"): FamilyOut =
            post("/v1/families", CreateFamilyReq(name), token).json()

        suspend fun createPairing(token: String): PairingCreateOut =
            post("/v1/pairing/sessions", null, token).json()

        suspend fun exchange(code: String, installId: String = UUID.randomUUID().toString()): HttpResponse =
            post(
                "/v1/pairing/exchange",
                ExchangeReq(code, installId, "Test Device", "Google", "Pixel 7", "16", "0.1.0"),
            )

        suspend fun confirm(exchangeToken: String, installId: String): ConfirmOut =
            post(
                "/v1/pairing/confirm",
                ConfirmReq(exchangeToken, installId, "Test Device", "Google", "Pixel 7", "16", "0.1.0"),
            ).json()

        suspend fun deviceAuth(deviceId: UUID, secret: String): TokenResp =
            post("/v1/device/auth", DeviceAuthReq(deviceId, secret)).json()

        /** جریان کامل: register+family+pairing+device auth. */
        suspend fun fullPairing(email: String = "p${UUID.randomUUID().toString().take(8)}@test.ir"): Paired {
            val tokens = register(email)
            val at = tokens.accessToken
            createFamily(at)
            val ps = createPairing(at)
            val installId = UUID.randomUUID().toString()
            val ex = exchange(ps.code, installId)
            val exOut = ex.json<ExchangeOut>()
            val cf = confirm(exOut.exchangeToken, installId)
            val dev = deviceAuth(cf.deviceId, cf.deviceSecret)
            return Paired(at, tokens, ps, cf, dev, installId)
        }

        data class Paired(
            val parentAccess: String,
            val parentTokens: TokenResp,
            val pairing: PairingCreateOut,
            val device: ConfirmOut,
            val deviceTokens: TokenResp,
            val installId: String,
        )
    }
}
