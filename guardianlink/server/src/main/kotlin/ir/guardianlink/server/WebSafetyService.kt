package ir.guardianlink.server

import java.sql.Connection
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Phase 7 service. Child reports are capability/status only; rule telemetry never contains visits. */
private data class WebSafetyStateRow(
    val enabled: Boolean, val defaultPolicy: String, val requestedMode: String, val alertsEnabled: Boolean,
    val ruleVersion: Long, val reportedMode: String, val reportedStatus: String, val reportedReason: String,
    val dnsStatus: String, val vpnStatus: String, val timezoneId: String, val lastReportedAt: String?,
)

private data class WebSafetySyncSettings(
    val enabled: Boolean, val defaultPolicy: String, val requestedMode: String, val ruleVersion: Long,
)

class WebSafetyService(private val deps: AppDeps) {
    private val db = deps.db
    private val eventRepo = deps.events
    private val defaultPolicyValues = setOf("ALLOW_ALL", "BLOCK_LISTED", "ALLOW_LISTED_ONLY")
    private val modeValues = setOf("AUTO", "VPN_BASED", "DNS_BASED", "MANAGED_DEVICE")
    private val maxActiveOverrides = 100
    private val statuses = setOf("UNKNOWN", "DISABLED", "UNSUPPORTED", "CONFIGURATION_APPLIED_UNVERIFIED", "ERROR", "LIMITED")
    private val modes = setOf("UNKNOWN", "NORMAL", "MANAGED_DEVICE")
    private val reasonCodes = setOf(
        "none", "disabled", "managed_device_required", "chrome_not_installed", "policy_readback_mismatch",
        "policy_stored_effect_unverified", "category_provider_unavailable", "vpn_not_implemented",
        "dns_provider_not_configured", "android_policy_rejected", "timezone_invalid",
    )
    private val childEventTypes = setOf("WEB_FILTERING_UNAVAILABLE", "WEB_FILTERING_ERROR")

    fun settings(deviceId: UUID): WebSafetySettingsOut {
        ensureSettings(deviceId)
        val s = db.one("SELECT * FROM web_protection_settings WHERE device_id=?", deviceId) { rs ->
            WebSafetyStateRow(
                rs.getBoolean("enabled"), rs.getString("default_policy"), rs.getString("requested_mode"),
                rs.getBoolean("alerts_enabled"), rs.getLong("rule_version"), rs.getString("reported_mode"),
                rs.getString("reported_status"), rs.getString("reported_reason"), rs.getString("dns_status"),
                rs.getString("vpn_status"), rs.getString("timezone_id"), rs.odtN("last_reported_at")?.toString(),
            )
        } ?: throw ApiError.notFound("web safety settings not found")
        return WebSafetySettingsOut(
            s.enabled, s.defaultPolicy, s.requestedMode, s.alertsEnabled, s.ruleVersion, s.reportedMode,
            s.reportedStatus, s.reportedReason, s.dnsStatus, s.vpnStatus, s.timezoneId, s.lastReportedAt,
            rules(deviceId), overrides(deviceId), categoryProviderAvailable = false,
        )
    }

    fun updateSettings(deviceId: UUID, userId: UUID, req: WebSafetySettingsReq): WebSafetySettingsOut {
        if (req.defaultPolicy !in defaultPolicyValues || req.requestedMode !in modeValues)
            throw ApiError.validation("invalid policy or mode")
        if (req.expectedVersion < 0) throw ApiError.validation("invalid expectedVersion")
        ensureSettings(deviceId)
        db.tx { c ->
            val changed = c.exec(
                """UPDATE web_protection_settings SET enabled=?,default_policy=?,requested_mode=?,alerts_enabled=?,
                   rule_version=rule_version+1,updated_at=now() WHERE device_id=? AND rule_version=?""",
                req.enabled, req.defaultPolicy, req.requestedMode, req.alertsEnabled, deviceId, req.expectedVersion,
            )
            if (changed != 1) throw ApiError.conflict("WEB_RULE_CONFLICT", "web policy changed; reload and retry")
            val type = if (req.enabled) "WEB_PROTECTION_CONFIG_CHANGED" else "WEB_PROTECTION_DISABLED"
            insertEvent(c, deviceId, null, type, "parent", "${deviceId}:settings:${req.expectedVersion + 1}", "parent_config")
            eventRepo.emit(c, deviceId, type, Json.write(mapOf("source" to "parent")), "$deviceId:web-settings:${req.expectedVersion + 1}")
        }
        return settings(deviceId)
    }

