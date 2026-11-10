package ir.guardianlink.server

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Phase 4 — Location (فقط monitoring؛ بدون Geofence).
 *
 * اصول:
 * - هویت دستگاه فقط از توکن؛ مختصات self-report با validation سخت (بازه، دقت، زمان).
 * - حریم خصوصی: lat/lon فقط وقتی sharing_enabled=true ذخیره می‌شود؛ در غیر این صورت
 *   نقاط بی‌صدا دور ریخته می‌شوند (stored=0) و فقط «وضعیت» نگه داشته می‌شود.
 * - Retention قابل تنظیم (۰ = فقط آخرین موقعیت) و purge تنبل در هر sync/خواندن.
 * - رویدادها فقط روی گذار حالت (نه در هر sync) با dedupe.
 */
class LocationService(private val deps: AppDeps) {
    private val repo = LocationRepo(deps.db)
    private val events = DeviceEventRepo(deps.db)

    companion object {
        const val MAX_POINTS = 500
        const val MAX_HISTORY_LIMIT = 1000
        const val MAX_HISTORY_RANGE_DAYS = 31
        private val SOURCES = setOf("fused", "gps", "network", "last_known", "passive")
        private val PERM_STATES = setOf("granted", "background_granted", "denied", "unknown")
        private val PROVIDER_STATES = setOf("enabled", "disabled", "unknown")
        private const val FIX_WINDOW_HOURS = 48L
        private const val CLOCK_SKEW_MIN = 5L
    }

    private fun staleSec(): Long = deps.cfg.defaultLocationStaleSec.toLong()

    // ================= Child: report + batch =================
    fun sync(deviceId: UUID, req: LocationSyncIn): Int {
        val perm = norm(req.permissionStatus, PERM_STATES, "permission_status")
        val provider = norm(req.providerStatus, PROVIDER_STATES, "provider_status")
        val points = req.points ?: emptyList()
        if (points.size > MAX_POINTS) throw ApiError.validation("too many points")
        val now = OffsetDateTime.now()
        val minTs = now.minusHours(FIX_WINDOW_HOURS)
        val maxTs = now.plusMinutes(CLOCK_SKEW_MIN)
        for (p in points) {
            if (p.latitude !in -90.0..90.0) throw ApiError.validation("latitude out of range")
            if (p.longitude !in -180.0..180.0) throw ApiError.validation("longitude out of range")
            val acc = p.accuracyM
            if (acc != null && (acc < -1f || acc > 100_000f)) throw ApiError.validation("accuracy out of range")
            val ts = tsOf(p.clientTs) ?: throw ApiError.validation("bad client_ts")
            if (ts.isBefore(minTs) || ts.isAfter(maxTs)) throw ApiError.validation("client_ts out of window")
            val src = (p.source ?: "fused").lowercase()
            if (src !in SOURCES) throw ApiError.validation("bad source")
        }
        val settings = repo.settings(deviceId) ?: LocationSettingsRow(deviceId, true, 30)
        val prev = repo.status(deviceId)
        val newest = points.maxOfOrNull { it.clientTs }?.let { tsOf(it) }
        val effectiveLastFix = maxOfOdt(prev?.lastFixTs, newest)
        val health = computeHealth(perm, provider, effectiveLastFix, settings.sharingEnabled)

        var stored = 0
        deps.db.tx { c ->
            // خط قرمز حریم خصوصی: مختصات فقط با sharing فعال ذخیره می‌شود
            if (settings.sharingEnabled && points.isNotEmpty()) {
                stored = repo.insertPoints(c, deviceId, points)
            }
            repo.upsertStatus(c, deviceId, perm, provider, newest, health)
        }
        applyRetention(deviceId, settings)
        emitTransitions(deviceId, prev, perm, provider, health, points.isNotEmpty() && settings.sharingEnabled)
        // Phase 5: موتور geofence از همان نقاط معتبرِ sync تغذیه می‌شود. هر retry نیز
        // دوباره evaluate می‌شود (حتی اگر نقطه قبلاً ذخیره شده باشد): گذار stateful/idempotent
        // است و نقاط کهنه با lastTransitionAt کنار گذاشته می‌شوند؛ بنابراین خطای موقت پردازش
        // باعث از دست رفتن رویداد بعد از markSent صف کودک نمی‌شود.
        if (settings.sharingEnabled && points.isNotEmpty()) {
            deps.geofenceService.evaluateFixes(deviceId, points)
        }
        return stored
    }

    private fun norm(v: String?, allowed: Set<String>, field: String): String {
        val s = (v ?: "unknown").lowercase()
        if (s !in allowed) throw ApiError.validation("bad $field")
        return s
    }

