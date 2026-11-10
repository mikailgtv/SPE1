package ir.guardianlink.server

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase 5 — موتور Geofence سمت سرور.
 *
 * چرا سمت سرور (و نه GeofencingClient)؟ طبق دستور صریح کاربر Play Services دور زده شد:
 * - کودک هیچ listener/FGS/receiver جدیدی ندارد ⇒ صفر مصرف اضافه، صفر notification.
 * - روی دستگاه‌های بدون GMS هم کار می‌کند.
 * - «منبع حقیقت» همان fixهای Phase 4 است ⇒ یک pipeline، بدون duplicate source.
 *
 * مقاومت در برابر خطای GPS (بندهای ۱۰/۱۱/۱۲):
 * - حالت اولیه و پس از تغییر هندسه: UNKNOWN — بدون fix معتبر هرگز inside/outside اعلام نمی‌شود.
 * - Hysteresis هندسی: INSIDE فقط وقتی dist+accuracy ≤ radius؛ OUTSIDE فقط وقتی dist−accuracy ≥ radius؛
 *   نوار مرزی مبهم ⇒ حفظ حالت قبلی (بدون تلنگر).
 * - دقت خیلی ضعیف (> [AppConfig.geofenceMinAccuracyM]) ⇒ «Location Uncertain»: فقط lastKnownAccuracy
 *   به‌روز می‌شود، هیچ گذاری رخ نمی‌دهد.
 * - Cooldown/Debounce قابل تنظیم ([AppConfig.geofenceCooldownSec]): دو گذار پشت‌سرهم زودتر از
 *   این فاصله ثبت نمی‌شوند (پینگ‌پنگ روی مرز ⇒ یک رویداد، نه چهارتا).
 * - رویداد فقط روی گذار واقعی حالت (state machine) + dedupe کلید در device_events.
 */
class GeofenceService(private val deps: AppDeps) {
    private val repo = GeofenceRepo(deps.db)
    private val events = DeviceEventRepo(deps.db)

    companion object {
        /** بند ۳: حداقل/حداکثر منطقی شعاع — پیشگیری از تنظیمات اشتباه (۵۰m..۵km). */
        const val NAME_MAX = 60
        private const val EVENT_HISTORY_LIMIT = 1000
        private val STATES = setOf("unknown", "inside", "outside", "error")

        /** Haversine — فاصله‌ی دو نقطه بر حسب متر. */
        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }

    // Sharing OFF/ON invalidates any previous inside/outside claim until a fresh fix.
    fun resetStates(deviceId: UUID) = repo.resetStatesForDevice(deviceId)

    // ================= Parent: CRUD =================
    fun create(deviceId: UUID, req: GeofenceReq): GeofenceOut {
        val name = validate(req)
        if (repo.countZones(deviceId) >= deps.cfg.geofenceMaxPerDevice) {
            throw ApiError.conflict(ErrCode.GEO_ZONE_LIMIT, "zone limit reached (${deps.cfg.geofenceMaxPerDevice})")
        }
        // Duplicate Zone: نام تکراری روی یک دستگاه ⇒ 409
        if (repo.zoneByName(deviceId, name) != null) {
            throw ApiError.conflict(ErrCode.GEO_DUPLICATE_ZONE, "zone name already exists")
        }
        val id = repo.insertZone(
            deviceId, name, req.latitude, req.longitude, req.radiusM,
            req.enabled ?: true, req.notifyOnEntry ?: true, req.notifyOnExit ?: true,
        )
        repo.insertEvent(id, deviceId, EventType.GEOFENCE_CREATED, OffsetDateTime.now(), null, null)
        emitLifecycle(deviceId, id, name, EventType.GEOFENCE_CREATED)
        dlog.info("geofence created", "device" to maskDeviceId(deviceId), "zone" to maskDeviceId(id))
        return out(repo.zone(id)!!)
    }

