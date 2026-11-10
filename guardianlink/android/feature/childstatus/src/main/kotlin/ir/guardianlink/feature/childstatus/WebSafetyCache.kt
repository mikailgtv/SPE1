package ir.guardianlink.feature.childstatus

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.WebSafetyEventIn
import ir.guardianlink.core.network.WebSafetySyncOut
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Encrypted offline web-policy snapshot. Contains rules, capability status and config events only. */
internal object WebSafetyCache {
    private const val KEY = "phase7_web_safety_v1"
    private val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
    private val mutex = Mutex()

    private data class Stored(
        val policy: WebSafetySyncOut? = null,
        val reportedMode: String = "UNKNOWN",
        val reportedStatus: String = "UNKNOWN",
        val reportedReason: String = "none",
        val dnsStatus: String = "UNSUPPORTED",
        val vpnStatus: String = "UNSUPPORTED",
        val timezoneId: String = "UTC",
        val events: List<WebSafetyEventIn> = emptyList(),
    )

    data class Snapshot(
        val policy: WebSafetySyncOut?, val reportedMode: String, val reportedStatus: String,
        val reportedReason: String, val dnsStatus: String, val vpnStatus: String,
        val timezoneId: String, val events: List<WebSafetyEventIn>,
    )

    suspend fun read(): Snapshot = mutex.withLock { readStored().snapshot() }

    suspend fun accept(server: WebSafetySyncOut) = mutex.withLock {
        val current = readStored()
        if (current.policy == null || server.ruleVersion >= current.policy.ruleVersion) write(current.copy(policy = server))
    }

    suspend fun report(mode: String, status: String, reason: String, dns: String = "UNSUPPORTED", vpn: String = "UNSUPPORTED") = mutex.withLock {
        val current = readStored()
        val normalizedReason = reason.takeIf { it in REASONS } ?: "none"
        val newEvents = if (current.reportedStatus != status || current.reportedReason != normalizedReason) {
            val eventType = when (status) {
                "UNSUPPORTED" -> "WEB_FILTERING_UNAVAILABLE"
                "ERROR" -> "WEB_FILTERING_ERROR"
                else -> null
            }
            if (eventType == null) current.events else current.events + WebSafetyEventIn(
                UUID.randomUUID().toString(), eventType, normalizedReason, System.currentTimeMillis(),
            )
        } else current.events
        write(current.copy(
            reportedMode = mode, reportedStatus = status, reportedReason = normalizedReason,
            dnsStatus = dns, vpnStatus = vpn, timezoneId = runCatching { java.time.ZoneId.systemDefault().id }.getOrDefault("UTC"),
            events = newEvents.takeLast(100),
        ))
    }

    suspend fun pendingEvents(): List<WebSafetyEventIn> = mutex.withLock { readStored().events.take(20) }
    suspend fun acknowledge(ids: Set<String>) = mutex.withLock {
        if (ids.isEmpty()) return@withLock
        val current = readStored()
        write(current.copy(events = current.events.filterNot { it.clientEventId in ids }))
    }
    suspend fun clear() = mutex.withLock { GlServices.secure.remove(KEY) }

    private suspend fun readStored(): Stored {
        val raw = GlServices.secure.get(KEY) ?: return Stored()
        return runCatching { gson.fromJson(raw, Stored::class.java) ?: Stored() }.getOrDefault(Stored())
    }
    private suspend fun write(value: Stored) { GlServices.secure.put(KEY, gson.toJson(value)) }
    private fun Stored.snapshot() = Snapshot(policy, reportedMode, reportedStatus, reportedReason, dnsStatus, vpnStatus, timezoneId, events)

    private val REASONS = setOf(
        "none", "disabled", "managed_device_required", "chrome_not_installed", "policy_readback_mismatch",
        "policy_stored_effect_unverified", "category_provider_unavailable", "vpn_not_implemented",
        "dns_provider_not_configured", "android_policy_rejected", "timezone_invalid",
    )
}
