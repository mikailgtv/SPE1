package ir.guardianlink.server

import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.request.httpMethod
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.PipelineResponse
import io.ktor.util.AttributeKey
import java.util.UUID
import java.sql.SQLException
import java.net.UnknownHostException
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

val RequestIdKey = AttributeKey<String>("guardianlink.requestId")
val ErrorCategoryKey = AttributeKey<String>("guardianlink.errorCategory")
val ErrorStatusKey = AttributeKey<Int>("guardianlink.errorStatus")

/** Bounded-cardinality, process-local operational counters. No request/user/device payload is retained. */
object ObservabilityMetrics {
    private const val MAX_SERIES = 192
    private const val WINDOW_SIZE = 512
    private val ERROR_CATEGORIES = setOf("AUTH", "DATABASE", "NETWORK", "VALIDATION", "PERMISSION", "SYNC", "NOTIFICATION", "LOCATION", "GEOFENCE", "SOS", "ANDROID", "WEB", "SECURITY", "INTERNAL")
    private val http = ConcurrentHashMap<HttpKey, Series>()
    private val db = Series()
    private val errors = ConcurrentHashMap<String, AtomicLong>()
    private val droppedSeries = AtomicLong()

    data class HttpKey(val method: String, val route: String, val statusClass: String)
    data class Snapshot(val count: Long, val errors: Long, val rateLimited: Long, val sumMs: Long, val samples: List<Long>)

    private class Series {
        private val count = AtomicLong()
        private val errors = AtomicLong()
        private val rateLimited = AtomicLong()
        private val sumMs = AtomicLong()
        private val ring = LongArray(WINDOW_SIZE)
        private var next = 0
        private var size = 0

        fun add(ms: Long, status: Int = 200, isFailure: Boolean = false) {
            count.incrementAndGet()
            sumMs.addAndGet(ms)
            if (isFailure || status >= 400) errors.incrementAndGet()
            if (status == 429) rateLimited.incrementAndGet()
            synchronized(ring) {
                ring[next] = ms
                next = (next + 1) % WINDOW_SIZE
                if (size < WINDOW_SIZE) size++
            }
        }

        fun snapshot(): Snapshot = synchronized(ring) {
            Snapshot(count.get(), errors.get(), rateLimited.get(), sumMs.get(), (0 until size).map { ring[it] }.sorted())
        }
    }

