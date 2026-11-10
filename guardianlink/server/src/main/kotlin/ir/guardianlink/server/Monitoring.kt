package ir.guardianlink.server

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

object EventType {
    const val DEVICE_CONNECTED = "DEVICE_CONNECTED"
    const val DEVICE_DISCONNECTED = "DEVICE_DISCONNECTED"
    const val BATTERY_LOW = "BATTERY_LOW"
    const val BATTERY_CRITICAL = "BATTERY_CRITICAL"
    const val SYNC_DELAYED = "SYNC_DELAYED"
    const val PERMISSION_CHANGED = "PERMISSION_CHANGED"
    const val APP_UPDATED = "APP_UPDATED"

    // Phase 3 — Screen Time (فقط monitoring)
    const val SCREEN_TIME_LIMIT_NEAR = "SCREEN_TIME_LIMIT_NEAR"
    const val SCREEN_TIME_LIMIT_REACHED = "SCREEN_TIME_LIMIT_REACHED"
    const val USAGE_ACCESS_REVOKED = "USAGE_ACCESS_REVOKED"
    const val SCHEDULE_STARTED = "SCHEDULE_STARTED"
    const val SCHEDULE_ENDED = "SCHEDULE_ENDED"

    // Phase 4 — Location (فقط monitoring)
    const val LOCATION_ENABLED = "LOCATION_ENABLED"
    const val LOCATION_DISABLED = "LOCATION_DISABLED"
    const val LOCATION_PERMISSION_GRANTED = "LOCATION_PERMISSION_GRANTED"
    const val LOCATION_PERMISSION_REVOKED = "LOCATION_PERMISSION_REVOKED"
    const val LOCATION_UPDATE_RECEIVED = "LOCATION_UPDATE_RECEIVED"
    const val LOCATION_SYNC_DELAYED = "LOCATION_SYNC_DELAYED"
    const val LOCATION_UNAVAILABLE = "LOCATION_UNAVAILABLE"

    // Phase 5 — Geofencing (فقط monitoring؛ رویداد فقط روی تغییر state)
    const val GEOFENCE_CREATED = "GEOFENCE_CREATED"
    const val GEOFENCE_UPDATED = "GEOFENCE_UPDATED"
    const val GEOFENCE_DELETED = "GEOFENCE_DELETED"
    const val GEOFENCE_ENTER = "GEOFENCE_ENTER"
    const val GEOFENCE_EXIT = "GEOFENCE_EXIT"
    const val GEOFENCE_ERROR = "GEOFENCE_ERROR"

    // Phase 6 — app management and enforcement telemetry
    const val APP_RULE_CHANGED = "APP_RULE_CHANGED"
    // Phase 7 — web rule/configuration status only; no blocked-visit event is generated.
    const val WEB_PROTECTION_CONFIG_CHANGED = "WEB_PROTECTION_CONFIG_CHANGED"
    const val WEB_PROTECTION_DISABLED = "WEB_PROTECTION_DISABLED"
    const val WEB_RULE_CHANGED = "WEB_RULE_CHANGED"
    const val WEB_FILTERING_UNAVAILABLE = "WEB_FILTERING_UNAVAILABLE"
    const val WEB_FILTERING_ERROR = "WEB_FILTERING_ERROR"
    const val WEB_FILTERING_STATUS_CHANGED = "WEB_FILTERING_STATUS_CHANGED"
    const val APP_RULE_DISABLED = "APP_RULE_DISABLED"
    const val APP_RESTRICTION_ENFORCED = "APP_RESTRICTION_ENFORCED"
    const val APP_RESTRICTION_FAILED = "APP_RESTRICTION_FAILED"
    const val APP_RESTRICTION_UNSUPPORTED = "APP_RESTRICTION_UNSUPPORTED"
    const val APP_USAGE_LIMIT_NEAR = "APP_USAGE_LIMIT_NEAR"
    const val APP_USAGE_LIMIT_CRITICAL = "APP_USAGE_LIMIT_CRITICAL"
    const val APP_USAGE_LIMIT_REACHED = "APP_USAGE_LIMIT_REACHED"
    const val DEVICE_MANAGEMENT_CHANGED = "DEVICE_MANAGEMENT_CHANGED"