    fun update(deviceId: UUID, zoneId: UUID, req: GeofenceReq): GeofenceOut {
        val prev = requireZone(deviceId, zoneId)
        val previousNotif = repo.notif(zoneId)
        val name = validate(req)
        val dup = repo.zoneByName(deviceId, name)
        if (dup != null && dup.id != zoneId) {
            throw ApiError.conflict(ErrCode.GEO_DUPLICATE_ZONE, "zone name already exists")
        }
        val enabled = req.enabled ?: prev.enabled
        repo.updateZone(
            zoneId, name, req.latitude, req.longitude, req.radiusM, enabled,
            req.notifyOnEntry ?: repo.notif(zoneId)?.notifyOnEntry ?: true,
            req.notifyOnExit ?: repo.notif(zoneId)?.notifyOnExit ?: true,
        )
        // بند ۱۰/۱۱: هندسه عوض شد یا zone بعد از غیرفعالی دوباره فعال شد ⇒
        // اطلاعات قبلی کافی نیست ⇒ UNKNOWN تا اولین fix معتبر (جلوگیری از False Alert)
        val geometryChanged = prev.latitude != req.latitude || prev.longitude != req.longitude ||
            prev.radiusM != req.radiusM
        val reEnabled = !prev.enabled && enabled
        val notifyChanged = req.notifyOnEntry?.let { it != (previousNotif?.notifyOnEntry ?: true) } == true ||
            req.notifyOnExit?.let { it != (previousNotif?.notifyOnExit ?: true) } == true
        val changed = prev.name != name || geometryChanged || prev.enabled != enabled || notifyChanged
        if (!enabled || geometryChanged || reEnabled) repo.resetState(zoneId)
        if (changed) {
            repo.insertEvent(zoneId, deviceId, EventType.GEOFENCE_UPDATED, OffsetDateTime.now(), null, null)
            emitLifecycle(deviceId, zoneId, name, EventType.GEOFENCE_UPDATED)
        }
        return out(repo.zone(zoneId)!!)
    }

    fun delete(deviceId: UUID, zoneId: UUID): Map<String, Any> {
        val prev = requireZone(deviceId, zoneId)
        // رویداد DELETED در feed پایش ثبت می‌شود «قبل» از حذف (تاریخچه‌ی zone با cascade
        // واقعاً پاک می‌شود — حذف واقعی مطابق سیاست حریم خصوصی پروژه)
        emitLifecycle(deviceId, zoneId, prev.name, EventType.GEOFENCE_DELETED)
        val n = repo.deleteZone(zoneId)
        dlog.info("geofence deleted", "device" to maskDeviceId(deviceId), "zone" to maskDeviceId(zoneId))
        return mapOf("deleted" to (n > 0), "zone_id" to zoneId.toString())
    }

    private fun applyEventRetention(deviceId: UUID) {
        val days = deps.cfg.geofenceEventRetentionDays.coerceIn(1, 365)
        repo.purgeOldEvents(deviceId, OffsetDateTime.now().minusDays(days.toLong()))
    }

    fun list(deviceId: UUID): GeofenceListOut {
        applyEventRetention(deviceId)
        val zones = repo.zones(deviceId)
        val transitionTimes = repo.transitionTimes(deviceId)
        return GeofenceListOut(
            zones = zones.map { out(it, transitionTimes[it.id]) },
            active = zones.count { it.enabled },
            maxZones = deps.cfg.geofenceMaxPerDevice,
            cooldownSec = deps.cfg.geofenceCooldownSec,
            minAccuracyM = deps.cfg.geofenceMinAccuracyM,
            minRadiusM = deps.cfg.geofenceMinRadiusM,
            maxRadiusM = deps.cfg.geofenceMaxRadiusM,
        )
    }

    fun zoneEvents(deviceId: UUID, zoneId: UUID, limit: Int): GeofenceEventsOut {
        applyEventRetention(deviceId)
        requireZone(deviceId, zoneId)
        val l = limit.coerceIn(1, EVENT_HISTORY_LIMIT)
        val rows = repo.eventsForZone(zoneId, l)
        return GeofenceEventsOut(rows.map { eventOut(it, null) }, rows.size)
    }

    fun history(deviceId: UUID, from: OffsetDateTime?, to: OffsetDateTime?, limit: Int): GeofenceEventsOut {
        applyEventRetention(deviceId)
        val l = limit.coerceIn(1, EVENT_HISTORY_LIMIT)
        val rows = repo.eventsForDevice(deviceId, from, to, l)
        return GeofenceEventsOut(rows.map { eventOut(it, null) }, rows.size)
    }

