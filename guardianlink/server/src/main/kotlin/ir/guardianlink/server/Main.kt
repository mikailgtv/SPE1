package ir.guardianlink.server

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.netty.EngineMain
import io.ktor.server.request.path
import io.ktor.http.ContentType
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

val AppDepsKey = AttributeKey<AppDeps>("AppDeps")

fun Application.module() {
    val cfg = AppConfig.from(environment.config)
    val db = Db(cfg)
    environment.monitor.subscribe(ApplicationStopped) {
        runCatching { db.close() }
            .onFailure { logger(Cat.APP).warn("database pool close failed", "type" to it.javaClass.simpleName) }
    }
    try {
        db.migrate()
    } catch (e: Exception) {
        runCatching { db.close() }
        throw e
    }
    val deps = AppDeps.build(cfg, db)
    attributes.put(AppDepsKey, deps)

    install(ContentNegotiation) {
        jackson {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    install(StatusPages) {
        exception<PayloadTooLargeException> { call, _ ->
            call.attributes.put(ErrorStatusKey, HttpStatusCode.PayloadTooLarge.value)
            call.attributes.put(ErrorCategoryKey, "VALIDATION")
            ObservabilityMetrics.observeError("VALIDATION")
            call.respond(HttpStatusCode.PayloadTooLarge, ErrorBody("PAYLOAD_TOO_LARGE", "request body too large"))
        }
        exception<ApiError> { call, cause ->
            val category = classifyError(cause, call.request.path())
            call.attributes.put(ErrorStatusKey, cause.status.value)
            call.attributes.put(ErrorCategoryKey, category)
            ObservabilityMetrics.observeError(category)
            call.respond(cause.status, ErrorBody(cause.code, cause.message ?: cause.code))
        }
        exception<BadRequestException> { call, cause ->
            var current: Throwable? = cause
            var oversized = false
            var depth = 0
            while (current != null && depth++ < 32) {
                if (current is PayloadTooLargeException) {
                    oversized = true
                    break
                }
                current = current.cause
            }
            val status = if (oversized) HttpStatusCode.PayloadTooLarge else HttpStatusCode.BadRequest
            val code = if (oversized) "PAYLOAD_TOO_LARGE" else ErrCode.VALIDATION
            val message = if (oversized) "request body too large" else "invalid request body"
            val category = "VALIDATION"
            call.attributes.put(ErrorStatusKey, status.value)
            call.attributes.put(ErrorCategoryKey, category)
            ObservabilityMetrics.observeError(category)
            call.respond(status, ErrorBody(code, message))
        }
        exception<Throwable> { call, cause ->
            var current: Throwable? = cause
            var cancelled = false
            var depth = 0
            while (current != null && depth++ < 32) {
                if (current is CancellationException) {
                    cancelled = true
                    break
                }
                current = current.cause
            }
            if (cancelled) {
                call.attributes.put(ErrorCategoryKey, "NETWORK")
                logger(Cat.NETWORK).warn(
                    "request cancelled",
                    "requestId" to call.attributes.getOrNull(RequestIdKey),
                    "errorClass" to cause.javaClass.simpleName,
                )
                throw cause
            }
            val category = classifyError(cause, call.request.path())
            call.attributes.put(ErrorStatusKey, HttpStatusCode.InternalServerError.value)
            call.attributes.put(ErrorCategoryKey, category)
            ObservabilityMetrics.observeError(category)
            logger(Cat.APP).error(
                "unhandled request error",
                "requestId" to call.attributes.getOrNull(RequestIdKey),
                "errorClass" to cause.javaClass.simpleName,
                "errorFrame" to cause.stackTrace.firstOrNull()?.let { "${it.className}.${it.methodName}:${it.lineNumber}" },
                "errorCategory" to category,
            )
            call.respond(HttpStatusCode.InternalServerError, ErrorBody(ErrCode.SERVER_ERROR, "server error"))
        }
    }

    installRequestObservability()

    routing {
        get("health/live") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "live"))
        }
        get("health/ready") {
            call.respondReadiness(db, cfg)
        }
        // Internal-only by deployment contract: Caddy does not proxy /metrics; never publish this port directly.
        get("metrics") {
            call.respondText(ObservabilityMetrics.prometheus(db.ds), ContentType.parse("text/plain; version=0.0.4; charset=utf-8"))
        }
        // Backward-compatible alias; orchestrators should use /health/live and /health/ready.
        get("health") {
            call.respondReadiness(db, cfg)
        }
        route("v1") {
            // JSON ingress guard. Larger bounded limits are reserved for known batch endpoints.
            install(RequestBodyLimit) {
                bodyLimit { call ->
                    when (call.request.path()) {
                        "/v1/device/sync", "/v1/device/usage/upload" -> 12L * 1024 * 1024
                        "/v1/device/app-management/sync" -> 4L * 1024 * 1024
                        else -> 1024L * 1024
                    }
                }
            }
            userRoutes(deps)
            pairingRoutes(deps)
            deviceRoutes(deps)
            monitoringRoutes(deps)
            usageRoutes(deps)
            locationRoutes(deps)
            geofenceRoutes(deps)
            appManagementRoutes(deps)
            webSafetyRoutes(deps)
            notificationRoutes(deps)
            sosRoutes(deps)
        }
    }
}

fun main(args: Array<String>): Unit = EngineMain.main(args)

private suspend fun ApplicationCall.respondReadiness(db: Db, cfg: AppConfig) {
    val databaseHealthy = runCatching { db.one("SELECT 1 AS ok") { it.getInt("ok") } == 1 }.getOrDefault(false)
    val storageReady = runCatching {
        val dir = Path.of(cfg.dataDir)
        Files.isDirectory(dir) && Files.isReadable(dir) && Files.isWritable(dir)
    }.getOrDefault(false)
    val ready = databaseHealthy && storageReady
    val checks = mapOf(
        "database" to if (databaseHealthy) "healthy" else "unavailable",
        "storage" to if (storageReady) "healthy" else "unavailable",
        // Server-side push is not implemented; this is disclosed rather than simulated.
        "push_notifications" to "not_configured",
    )
    val status = if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
    respond(status, mapOf("status" to if (ready) "ready" else "not_ready", "checks" to checks))
}
