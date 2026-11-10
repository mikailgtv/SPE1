package ir.guardianlink.server

import net.logstash.logback.marker.Markers
import org.slf4j.LoggerFactory

/** Structured event categories. Never use request values as category names. */
object Cat {
    const val AUTH = "AUTH"
    const val PAIRING = "PAIRING"
    const val DEVICE = "DEVICE"
    const val NETWORK = "NETWORK"
    const val SECURITY = "SECURITY"
    const val APP = "APP"
}

/** Compatibility helpers deliberately never return a token/code fingerprint. */
object Redact {
    fun token(t: String?): String = if (t.isNullOrEmpty()) "<none>" else "<redacted>"
    fun email(e: String?): String = if (e.isNullOrEmpty()) "<none>" else "<redacted>"
    fun code(c: String?): String = if (c.isNullOrEmpty()) "<none>" else "<redacted>"
}

class GlLogger(private val category: String) {
    private val log = LoggerFactory.getLogger("GL")
    private val safeKeys = setOf(
        "requestId", "method", "route", "status", "durationMs", "type", "ttl", "count",
        "level", "health", "next_min", "httpStatus", "errorClass", "errorCategory", "errorFrame",
        "result", "attempt", "state", "operation", "scope", "format",
    )

    private fun write(level: Int, event: String, kv: Array<out Pair<String, String?>>) {
        val fields = linkedMapOf<String, Any>(
            "category" to category.take(24),
            "event" to clean(event, 96),
        )
        for ((key, value) in kv) {
            if (key in safeKeys && value != null) fields[key] = clean(value, 128)
        }
        val marker = Markers.appendEntries(fields)
        val message = fields["event"] as String
        when (level) {
            0 -> log.info(marker, message)
            1 -> log.warn(marker, message)
            else -> log.error(marker, message)
        }
    }

    private fun clean(value: String, max: Int): String = value
        .filterNot { it.isISOControl() }
        .take(max)

    fun info(msg: String, vararg kv: Pair<String, String?>) = write(0, msg, kv)
    fun warn(msg: String, vararg kv: Pair<String, String?>) = write(1, msg, kv)
    fun error(msg: String, vararg kv: Pair<String, String?>) = write(2, msg, kv)
}

fun logger(category: String): GlLogger = GlLogger(category)

val log = logger(Cat.AUTH)
val plog = logger(Cat.PAIRING)
val dlog = logger(Cat.DEVICE)
val slog = logger(Cat.SECURITY)
val alog = logger(Cat.APP)