    fun createRule(deviceId: UUID, userId: UUID, req: WebSafetyRuleReq): WebSafetyRuleOut {
        validateRule(req)
        ensureSettings(deviceId)
        if (ruleCount(deviceId) >= 500) throw ApiError.validation("maximum 500 web rules per device")
        val norm = req.domain?.let { WebSafetyPolicy.normalizeDomain(it, req.scope) }
        val id = UUID.randomUUID()
        try {
            db.tx { c ->
                c.exec(
                    """INSERT INTO web_rules(id,device_id,rule_type,domain,scope,action,category,enabled,priority,created_by)
                       VALUES(?,?,?,?,?,?,?,?,?,?)""",
                    id, deviceId, req.ruleType, norm?.first, norm?.second ?: req.scope, req.action,
                    req.category?.lowercase(), req.enabled, req.priority, userId,
                )
                replaceSchedules(c, id, req.schedules)
                bumpVersion(c, deviceId)
                insertEvent(c, deviceId, id, "WEB_RULE_CREATED", "parent", "$deviceId:rule-created:$id", "parent_config")
                eventRepo.emit(c, deviceId, "WEB_RULE_CHANGED", Json.write(mapOf("rule_id" to id.toString(), "source" to "parent")), "$deviceId:web-rule:$id:1")
            }
        } catch (e: Exception) {
            if (e is ApiError) throw e
            if (e.message?.contains("ux_web_") == true || e.message?.contains("duplicate key") == true)
                throw ApiError.conflict("WEB_RULE_DUPLICATE", "an equivalent rule already exists")
            throw e
        }
        return rule(deviceId, id)!!
    }

    fun updateRule(deviceId: UUID, id: UUID, req: WebSafetyRuleReq): WebSafetyRuleOut {
        validateRule(req)
        val expected = req.expectedVersion ?: throw ApiError.validation("expectedVersion is required")
        val norm = req.domain?.let { WebSafetyPolicy.normalizeDomain(it, req.scope) }
        mapDuplicateConflict {
        db.tx { c ->
            val changed = c.exec(
                """UPDATE web_rules SET rule_type=?,domain=?,scope=?,action=?,category=?,enabled=?,priority=?,
                   version=version+1,updated_at=now() WHERE device_id=? AND id=? AND version=?""",
                req.ruleType, norm?.first, norm?.second ?: req.scope, req.action, req.category?.lowercase(), req.enabled,
                req.priority, deviceId, id, expected,
            )
            if (changed != 1) {
                if (c.one("SELECT 1 AS n FROM web_rules WHERE id=? AND device_id=?", id, deviceId) { it.getInt("n") } == null)
                    throw ApiError.notFound("web rule not found")
                throw ApiError.conflict("WEB_RULE_CONFLICT", "web rule changed; reload and retry")
            }
            replaceSchedules(c, id, req.schedules)
            bumpVersion(c, deviceId)
            insertEvent(c, deviceId, id, "WEB_RULE_UPDATED", "parent", "$deviceId:rule-updated:$id:$expected", "parent_config")
            eventRepo.emit(c, deviceId, "WEB_RULE_CHANGED", Json.write(mapOf("rule_id" to id.toString(), "source" to "parent")), "$deviceId:web-rule:$id:${expected + 1}")
        }
        }
        return rule(deviceId, id)!!
    }