    // Phase 9 — SOS event system
    const val SOS_ACTIVATED = "SOS_ACTIVATED"
    const val SOS_ACTIVATION_FAILED = "SOS_ACTIVATION_FAILED"
    const val SOS_LOCATION_UPDATED = "SOS_LOCATION_UPDATED"
    const val SOS_LOCATION_UNAVAILABLE = "SOS_LOCATION_UNAVAILABLE"
    const val SOS_NETWORK_LOST = "SOS_NETWORK_LOST"
    const val SOS_NETWORK_RESTORED = "SOS_NETWORK_RESTORED"
    const val SOS_ACKNOWLEDGED = "SOS_ACKNOWLEDGED"
    const val SOS_CANCELLED = "SOS_CANCELLED"
    const val SOS_RESOLVED = "SOS_RESOLVED"
    const val SOS_TRACKING_STARTED = "SOS_TRACKING_STARTED"
    const val SOS_TRACKING_STOPPED = "SOS_TRACKING_STOPPED"
    const val SOS_BATTERY_CRITICAL = "SOS_BATTERY_CRITICAL"
    const val SOS_ALREADY_ACTIVE = "SOS_ALREADY_ACTIVE"
    const val SOS_EXPIRED = "SOS_EXPIRED"
    const val SOS_NOTE_ADDED = "SOS_NOTE_ADDED"
}

object BatteryBand {
    const val NORMAL = "normal"
    const val LOW = "low"
    const val CRITICAL = "critical"
    const val UNKNOWN = "unknown"

    fun of(level: Int?, lowPct: Int, criticalPct: Int): String = when {
        level == null -> UNKNOWN
        level < criticalPct -> CRITICAL
        level <= lowPct -> LOW
        else -> NORMAL
    }
}

object Health {
    const val HEALTHY = "healthy"
    const val DELAYED = "delayed"
    const val OFFLINE = "offline"
    const val UNKNOWN = "unknown"

    fun of(lastHeartbeat: OffsetDateTime?, syncDelayedSec: Long, offlineSec: Long): String {
        val lh = lastHeartbeat ?: return UNKNOWN
        val age = ChronoUnit.SECONDS.between(lh, OffsetDateTime.now())
        return when {
            age <= syncDelayedSec -> HEALTHY
            age <= offlineSec -> DELAYED
            else -> OFFLINE
        }
    }
}

/** اعتبارسنجی payloadهای child — هیچ داده‌ای blind trusted نیست. */
object PayloadGuard {
    private val CONN_STATES = setOf("wifi", "cellular", "ethernet", "none", "unknown")
    private val PERM_STATES = setOf(
        "granted", "denied", "not_requested", "not_configured", "not_granted",
        "optimized", "unrestricted", "restricted", "allowed", "unknown",
    )
    private val KEY_RX = Regex("^[a-z_]{1,40}$")

    fun battery(v: Int?): Int? {
        if (v == null) return null
        if (v < 0 || v > 100) throw ApiError.validation("battery out of range")
        return v
    }

    fun connection(v: String?): String {
        val s = v?.lowercase() ?: "unknown"
        if (s !in CONN_STATES) throw ApiError.validation("bad connection_state")
        return s
    }

    fun shortText(v: String?, max: Int = 100): String? {
        if (v == null) return null
        val t = v.take(max)
        if (t.any { it.isISOControl() }) throw ApiError.validation("bad text")
        return t
    }

    fun permissions(raw: Map<String, String>?): Map<String, String> {
        if (raw == null) return emptyMap()
        if (raw.size > 32) throw ApiError.validation("too many permission keys")
        val out = LinkedHashMap<String, String>()
        for ((k, v) in raw) {
            if (!KEY_RX.matches(k)) throw ApiError.validation("bad permission key")
            val state = v.lowercase()
            if (state !in PERM_STATES) throw ApiError.validation("bad permission state")
            out[k] = state
        }
        return out
    }
}

object Json {
    private val mapper = com.fasterxml.jackson.databind.ObjectMapper()
    fun write(v: Any?): String = mapper.writeValueAsString(v ?: emptyMap<String, String>())
    fun readMap(s: String?): Map<String, String> =
        try {
            if (s.isNullOrBlank()) emptyMap() else mapper.readValue(s)
        } catch (_: Exception) {
            emptyMap()
        }
}

/** سرویس رویداد + سلامت؛ تنها نقطه‌ی تولید device_events. */
class MonitoringService(private val deps: AppDeps) {
    private val statuses = DeviceStatusRepo(deps.db)
    private val events = DeviceEventRepo(deps.db)
    private val settingsRepo = FamilySettingsRepo(deps.db)

    fun settings(familyId: UUID): FamilySettingsRow = settingsRepo.get(familyId, deps.cfg)

    /** کلید dedupe با دقت ثانیه: جلوگیری از انتشار تکراری در race هم‌زمان دو درخواست. */
    private fun secKey(deviceId: UUID, tag: String): String =
        "$deviceId:$tag:${OffsetDateTime.now().toEpochSecond()}"