    fun observeHttp(method: String, path: String, status: Int, elapsedNanos: Long) {
        val key = HttpKey(
            method = method.uppercase().takeIf { it in setOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD") } ?: "OTHER",
            route = routeTemplate(path),
            statusClass = "${(status.coerceIn(100, 599) / 100)}xx",
        )
        val series = http[key] ?: if (http.size < MAX_SERIES) {
            http.putIfAbsent(key, Series()) ?: http[key]!!
        } else {
            droppedSeries.incrementAndGet()
            http.computeIfAbsent(HttpKey("OTHER", "/_overflow", key.statusClass)) { Series() }
        }
        series.add((elapsedNanos / 1_000_000).coerceAtLeast(0), status)
    }

    fun observeDb(elapsedNanos: Long, failed: Boolean) {
        db.add((elapsedNanos / 1_000_000).coerceAtLeast(0), if (failed) 500 else 200, failed)
    }

    fun observeError(category: String) {
        val safe = category.takeIf { it in ERROR_CATEGORIES } ?: "INTERNAL"
        errors.computeIfAbsent(safe) { AtomicLong() }.incrementAndGet()
    }

    fun prometheus(dataSource: javax.sql.DataSource?): String = buildString {
        appendLine("# HELP guardianlink_http_requests_total Completed HTTP requests by bounded route template and status class.")
        appendLine("# TYPE guardianlink_http_requests_total counter")
        appendLine("# HELP guardianlink_http_errors_total HTTP responses with status >= 400.")
        appendLine("# TYPE guardianlink_http_errors_total counter")
        appendLine("# HELP guardianlink_http_rate_limited_total HTTP 429 responses.")
        appendLine("# TYPE guardianlink_http_rate_limited_total counter")
        appendLine("# HELP guardianlink_http_duration_ms_sum Total server-side request duration in milliseconds.")
        appendLine("# TYPE guardianlink_http_duration_ms_sum counter")
        appendLine("# HELP guardianlink_http_duration_ms_count Number of observed requests.")
        appendLine("# TYPE guardianlink_http_duration_ms_count counter")
        appendLine("# HELP guardianlink_http_duration_ms_recent_quantile Recent rolling latency quantiles from up to $WINDOW_SIZE samples per series; not a lifetime histogram.")
        appendLine("# TYPE guardianlink_http_duration_ms_recent_quantile gauge")
        http.entries.sortedWith(compareBy({ it.key.route }, { it.key.method }, { it.key.statusClass })).forEach { (key, series) ->
            val s = series.snapshot()
            val labels = "method=\"${key.method}\",route=\"${key.route}\",status_class=\"${key.statusClass}\""
            appendLine("guardianlink_http_requests_total{$labels} ${s.count}")
            appendLine("guardianlink_http_errors_total{$labels} ${s.errors}")
            appendLine("guardianlink_http_rate_limited_total{$labels} ${s.rateLimited}")
            appendLine("guardianlink_http_duration_ms_sum{$labels} ${s.sumMs}")
            appendLine("guardianlink_http_duration_ms_count{$labels} ${s.count}")
            for (q in listOf(0.50, 0.95, 0.99)) {
                appendLine("guardianlink_http_duration_ms_recent_quantile{${labels},quantile=\"$q\"} ${quantile(s.samples, q)}")
            }
        }
        appendLine("# HELP guardianlink_errors_total Classified failures handled by the API; labels are fixed categories.")
        appendLine("# TYPE guardianlink_errors_total counter")
        errors.toSortedMap().forEach { (category, counter) ->
            appendLine("guardianlink_errors_total{category=\"$category\"} ${counter.get()}")
        }
        val ds = dataSource as? HikariDataSource
        val pool = runCatching { ds?.hikariPoolMXBean }.getOrNull()
        appendLine("# HELP guardianlink_db_pool_metrics_available Whether the Hikari pool management bean is available.")
        appendLine("# TYPE guardianlink_db_pool_metrics_available gauge")
        appendLine("guardianlink_db_pool_metrics_available ${if (pool == null) 0 else 1}")
        appendLine("# HELP guardianlink_db_pool_max Configured maximum PostgreSQL connections in this process pool.")
        appendLine("# TYPE guardianlink_db_pool_max gauge")
        appendLine("guardianlink_db_pool_max ${ds?.maximumPoolSize ?: 0}")
        appendLine("# HELP guardianlink_db_pool_min_idle Configured minimum idle PostgreSQL connections in this process pool.")
        appendLine("# TYPE guardianlink_db_pool_min_idle gauge")
        appendLine("guardianlink_db_pool_min_idle ${ds?.minimumIdle ?: 0}")
        appendLine("# HELP guardianlink_db_pool_active Active PostgreSQL pool connections; zero if pool metrics are unavailable.")
        appendLine("# TYPE guardianlink_db_pool_active gauge")
        appendLine("guardianlink_db_pool_active ${pool?.activeConnections ?: 0}")
        appendLine("# HELP guardianlink_db_pool_idle Idle PostgreSQL pool connections; zero if pool metrics are unavailable.")
        appendLine("# TYPE guardianlink_db_pool_idle gauge")
        appendLine("guardianlink_db_pool_idle ${pool?.idleConnections ?: 0}")
        appendLine("# HELP guardianlink_db_pool_pending Threads waiting for a PostgreSQL pool connection.")
        appendLine("# TYPE guardianlink_db_pool_pending gauge")
        appendLine("guardianlink_db_pool_pending ${pool?.threadsAwaitingConnection ?: 0}")
        val d = db.snapshot()
        appendLine("# HELP guardianlink_db_statement_total SQL statements executed; SQL text and parameters are never exported.")
        appendLine("# TYPE guardianlink_db_statement_total counter")
        appendLine("guardianlink_db_statement_total ${d.count}")
        appendLine("# HELP guardianlink_db_statement_errors_total SQL statement failures.")
        appendLine("# TYPE guardianlink_db_statement_errors_total counter")
        appendLine("guardianlink_db_statement_errors_total ${d.errors}")
        appendLine("# HELP guardianlink_db_statement_duration_ms_recent_quantile Recent rolling SQL execution duration quantiles, without SQL labels.")
        appendLine("# TYPE guardianlink_db_statement_duration_ms_recent_quantile gauge")
        for (q in listOf(0.50, 0.95, 0.99)) appendLine("guardianlink_db_statement_duration_ms_recent_quantile{quantile=\"$q\"} ${quantile(d.samples, q)}")
        appendLine("# HELP guardianlink_observability_series_overflow_total Observations aggregated into bounded overflow series.")
        appendLine("# TYPE guardianlink_observability_series_overflow_total counter")
        appendLine("guardianlink_observability_series_overflow_total ${droppedSeries.get()}")
    }

    private fun quantile(sorted: List<Long>, q: Double): Long {
        if (sorted.isEmpty()) return 0
        val index = ceil(q * sorted.size).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    /** Keep only route shapes from an explicit allowlist; IDs, package names, queries and arbitrary paths never become labels. */
    internal fun routeTemplate(path: String): String {
        val p = path.split('/').filter(String::isNotEmpty)
        if (p.isEmpty()) return "/"
        if (p[0] == "health") return if (p.size == 1) "/health" else if (p[1] in setOf("live", "ready")) "/health/${p[1]}" else "/health/other"
        if (p[0] != "v1" || p.size < 2) return "/other"
        return when (p[1]) {
            "auth" -> "/v1/auth/${p.getOrNull(2).safeOperation(setOf("register", "login", "refresh", "logout"))}"
            "me" -> "/v1/me"
            "families" -> familyRoute(p)
            "pairing" -> "/v1/pairing/${p.getOrNull(2).safeOperation(setOf("sessions", "exchange", "confirm"))}"
            "device" -> "/v1/device/${p.getOrNull(2).safeOperation(setOf("auth", "refresh", "heartbeat", "status", "sync", "usage", "location", "geofences", "app-management", "web-safety"))}"
            else -> "/v1/other"
        }
    }

    private fun familyRoute(p: List<String>): String {
        if (p.getOrNull(2) != "me") return "/v1/families/other"
        return when (p.getOrNull(3)) {
            null -> "/v1/families/me"
            "devices" -> {
                if (p.size <= 4) "/v1/families/me/devices"
                else "/v1/families/me/devices/{id}/${p.getOrNull(5).safeOperation(setOf("health", "permissions", "events", "location", "usage", "screen-time", "geofences", "geofence-events", "app-rules", "app-management", "apps", "app-events", "web-safety", "rules", "overrides"))}"
            }
            "notifications" -> "/v1/families/me/notifications/${p.getOrNull(4).safeOperation(setOf("unread-count", "read-all", "preferences"))}"
            "sos" -> "/v1/families/me/sos/${p.getOrNull(4).safeOperation(setOf("active", "history", "preferences", "location", "events", "notes", "acknowledge", "resolve", "cancel", "tracking", "network", "locations"))}"
            else -> "/v1/families/me/other"
        }
    }

    private fun String?.safeOperation(allowed: Set<String>): String = this?.takeIf { it in allowed } ?: "other"
}

/** Classify without retaining the source message or payload. */
fun classifyError(error: Throwable, route: String = ""): String {
    val path = route.lowercase()
    if ("/sos" in path) return "SOS"
    if ("location" in path) return "LOCATION"
    if ("geofence" in path) return "GEOFENCE"
    if ("notification" in path) return "NOTIFICATION"
    if ("/web" in path) return "WEB"
    if ("/sync" in path) return "SYNC"
    return when (error) {
        is ApiError -> when (error.status.value) {
            400, 404, 409, 410 -> "VALIDATION"
            401 -> "AUTH"
            403 -> "PERMISSION"
            429 -> "SECURITY"
            else -> "INTERNAL"
        }
        is SQLException -> "DATABASE"
        is UnknownHostException, is SocketException -> "NETWORK"
        else -> "INTERNAL"
    }
}

fun <T> measureDbStatement(block: () -> T): T {
    val started = System.nanoTime()
    var failed = false
    try {
        return block()
    } catch (t: Throwable) {
        failed = true
        throw t
    } finally {
        ObservabilityMetrics.observeDb(System.nanoTime() - started, failed)
    }
}

/** Request IDs are generated by the server; client-controlled identifiers are not trusted or logged. */
fun Application.installRequestObservability() {
    intercept(ApplicationCallPipeline.Setup) {
        val call: ApplicationCall = this.call
        val requestId = UUID.randomUUID().toString()
        call.attributes.put(RequestIdKey, requestId)
        call.response.headers.append("X-Request-ID", requestId)
        val started = System.nanoTime()
        var logged = false
        fun recordResponse() {
            if (logged) return
            logged = true
            val status = call.attributes.getOrNull(ErrorStatusKey) ?: call.response.status()?.value ?: 500
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val route = ObservabilityMetrics.routeTemplate(path)
            val elapsed = (System.nanoTime() - started).coerceAtLeast(0)
            val durationMs = (elapsed / 1_000_000).toString()
            ObservabilityMetrics.observeHttp(method, path, status, elapsed)
            val log = logger(if (status >= 500) Cat.APP else Cat.NETWORK)
            val fields = arrayOf(
                "requestId" to requestId,
                "method" to method,
                "route" to route,
                "status" to status.toString(),
                "durationMs" to durationMs,
                "errorCategory" to call.attributes.getOrNull(ErrorCategoryKey),
            )
            when {
                status >= 500 -> log.error("http request completed", *fields)
                status >= 400 -> log.warn("http request completed", *fields)
                else -> log.info("http request completed", *fields)
            }
        }
        (call.response as? PipelineResponse)?.pipeline?.intercept(ApplicationSendPipeline.Engine) {
            recordResponse()
            proceed()
        }
        proceed()
    }
}