    private fun tsOf(ms: Long): OffsetDateTime? =
        runCatching { OffsetDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC) }.getOrNull()

    private fun maxOfOdt(a: OffsetDateTime?, b: OffsetDateTime?): OffsetDateTime? = when {
        a == null -> b
        b == null -> a
        else -> if (a.isAfter(b)) a else b
    }

    private fun computeHealth(perm: String, provider: String, lastFix: OffsetDateTime?, sharing: Boolean): String =
        when {
            !sharing -> "unavailable"
            perm == "denied" || perm == "unknown" -> "unavailable"
            provider == "disabled" -> "unavailable"
            lastFix == null -> "unavailable"
            Duration.between(lastFix, OffsetDateTime.now()).seconds > staleSec() -> "delayed"
            else -> "healthy"
        }

    // ================= رویدادها — فقط گذار =================
    private fun emitTransitions(
        deviceId: UUID,
        prev: LocationStatusRow?,
        newPerm: String,
        newProvider: String,
        newHealth: String,
        hadPoints: Boolean,
    ) {
        val oldPerm = prev?.permissionStatus ?: "unknown"
        val oldProvider = prev?.providerStatus ?: "unknown"
        val oldHealth = prev?.healthState ?: "unavailable"
        val out = mutableListOf<Pair<String, Map<String, String>>>()
        if (oldPerm != newPerm) {
            if ((oldPerm == "denied" || oldPerm == "unknown") && newPerm != "denied" && newPerm != "unknown") {
                out.add(EventType.LOCATION_PERMISSION_GRANTED to mapOf("status" to newPerm))
            }
            if ((oldPerm == "granted" || oldPerm == "background_granted") && newPerm == "denied") {
                out.add(EventType.LOCATION_PERMISSION_REVOKED to mapOf("status" to newPerm))
            }
        }
        if (oldProvider != newProvider) {
            if (newProvider == "enabled" && oldProvider != "unknown") {
                out.add(EventType.LOCATION_ENABLED to mapOf("provider_status" to newProvider))
            }
            if (newProvider == "disabled") {
                out.add(EventType.LOCATION_DISABLED to mapOf("provider_status" to newProvider))
            }
        }
        if (oldHealth != newHealth) {
            when (newHealth) {
                "unavailable" -> out.add(EventType.LOCATION_UNAVAILABLE to mapOf("from" to oldHealth))
                "delayed" -> out.add(EventType.LOCATION_SYNC_DELAYED to mapOf("from" to oldHealth))
                "healthy" ->
                    // LOCATION_UPDATE_RECEIVED فقط هنگام «بازیابی» (ورود fix تازه بعد از وضعیت خراب)،
                    // نه در هر sync — طبق قاعده‌ی dedupe رویدادها
                    if (hadPoints && (oldHealth == "unavailable" || oldHealth == "delayed")) {
                        out.add(EventType.LOCATION_UPDATE_RECEIVED to mapOf("from" to oldHealth))
                    }
            }
        }
        if (out.isEmpty()) return
        deps.db.tx { c ->
            for ((type, meta) in out) {
                events.emit(
                    c, deviceId, type, Json.write(meta),
                    "$deviceId:$type:${OffsetDateTime.now().toEpochSecond()}",
                )
            }
        }
        dlog.info("location events", "device" to maskDeviceId(deviceId), "count" to out.size.toString())
    }

    // ================= Retention =================
    private fun applyRetention(deviceId: UUID, settings: LocationSettingsRow) {
        if (settings.retentionDays <= 0) {
            repo.keepOnlyLatest(deviceId)
        } else {
            repo.purgeOlderThan(deviceId, OffsetDateTime.now().minusDays(settings.retentionDays.toLong()))
        }
    }

    // ================= Parent: reads =================
    fun current(deviceId: UUID): LocationCurrentOut {
        val settings = repo.settings(deviceId) ?: LocationSettingsRow(deviceId, true, 30)
        applyRetention(deviceId, settings)
        val st = refreshHealth(deviceId, settings)
        val latest = if (settings.sharingEnabled) repo.latestFix(deviceId) else null
        val ageSec = latest?.let { Duration.between(it.clientTs, OffsetDateTime.now()).seconds } ?: Long.MAX_VALUE
        return LocationCurrentOut(
            fix = latest?.let {
                LocationFixOut(
                    it.latitude, it.longitude,
                    it.accuracyM.takeIf { a -> a >= 0f },
                    it.clientTs.toString(), it.source,
                    isCurrent = ageSec <= staleSec() && st.healthState == "healthy",
                )
            },
            sharingEnabled = settings.sharingEnabled,
            permissionStatus = st.permissionStatus,
            providerStatus = st.providerStatus,
            health = st.healthState,
            staleAfterSec = deps.cfg.defaultLocationStaleSec,
            retentionDays = settings.retentionDays,
        )
    }

    /** health تنبل: اگر fix کهنه شده باشد بدون گزارش جدید هم healthy→delayed می‌شود. */
    private fun refreshHealth(deviceId: UUID, settings: LocationSettingsRow): LocationStatusRow {
        val st = repo.status(deviceId)
            ?: LocationStatusRow(deviceId, "unknown", "unknown", null, null, "unavailable")
        val fresh = computeHealth(st.permissionStatus, st.providerStatus, st.lastFixTs, settings.sharingEnabled)
        if (fresh == st.healthState) return st
        val prev = st
        deps.db.tx { c -> repo.upsertStatus(c, deviceId, st.permissionStatus, st.providerStatus, null, fresh, reportNow = false) }
        emitTransitions(deviceId, prev, st.permissionStatus, st.providerStatus, fresh, hadPoints = false)
        return st.copy(healthState = fresh)
    }

    fun history(deviceId: UUID, from: OffsetDateTime?, to: OffsetDateTime?, limit: Int): LocationHistoryOut {
        if (from != null && to != null) {
            val range = Duration.between(from, to)
            if (range.isNegative || range.toDays() > MAX_HISTORY_RANGE_DAYS) {
                throw ApiError.validation("location history range must be between zero and $MAX_HISTORY_RANGE_DAYS days")
            }
        }
        val settings = repo.settings(deviceId) ?: LocationSettingsRow(deviceId, true, 30)
        applyRetention(deviceId, settings)
        if (!settings.sharingEnabled) {
            return LocationHistoryOut(emptyList(), settings.retentionDays, false)
        }
        val rows = repo.history(deviceId, from, to, limit.coerceIn(1, MAX_HISTORY_LIMIT))
        val now = OffsetDateTime.now()
        return LocationHistoryOut(
            points = rows.map {
                LocationFixOut(
                    it.latitude, it.longitude,
                    it.accuracyM.takeIf { a -> a >= 0f },
                    it.clientTs.toString(), it.source,
                    isCurrent = Duration.between(it.clientTs, now).seconds <= staleSec(),
                )
            },
            retentionDays = settings.retentionDays,
            sharingEnabled = true,
        )
    }

    fun settingsOut(deviceId: UUID): LocationSettingsOut {
        val s = repo.settings(deviceId) ?: deps.db.tx { c -> repo.upsertSettings(c, deviceId, true, 30) }
        return LocationSettingsOut(s.deviceId, s.sharingEnabled, s.retentionDays)
    }

    fun saveSettings(deviceId: UUID, req: LocationSettingsReq): LocationSettingsOut {
        val cur = repo.settings(deviceId) ?: LocationSettingsRow(deviceId, true, 30)
        val sharing = req.sharingEnabled ?: cur.sharingEnabled
        val retention = req.retentionDays ?: cur.retentionDays
        if (retention != 0 && retention !in 1..365) throw ApiError.validation("retention_days must be 0 (disabled) or 1..365")
        val prevStatus = repo.status(deviceId)
        val row = deps.db.tx { c -> repo.upsertSettings(c, deviceId, sharing, retention) }
        if (sharing != cur.sharingEnabled) {
            // OFF/ON makes any prior geofence inside/outside observation stale; reset to UNKNOWN.
            deps.geofenceService.resetStates(deviceId)
        }
        applyRetention(deviceId, row)
        // خاموش/روشن شدن sharing ⇒ رویداد LOCATION_DISABLED/ENABLED نیست (مخصوص provider است)؛
        // ولی health ممکن است تغییر کند
        if (prevStatus != null) {
            val fresh = computeHealth(prevStatus.permissionStatus, prevStatus.providerStatus, prevStatus.lastFixTs, sharing)
            if (fresh != prevStatus.healthState) {
                deps.db.tx { c ->
                    repo.upsertStatus(c, deviceId, prevStatus.permissionStatus, prevStatus.providerStatus, null, fresh, reportNow = false)
                }
                emitTransitions(deviceId, prevStatus, prevStatus.permissionStatus, prevStatus.providerStatus, fresh, hadPoints = false)
            }
        }
        dlog.info("location settings saved", "device" to maskDeviceId(deviceId), "sharing" to sharing.toString(), "retention" to retention.toString())
        return LocationSettingsOut(row.deviceId, row.sharingEnabled, row.retentionDays)
    }

    /** حذف واقعی از backend: today | last7 | all. */
    fun deleteHistory(deviceId: UUID, scope: String): Int {
        val deleted = deps.db.tx { c ->
            when (scope) {
                "today" -> repo.deleteRange(c, deviceId, LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime())
                "last7" -> repo.deleteRange(c, deviceId, OffsetDateTime.now().minusDays(7))
                "all" -> repo.deleteRange(c, deviceId, null)
                else -> throw ApiError.validation("scope must be today|last7|all")
            }
        }
        dlog.info("location history deleted", "device" to maskDeviceId(deviceId), "scope" to scope, "rows" to deleted.toString())
        return deleted
    }
}