    fun emitBatteryEvent(deviceId: UUID, newBand: String, oldBand: String, level: Int?) {
        if (newBand == oldBand) return
        val type = when (newBand) {
            BatteryBand.LOW -> EventType.BATTERY_LOW
            BatteryBand.CRITICAL -> EventType.BATTERY_CRITICAL
            else -> null
        } ?: return
        deps.db.tx { c ->
            events.emit(
                c, deviceId, type,
                Json.write(mapOf("battery_level" to (level ?: -1), "band" to newBand, "previous_band" to oldBand)),
                secKey(deviceId, type),
            )
        }
        dlog.info("battery event", "device" to maskDeviceId(deviceId), "type" to type, "level" to level?.toString())
    }

    fun emitPermissionEvents(deviceId: UUID, old: Map<String, String>, new: Map<String, String>) {
        val changed = mutableListOf<Triple<String, String?, String>>()
        for ((k, v) in new) {
            val o = old[k]
            if (o != v) changed.add(Triple(k, o, v))
        }
        for (k in old.keys) if (k !in new) changed.add(Triple(k, old[k], "not_configured"))
        if (changed.isEmpty()) return
        // Phase 3: لغو Usage Access رویداد اختصاصی هم می‌گیرد (علاوه بر PERMISSION_CHANGED)
        val usageRevoked = changed.any { (k, o, v) ->
            k == "usage_access" && o == "granted" && v != "granted"
        }
        deps.db.tx { c ->
            for ((k, o, v) in changed) {
                events.emit(
                    c, deviceId, EventType.PERMISSION_CHANGED,
                    Json.write(mapOf("key" to k, "from" to (o ?: "not_configured"), "to" to v)),
                    secKey(deviceId, "PERM:$k:$v"),
                )
            }
            if (usageRevoked) {
                events.emit(
                    c, deviceId, EventType.USAGE_ACCESS_REVOKED,
                    Json.write(mapOf("key" to "usage_access", "to" to (new["usage_access"] ?: "not_granted"))),
                    secKey(deviceId, "USAGE_REVOKED"),
                )
            }
        }
        dlog.info("permission events", "device" to maskDeviceId(deviceId), "count" to changed.size.toString())
    }

    fun emitAppUpdated(deviceId: UUID, from: String, to: String) {
        deps.db.tx { c ->
            events.emit(
                c, deviceId, EventType.APP_UPDATED,
                Json.write(mapOf("from" to from, "to" to to)),
                secKey(deviceId, "APP_UPDATED:$to"),
            )
        }
    }

    /**
     * محاسبه‌ی health سمت سرور + رویدادهای lazy (SYNC_DELAYED / DEVICE_DISCONNECTED).
     * در هر خواندن صدا زده می‌شود تا بدون سرویس پس‌زمینه‌ی اضافه، حالت‌ها به‌روز بمانند.
     */
    fun refreshHealth(deviceId: UUID): DeviceStatusRow? {
        val st = statuses.byDevice(deviceId)
        val dev = deps.devices.byId(deviceId) ?: return null
        val set = settingsRepo.get(dev.familyId, deps.cfg)
        val computed = Health.of(st?.lastHeartbeat, set.syncDelayedSec.toLong(), set.offlineSec.toLong())
        val old = st?.healthState ?: Health.UNKNOWN
        if (st == null) return null
        if (computed != old) {
            deps.db.tx { c ->
                statuses.setHealth(c, deviceId, computed)
                if (computed == Health.OFFLINE && old != Health.UNKNOWN) {
                    events.emit(
                        c, deviceId, EventType.DEVICE_DISCONNECTED,
                        Json.write(mapOf("previous" to old)),
                        secKey(deviceId, EventType.DEVICE_DISCONNECTED),
                    )
                }
                if (computed == Health.DELAYED && old == Health.HEALTHY) {
                    events.emit(
                        c, deviceId, EventType.SYNC_DELAYED,
                        Json.write(mapOf("previous" to old)),
                        secKey(deviceId, EventType.SYNC_DELAYED),
                    )
                }
            }
        }
        return st.copy(healthState = computed)
    }

    /** پس از heartbeat موفق: اگر قبلاً offline بوده، رویداد اتصال. */
    fun onHeartbeatConnected(deviceId: UUID, previousHealth: String) {
        if (previousHealth != Health.OFFLINE && previousHealth != Health.UNKNOWN) return
        deps.db.tx { c ->
            events.emit(
                c, deviceId, EventType.DEVICE_CONNECTED,
                Json.write(mapOf("previous" to previousHealth)),
                secKey(deviceId, EventType.DEVICE_CONNECTED),
            )
        }
    }
}