    fun deleteRule(deviceId: UUID, id: UUID, expected: Long) {
        db.tx { c ->
            val changed = c.exec("DELETE FROM web_rules WHERE device_id=? AND id=? AND version=?", deviceId, id, expected)
            if (changed != 1) {
                if (c.one("SELECT 1 AS n FROM web_rules WHERE device_id=? AND id=?", deviceId, id) { it.getInt("n") } == null)
                    throw ApiError.notFound("web rule not found")
                throw ApiError.conflict("WEB_RULE_CONFLICT", "web rule changed; reload and retry")
            }
            bumpVersion(c, deviceId)
            insertEvent(c, deviceId, id, "WEB_RULE_DELETED", "parent", "$deviceId:rule-deleted:$id:$expected", "parent_config")
            eventRepo.emit(c, deviceId, "WEB_RULE_CHANGED", Json.write(mapOf("rule_id" to id.toString(), "source" to "parent", "deleted" to true)), "$deviceId:web-rule:$id:deleted:${expected + 1}")
        }
    }

    fun createOverride(deviceId: UUID, userId: UUID, req: WebSafetyOverrideReq): WebSafetyOverrideOut {
        val norm = WebSafetyPolicy.normalizeDomain(req.domain, req.scope)
        if (req.action !in setOf("ALLOW", "BLOCK") || req.durationSeconds !in 60..86_400)
            throw ApiError.validation("override action or duration is invalid (60 seconds to 24 hours)")
        val expires = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(req.durationSeconds)
        ensureSettings(deviceId)
        val id = db.tx { c ->
            c.one("SELECT rule_version FROM web_protection_settings WHERE device_id=? FOR UPDATE", deviceId) { it.getLong("rule_version") }
            val activeCount = c.one("SELECT count(*)::int AS n FROM web_overrides WHERE device_id=? AND revoked_at IS NULL AND expires_at>now()", deviceId) { it.getInt("n") } ?: 0
            if (activeCount >= maxActiveOverrides) throw ApiError.validation("maximum active web overrides reached")
            val newId = c.execReturningUuid(
                """INSERT INTO web_overrides(device_id,domain,scope,action,expires_at,created_by)
                   VALUES(?,?,?,?,?,?) RETURNING id""",
                deviceId, norm.first, norm.second, req.action, expires, userId,
            )
            bumpVersion(c, deviceId)
            insertEvent(c, deviceId, null, "WEB_OVERRIDE_CREATED", "parent", "$deviceId:override:$newId", "parent_config")
            newId
        }
        return WebSafetyOverrideOut(id, norm.first, norm.second, req.action, expires.toString())
    }

    fun revokeOverride(deviceId: UUID, id: UUID): Boolean = db.tx { c ->
        val n = c.exec("UPDATE web_overrides SET revoked_at=now() WHERE device_id=? AND id=? AND revoked_at IS NULL AND expires_at>now()", deviceId, id)
        if (n > 0) {
            bumpVersion(c, deviceId)
            insertEvent(c, deviceId, null, "WEB_OVERRIDE_REVOKED", "parent", "$deviceId:override-revoked:$id", "parent_config")
            eventRepo.emit(c, deviceId, "WEB_RULE_CHANGED", Json.write(mapOf("override_id" to id.toString(), "revoked" to true)), "$deviceId:web-override-revoked:$id")
        }
        n > 0
    }

    fun rulesSync(deviceId: UUID): WebSafetySyncOut {
        ensureSettings(deviceId)
        val s = db.one("SELECT enabled,default_policy,requested_mode,rule_version FROM web_protection_settings WHERE device_id=?", deviceId) { rs ->
            WebSafetySyncSettings(rs.getBoolean("enabled"), rs.getString("default_policy"), rs.getString("requested_mode"), rs.getLong("rule_version"))
        }!!
        return WebSafetySyncOut(
            s.enabled, s.defaultPolicy, s.requestedMode, s.ruleVersion,
            rules(deviceId), overrides(deviceId),
        )
    }