    // ================= Child: read-only + cross-validated events =================
    /** کودک فقط وضعیت «خواندنی» zoneهای خودش را می‌گیرد — بدون مختصات والد، بدون کنترل. */
    fun childZones(deviceId: UUID): ChildGeofencesOut {
        val zones = repo.zones(deviceId)
        return ChildGeofencesOut(
            zones.map { z ->
                val st = repo.state(z.id)
                ChildGeofenceOut(z.id, z.name, st?.state ?: "unknown", z.radiusM, z.enabled)
            },
        )
    }

    /**
     * POST رویداد از کودک — «هرگز به payload کودک اعتماد نکن»:
     * - بدون مختصات ⇒ ادعا پذیرفته نمی‌شود (accepted=false)؛ منبع حقیقت، fixهای sync است.
     * - با مختصات ⇒ سرور هندسه را باز-محاسبه می‌کند؛ ادعای سازگار ⇒ evaluate (گذار واقعی)،
     *   ادعای متناقض ⇒ GEOFENCE_ERROR (نه گذار جعلی).
     */
    fun handleChildEvent(deviceId: UUID, req: GeofenceEventPost): GeofenceEventAck {
        val zone = repo.zone(req.geofenceId) ?: throw ApiError.notFound("zone not found")
        // Deleted/foreign zone نتواند رویداد معتبر بسازد
        if (zone.deviceId != deviceId) throw ApiError.notFound("zone not found")
        if (!zone.enabled) return GeofenceEventAck(false, repo.state(zone.id)?.state ?: "unknown", "zone disabled")
        val type = req.eventType.uppercase()
        if (type !in setOf("GEOFENCE_ENTER", "GEOFENCE_EXIT", "GEOFENCE_ERROR")) {
            throw ApiError.validation("bad event_type")
        }
        // دستگاه اجازه ندارد با payload دلخواه، state/alert ورود و خروج را جعل کند.
        // Transitionها فقط از fixهای Phase 4 که سرور هندسی ارزیابی می‌کند ساخته می‌شوند.
        if (type == "GEOFENCE_ENTER" || type == "GEOFENCE_EXIT") {
            return GeofenceEventAck(false, repo.state(zone.id)?.state ?: "unknown", "transitions are server-derived")
        }
        if (type == "GEOFENCE_ERROR") {
            // گزارش خطای سمت دستگاه ⇒ گذار اتمی به error؛ تکرار همزمان یک‌بار شمرده می‌شود.
            val ts = tsOf(req.clientTs) ?: OffsetDateTime.now()
            val inserted = repo.markError(zone.id, deviceId, ts, req.accuracyM)
            if (inserted != null) emitMonitoring(deviceId, zone, EventType.GEOFENCE_ERROR, req.accuracyM, null, notify = true)
            return GeofenceEventAck(true, "error", null)
        }
        val lat = req.latitude ?: return GeofenceEventAck(false, repo.state(zone.id)?.state ?: "unknown", "coordinates required")
        val lon = req.longitude ?: return GeofenceEventAck(false, repo.state(zone.id)?.state ?: "unknown", "coordinates required")
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) throw ApiError.validation("coordinates out of range")
        val ts = tsOf(req.clientTs) ?: OffsetDateTime.now()
        // evaluate روی همان نقطه ⇒ نتیجه، حقیقتِ محاسبه‌شده‌ی سرور است نه ادعای کودک
        val before = repo.state(zone.id)?.state ?: "unknown"
        evaluateOne(deviceId, ZoneFull(zone, repo.state(zone.id) ?: GeofenceStateRow(zone.id, before, null, null, ts), repo.notif(zone.id) ?: GeofenceNotifRow(zone.id, true, true, true)), lat, lon, req.accuracyM, ts)
        val after = repo.state(zone.id)?.state ?: before
        val consistent = (type == "GEOFENCE_ENTER" && after == "inside") || (type == "GEOFENCE_EXIT" && after == "outside") || before == after
        if (!consistent) {
            repo.insertEvent(zone.id, deviceId, EventType.GEOFENCE_ERROR, ts, req.accuracyM, null)
            emitMonitoring(deviceId, zone, EventType.GEOFENCE_ERROR, req.accuracyM, null, notify = false)
            return GeofenceEventAck(false, after, "claim contradicts server geometry")
        }
        return GeofenceEventAck(true, after, null)
    }

    // ================= موتور ارزیابی — ورودی: fixهای Phase 4 =================
    /** پس از ذخیره‌ی نقاط در LocationService.sync صدا زده می‌شود (ترتیب زمانی، idempotent). */
    fun evaluateFixes(deviceId: UUID, points: List<LocationPointIn>) {
        applyEventRetention(deviceId)
        val zones = repo.enabledZonesFull(deviceId)
        if (zones.isEmpty()) return
        for (p in points.sortedBy { it.clientTs }) {
            val ts = tsOf(p.clientTs) ?: continue
            val acc = p.accuracyM?.takeIf { it >= 0f }
            for (zf in zones) {
                evaluateOne(deviceId, zf, p.latitude, p.longitude, acc, ts)
            }
        }
    }

    private fun evaluateOne(
        deviceId: UUID,
        zf: ZoneFull,
        lat: Double,
        lon: Double,
        acc: Float?,
        ts: OffsetDateTime,
    ) {
        val z = zf.zone
        val st = zf.state
        val dist = distanceM(z.latitude, z.longitude, lat, lon)
        // دقت خیلی ضعیف یا نامعلوم ⇒ Location Uncertain: بدون گذار
        if (acc == null || acc > deps.cfg.geofenceMinAccuracyM) {
            repo.touchAccuracy(z.id, acc)
            zf.state = st.copy(lastKnownAccuracy = acc)
            return
        }
        // hysteresis هندسی: نوار مرزی مبهم ⇒ حفظ حالت قبلی
        val target = when {
            dist + acc <= z.radiusM -> "inside"
            dist - acc >= z.radiusM -> "outside"
            else -> null
        }
        if (target == null || target == st.state) {
            repo.touchAccuracy(z.id, acc)
            zf.state = st.copy(lastKnownAccuracy = acc)
            return
        }
        // اولین fix معتبر (یا بازیابی از ERROR) فقط state اولیه را تعیین می‌کند؛
        // ENTER/EXIT alert تولید نمی‌شود چون ورود/خروج مشاهده نشده است.
        if (st.state == "unknown" || st.state == "error") {
            repo.setStateOnly(z.id, target, ts)
            zf.state = GeofenceStateRow(z.id, target, ts, acc, ts)
            return
        }
        // fix کهنه‌تر از آخرین گذار ⇒ state قبلاً جلو رفته؛ نادیده گرفته می‌شود
        val lastT = st.lastTransitionAt
        if (lastT != null && !ts.isAfter(lastT)) {
            zf.state = st.copy(lastKnownAccuracy = acc)
            return
        }
        // cooldown/debounce — پینگ‌پنگ روی مرز
        if (lastT != null && Duration.between(lastT, ts).seconds < deps.cfg.geofenceCooldownSec) {
            zf.state = st.copy(lastKnownAccuracy = acc)
            return
        }
        val eventType = if (target == "inside") EventType.GEOFENCE_ENTER else EventType.GEOFENCE_EXIT
        // Dwell detection: مدت حضور داخل zone قبل از خروج
        val dwellMin = if (target == "outside" && st.state == "inside" && lastT != null) {
            Duration.between(lastT, ts).toMinutes().toInt().takeIf { it >= 0 }
        } else {
            null
        }
        val transitionId = repo.transition(
            z.id, deviceId, st.state, st.lastTransitionAt, target, eventType, ts, acc, dwellMin,
        )
        if (transitionId == null) {
            // درخواست همزمان/تکراری: state دیگر مطابق snapshot ما نیست ⇒ رویداد نساز.
            zf.state = repo.state(z.id) ?: st
            return
        }
        zf.state = GeofenceStateRow(z.id, target, ts, acc, ts)
        val notify = when (target) {
            "inside" -> zf.notif.notifyOnEntry && zf.notif.enabled
            else -> zf.notif.notifyOnExit && zf.notif.enabled
        }
        emitMonitoring(deviceId, z, eventType, acc, dwellMin, notify)
        dlog.info(
            "geofence transition", "device" to maskDeviceId(deviceId), "zone" to maskDeviceId(z.id),
            "type" to eventType, "dist" to dist.roundToLong().toString(),
        )
    }

    // ================= helpers =================
    private fun requireZone(deviceId: UUID, zoneId: UUID): GeofenceRow {
        val z = repo.zone(zoneId) ?: throw ApiError.notFound("zone not found")
        if (z.deviceId != deviceId) throw ApiError.notFound("zone not found")
        return z
    }

    private fun validate(req: GeofenceReq): String {
        val name = req.name.trim()
        if (name.isEmpty() || name.length > NAME_MAX) throw ApiError.validation("name must be 1..$NAME_MAX chars")
        if (req.latitude !in -90.0..90.0) throw ApiError.validation("latitude out of range")
        if (req.longitude !in -180.0..180.0) throw ApiError.validation("longitude out of range")
        // شعاع free-input؛ بازه از config سرور می‌آید (default 50..5000m)، نه مقدار client.
        if (req.radiusM < deps.cfg.geofenceMinRadiusM || req.radiusM > deps.cfg.geofenceMaxRadiusM) {
            throw ApiError.validation("radius must be ${deps.cfg.geofenceMinRadiusM}..${deps.cfg.geofenceMaxRadiusM} meters")
        }
        return name
    }

    private fun tsOf(ms: Long?): OffsetDateTime? =
        ms?.let { runCatching { OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }.getOrNull() }

    private fun emitLifecycle(deviceId: UUID, zoneId: UUID, name: String, type: String) {
        val meta = mapOf("zone" to name, "zone_id" to zoneId.toString(), "notify" to "false")
        deps.db.tx { c ->
            events.emit(c, deviceId, type, Json.write(meta), "$deviceId:$type:$zoneId:${OffsetDateTime.now().toEpochSecond()}")
        }
    }

    private fun emitMonitoring(
        deviceId: UUID,
        zone: GeofenceRow,
        type: String,
        accuracyM: Float?,
        dwellMin: Int?,
        notify: Boolean,
    ) {
        val meta = buildMap {
            put("zone", zone.name)
            put("zone_id", zone.id.toString())
            put("radius_m", zone.radiusM.toString())
            put("notify", notify.toString())
            if (accuracyM != null) put("accuracy_m", accuracyM.toInt().toString())
            if (dwellMin != null) put("dwell_min", dwellMin.toString())
        }
        deps.db.tx { c ->
            // dedupe کلید شامل zone+زمان رویداد ⇒ تکرار حتی در retry هم غیرممکن
            events.emit(c, deviceId, type, Json.write(meta), "$deviceId:$type:${zone.id}:${OffsetDateTime.now().toEpochSecond()}")
        }
    }

    private fun out(z: GeofenceRow, transitions: Pair<OffsetDateTime?, OffsetDateTime?>? = null): GeofenceOut {
        val st = repo.state(z.id)
        val entryExit = transitions ?: repo.transitionTimes(z.deviceId)[z.id]
        val n = repo.notif(z.id) ?: GeofenceNotifRow(z.id, true, true, true)
        val lastEv = repo.lastEventForZone(z.id)
        return GeofenceOut(
            id = z.id,
            name = z.name,
            latitude = z.latitude,
            longitude = z.longitude,
            radiusM = z.radiusM,
            enabled = z.enabled,
            state = st?.state ?: "unknown",
            lastTransitionAt = st?.lastTransitionAt?.toString(),
            lastEntryAt = entryExit?.first?.toString(),
            lastExitAt = entryExit?.second?.toString(),
            lastKnownAccuracy = st?.lastKnownAccuracy?.takeIf { it >= 0f },
            notifyOnEntry = n.notifyOnEntry,
            notifyOnExit = n.notifyOnExit,
            notifyEnabled = n.enabled,
            createdAt = z.createdAt.toString(),
            updatedAt = z.updatedAt.toString(),
            lastEvent = lastEv?.let { eventOut(it, z.name) },
        )
    }

    private fun eventOut(r: GeofenceEventRow, zoneName: String?): GeofenceEventOut = GeofenceEventOut(
        id = r.id,
        geofenceId = r.geofenceId,
        zoneName = zoneName ?: repo.zone(r.geofenceId)?.name ?: "?",
        eventType = r.eventType,
        ts = r.ts.toString(),
        accuracyM = r.accuracyM?.takeIf { it >= 0f },
        dwellMin = r.dwellMin,
        processedAt = r.processedAt.toString(),
    )
}