    fun syncFromChild(deviceId: UUID, input: WebSafetySyncIn?): WebSafetySyncOut {
        ensureSettings(deviceId)
        if (input != null) {
            val mode = input.reportedMode?.uppercase() ?: "UNKNOWN"
            val status = input.reportedStatus?.uppercase() ?: "UNKNOWN"
            val reason = input.reportedReason ?: "none"
            if (mode !in modes || status !in statuses || reason !in reasonCodes) throw ApiError.validation("invalid web safety status")
            val dns = input.dnsStatus?.uppercase() ?: "UNSUPPORTED"
            val vpn = input.vpnStatus?.uppercase() ?: "UNSUPPORTED"
            if (dns !in setOf("UNSUPPORTED", "NOT_CONFIGURED") || vpn !in setOf("UNSUPPORTED", "NOT_CONFIGURED"))
                throw ApiError.validation("invalid web safety capability status")
            val zone = input.timezoneId?.let { runCatching { ZoneId.of(it).id }.getOrElse { throw ApiError.validation("invalid device timezone") } } ?: "UTC"
            if ((input.events?.size ?: 0) > 20) throw ApiError.validation("too many web events")
            input.events.orEmpty().forEach { e ->
                if (runCatching { UUID.fromString(e.clientEventId) }.isFailure || e.eventType !in childEventTypes ||
                    e.reasonCode !in reasonCodes || e.clientTs < 0 || kotlin.math.abs(System.currentTimeMillis() - e.clientTs) > 7L * 86_400_000L)
                    throw ApiError.validation("invalid web safety event")
            }
            db.tx { c ->
                c.exec("DELETE FROM web_filter_events WHERE device_id=? AND ts < now() - interval '90 days'", deviceId)
                c.exec("DELETE FROM web_overrides WHERE device_id=? AND created_at < now() - interval '90 days' AND (revoked_at IS NOT NULL OR expires_at < now())", deviceId)
                val old = c.one("SELECT reported_status,reported_mode,reported_reason FROM web_protection_settings WHERE device_id=? FOR UPDATE", deviceId) {
                    Triple(it.getString("reported_status"), it.getString("reported_mode"), it.getString("reported_reason"))
                }
                c.exec(
                    """UPDATE web_protection_settings SET reported_mode=?,reported_status=?,reported_reason=?,dns_status=?,vpn_status=?,
                       timezone_id=?,last_reported_at=now(),updated_at=now() WHERE device_id=?""",
                    mode, status, reason, dns, vpn, zone, deviceId,
                )
                if (old != null && (old.first != status || old.third != reason)) {
                    val eventType = if (status == "ERROR") "WEB_FILTERING_ERROR" else if (status == "UNSUPPORTED") "WEB_FILTERING_UNAVAILABLE" else "WEB_FILTERING_STATUS_CHANGED"
                    val hasMatchingClientEvent = input.events.orEmpty().any { it.eventType == eventType && it.reasonCode == reason }
                    if (!hasMatchingClientEvent) {
                        val transitionKey = "$deviceId:status:${UUID.randomUUID()}"
                        insertEvent(c, deviceId, null, eventType, "device", transitionKey, reason)
                        if (status in setOf("ERROR", "UNSUPPORTED")) {
                            val alertsEnabled = c.one("SELECT alerts_enabled FROM web_protection_settings WHERE device_id=?", deviceId) { it.getBoolean("alerts_enabled") } ?: true
                            if (alertsEnabled) eventRepo.emit(c, deviceId, eventType, Json.write(mapOf("status" to status, "reason_code" to reason, "source" to "device")), transitionKey)
                        }
                    }
                }
                input.events.orEmpty().forEach { e ->
                    val ts = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.clientTs), ZoneOffset.UTC)
                    val inserted = c.exec(
                        """INSERT INTO web_filter_events(device_id,event_type,ts,metadata,dedupe_key,source)
                           VALUES(?,?,?,?,?,'device') ON CONFLICT(dedupe_key) DO NOTHING""",
                        deviceId, e.eventType, ts, Json.write(mapOf("reason_code" to e.reasonCode)), "$deviceId:${e.clientEventId}",
                    )
                    if (inserted > 0 && e.eventType in setOf("WEB_FILTERING_ERROR", "WEB_FILTERING_UNAVAILABLE")) {
                        val alertsEnabled = c.one("SELECT alerts_enabled FROM web_protection_settings WHERE device_id=?", deviceId) { it.getBoolean("alerts_enabled") } ?: true
                        if (alertsEnabled) eventRepo.emit(c, deviceId, e.eventType, Json.write(mapOf("reason_code" to e.reasonCode, "source" to "device")), "$deviceId:${e.clientEventId}")
                    }
                }
            }
        }
        return rulesSync(deviceId)
    }

    fun events(deviceId: UUID, limitRaw: Int): WebSafetyEventsOut {
        val limit = limitRaw.coerceIn(1, 200)
        val list = db.query(
            "SELECT id,rule_id,event_type,ts,metadata,source FROM web_filter_events WHERE device_id=? ORDER BY ts DESC LIMIT ?",
            deviceId, limit,
        ) { rs ->
            val metadata = Json.readMap(rs.getString("metadata"))
            WebSafetyEventOut(
                rs.uuid("id"), rs.uuidN("rule_id"), rs.getString("event_type"), rs.odt("ts").toString(),
                metadata["reason_code"] ?: "parent_config", rs.getString("source"),
            )
        }
        return WebSafetyEventsOut(list)
    }

    private fun <T> mapDuplicateConflict(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        if (e is ApiError) throw e
        if (e.message?.contains("ux_web_") == true || e.message?.contains("duplicate key") == true)
            throw ApiError.conflict("WEB_RULE_DUPLICATE", "an equivalent rule already exists")
        throw e
    }

    private fun validateRule(r: WebSafetyRuleReq) {
        if (r.ruleType !in setOf("DOMAIN", "CATEGORY") || r.action !in setOf("ALLOW", "BLOCK") || r.priority !in -1000..1000 ||
            r.scope !in setOf("EXACT_HOST", "DOMAIN_AND_SUBDOMAINS"))
            throw ApiError.validation("invalid rule")
        if (r.ruleType == "DOMAIN") {
            if (r.domain.isNullOrBlank() || r.category != null) throw ApiError.validation("domain rule requires domain only")
            WebSafetyPolicy.normalizeDomain(r.domain, r.scope)
        } else {
            if (r.domain != null) throw ApiError.validation("category rule cannot have a domain")
            WebSafetyPolicy.validateCategory(r.category)
        }
        if (r.schedules.size > 28) throw ApiError.validation("too many schedules")
        r.schedules.forEach { s ->
            if (s.dayOfWeek !in 1..7 || s.action !in setOf("ALLOW", "BLOCK") || s.startTime == s.endTime)
                throw ApiError.validation("invalid schedule")
            runCatching { LocalTime.parse(s.startTime) }.getOrElse { throw ApiError.validation("invalid schedule start time") }
            runCatching { LocalTime.parse(s.endTime) }.getOrElse { throw ApiError.validation("invalid schedule end time") }
            s.timezoneId?.let { runCatching { ZoneId.of(it) }.getOrElse { throw ApiError.validation("invalid schedule timezone") } }
        }
    }

    private fun replaceSchedules(c: Connection, ruleId: UUID, schedules: List<WebSafetyScheduleReq>) {
        c.exec("DELETE FROM web_rule_schedules WHERE rule_id=?", ruleId)
        schedules.forEach { s ->
            c.exec(
                """INSERT INTO web_rule_schedules(rule_id,day_of_week,start_time,end_time,action,timezone_id,enabled)
                   VALUES(?,?,?,?,?,?,?)""",
                ruleId, s.dayOfWeek, LocalTime.parse(s.startTime), LocalTime.parse(s.endTime), s.action, s.timezoneId, s.enabled,
            )
        }
    }

    private fun bumpVersion(c: Connection, deviceId: UUID) {
        c.exec("UPDATE web_protection_settings SET rule_version=rule_version+1,updated_at=now() WHERE device_id=?", deviceId)
    }

    private fun insertEvent(c: Connection, deviceId: UUID, ruleId: UUID?, type: String, source: String, dedupe: String, reason: String) {
        c.exec(
            """INSERT INTO web_filter_events(device_id,rule_id,event_type,metadata,dedupe_key,source)
               VALUES(?,?,?,?,?,?) ON CONFLICT(dedupe_key) DO NOTHING""",
            deviceId, ruleId, type, Json.write(mapOf("reason_code" to reason)), dedupe, webEventStorageSource(source),
        )
    }

    private fun ensureSettings(deviceId: UUID) {
        db.exec("INSERT INTO web_protection_settings(device_id) VALUES(?) ON CONFLICT(device_id) DO NOTHING", deviceId)
    }
    private fun ruleCount(deviceId: UUID) = db.one("SELECT count(*)::int AS n FROM web_rules WHERE device_id=?", deviceId) { it.getInt("n") } ?: 0

    private fun rules(deviceId: UUID): List<WebSafetyRuleOut> {
        val scheduleMap = db.query(
            """SELECT s.rule_id,s.id,s.day_of_week,s.start_time,s.end_time,s.action,s.timezone_id,s.enabled FROM web_rule_schedules s JOIN web_rules r ON r.id=s.rule_id
               WHERE r.device_id=? ORDER BY s.day_of_week,s.start_time""", deviceId,
        ) { rs ->
            rs.uuid("rule_id") to WebSafetyScheduleOut(
                rs.uuid("id"), rs.getInt("day_of_week"), rs.getTime("start_time").toLocalTime().toString(),
                rs.getTime("end_time").toLocalTime().toString(), rs.getString("action"), rs.getString("timezone_id"), rs.getBoolean("enabled"),
            )
        }.groupBy({ it.first }, { it.second })
        return db.query("SELECT * FROM web_rules WHERE device_id=? ORDER BY priority DESC,created_at,id", deviceId) { rs ->
            val id = rs.uuid("id")
            WebSafetyRuleOut(
                id, rs.uuid("device_id"), rs.getString("rule_type"), rs.getString("domain"), rs.getString("scope"), rs.getString("category"),
                rs.getString("action"), rs.getBoolean("enabled"), rs.getInt("priority"), rs.getLong("version"),
                categoryEnforceable = false, schedules = scheduleMap[id].orEmpty(), rs.odt("created_at").toString(), rs.odt("updated_at").toString(),
            )
        }
    }

    private fun rule(deviceId: UUID, id: UUID) = rules(deviceId).firstOrNull { it.id == id }

    private fun overrides(deviceId: UUID): List<WebSafetyOverrideOut> = db.query(
        "SELECT id,domain,scope,action,expires_at FROM web_overrides WHERE device_id=? AND revoked_at IS NULL AND expires_at>now() ORDER BY expires_at",
        deviceId,
    ) { WebSafetyOverrideOut(it.uuid("id"), it.getString("domain"), it.getString("scope"), it.getString("action"), it.odt("expires_at").toString()) }
}

/** Web-filter event source is a server/device origin, unlike the parent/device actor in audit metadata. */
internal fun webEventStorageSource(source: String): String = when (source) {
    "parent", "server" -> "server"
    "device" -> "device"
    else -> throw IllegalArgumentException("unsupported web event source")
}
