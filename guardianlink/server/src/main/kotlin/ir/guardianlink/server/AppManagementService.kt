package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Phase 6 server orchestration. Device identity is always supplied by the verified Child token;
 * Parent device access is checked by AppManagementRoutes before calling mutations/reads.
 * Rules are advisory unless the Child reports genuine Device Owner capability and the local
 * official Android DPM adapter reports success. Client capability fields are never authorization.
 */
class AppManagementService(private val deps: AppDeps) {
    private val repo = AppManagementRepo(deps.db)
    private val events = deps.events

    companion object {
        const val MAX_INVENTORY = 5000
        const val MAX_EVENTS_PER_BATCH = 100
        const val MAX_RULES_PER_DEVICE = 200
        private const val AGENT_PACKAGE = "ir.guardianlink.child"
        private val PKG_RX = Regex("^[A-Za-z0-9_.$]{1,180}$")
        private val APP_NAME_MAX = 180
        private val RULE_TYPES = setOf("ALLOW", "LIMITED", "RESTRICTED", "SCHEDULED")
        private val MODES = setOf("normal", "managed", "unsupported", "unknown")
        private val CATEGORIES = setOf(
            "undefined", "game", "audio", "video", "image", "social", "news", "maps",
            "productivity", "accessibility", "system", "other",
        )
        private val CHILD_EVENT_TYPES = setOf(
            EventType.APP_RESTRICTION_ENFORCED,
            EventType.APP_RESTRICTION_FAILED,
            EventType.APP_RESTRICTION_UNSUPPORTED,
        )
        private val CHILD_EVENT_DETAILS = setOf(
            "device_owner_required", "usage_access_required", "package_suspended",
            "package_not_suspended", "android_rejected_package_suspension",
        )
    }

    fun syncFromChild(deviceId: UUID, input: AppManagementSyncIn?): AppRulesSyncOut {
        if (input == null) return rulesSync(deviceId)
        val mode = input.managementMode?.lowercase() ?: "unknown"
        if (mode !in MODES) throw ApiError.validation("invalid management mode")
        val zone = parseZone(input.timeZoneId)
        val inventory = input.inventory
        if (inventory != null) {
            if (inventory.size > MAX_INVENTORY) throw ApiError.validation("app inventory too large")
            val seen = HashSet<String>()
            for (item in inventory) validateInventory(item, seen)
        }
        val incomingEvents = input.events ?: emptyList()
        if (incomingEvents.size > MAX_EVENTS_PER_BATCH) throw ApiError.validation("too many app events")
        incomingEvents.forEach(::validateChildEvent)

        val oldState = repo.state(deviceId)
        deps.db.tx { c ->
            c.exec("DELETE FROM app_restriction_events WHERE device_id=? AND ts < ?", deviceId, OffsetDateTime.now(ZoneOffset.UTC).minusDays(90))
            repo.setChildStatus(c, deviceId, mode, input.usageAccessGranted ?: false, zone.id)
            if (inventory != null) {
                if (input.inventoryComplete && inventory.isNotEmpty()) repo.markAllInventoryNotInstalled(c, deviceId)
                for (item in inventory) {
                    val installedAt = item.installedAtMs?.let {
                        if (it < 0L || it > System.currentTimeMillis() + 86_400_000L) null
                        else OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneOffset.UTC)
                    }
                    repo.upsertInventory(c, deviceId, item, installedAt)
                }
                if (input.inventoryComplete && inventory.isNotEmpty()) {
                    repo.markInventorySnapshot(c, deviceId)
                    repo.purgeOldUninstalled(c, deviceId)
                }
            }
            if (oldState != null && oldState.managementMode != mode) {
                val meta = mapOf("from" to oldState.managementMode, "to" to mode, "source" to "child_report")
                events.emit(c, deviceId, EventType.DEVICE_MANAGEMENT_CHANGED, Json.write(meta), "$deviceId:management:$mode")
            }
            for (event in incomingEvents) persistChildEvent(c, deviceId, event)
        }
        return rulesSync(deviceId)
    }

    private fun validateInventory(a: AppInventoryItemIn, seen: MutableSet<String>) {
        if (!PKG_RX.matches(a.packageName)) throw ApiError.validation("invalid package name in inventory")
        if (!seen.add(a.packageName)) throw ApiError.validation("duplicate package in inventory")
        if (a.appName.isBlank() || a.appName.length > APP_NAME_MAX) throw ApiError.validation("invalid app label")
        if (a.versionName.orEmpty().length > 100 || a.versionCode < 0) throw ApiError.validation("invalid app version")
        if (a.installedAtMs != null && a.installedAtMs < 0) throw ApiError.validation("invalid install time")
        if ((a.category ?: "undefined").lowercase() !in CATEGORIES) throw ApiError.validation("invalid app category")
    }

    private fun validateChildEvent(e: AppRestrictionEventIn) {
        if (runCatching { UUID.fromString(e.clientEventId) }.isFailure) throw ApiError.validation("invalid app event id")
        if (e.eventType !in CHILD_EVENT_TYPES) throw ApiError.validation("invalid app event type")
        if (!PKG_RX.matches(e.packageName) || e.ruleVersion < 0) throw ApiError.validation("invalid app event")
        if (e.clientTs < 0 || kotlin.math.abs(System.currentTimeMillis() - e.clientTs) > 7L * 86_400_000L) {
            throw ApiError.validation("app event timestamp outside accepted window")
        }
        if (e.detail != null && e.detail !in CHILD_EVENT_DETAILS) throw ApiError.validation("invalid app event detail")
    }

    private fun persistChildEvent(c: java.sql.Connection, deviceId: UUID, e: AppRestrictionEventIn) {
        val currentRule = repo.rule(deviceId, e.packageName)
        val ts = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(e.clientTs), ZoneOffset.UTC)
        val metadata = buildMap {
            put("rule_version", e.ruleVersion.toString())
            put("source", "device")
            e.detail?.let { put("detail", it) }
        }
        val dedupeKey = "$deviceId:${e.clientEventId}"
        val inserted = repo.insertRestrictionEvent(
            c, deviceId, currentRule?.id, e.packageName, e.eventType, ts, Json.write(metadata), dedupeKey,
        ) != null
        if (!inserted) return
        if (currentRule != null && currentRule.version == e.ruleVersion) {
            val status = when (e.eventType) {
                EventType.APP_RESTRICTION_ENFORCED -> "applied"
                EventType.APP_RESTRICTION_FAILED -> "failed"
                else -> "unsupported"
            }
            c.exec(
                "UPDATE app_rules SET last_enforcement_status=?,last_enforcement_at=?,updated_at=now() WHERE id=?",
                status, ts, currentRule.id,
            )
        }
        val meta = buildMap {
            put("package_name", e.packageName)
            put("rule_version", e.ruleVersion.toString())
            put("source", "device")
            e.detail?.let { put("detail", it) }
        }
        events.emit(c, deviceId, e.eventType, Json.write(meta), dedupeKey)
    }

    fun rulesSync(deviceId: UUID): AppRulesSyncOut =
        AppRulesSyncOut(repo.maxVersion(deviceId), repo.rules(deviceId).map { toOut(it) })

    fun parentRules(deviceId: UUID): AppRulesOut {
        val state = repo.state(deviceId)
        return AppRulesOut(
            state?.ruleVersion ?: 0,
            state?.managementMode ?: "unknown",
            state?.usageAccessGranted ?: false,
            repo.rules(deviceId).map { toOut(it) },
        )
    }

    fun catalog(deviceId: UUID, includeUninstalled: Boolean = false): AppCatalogListOut {
        val state = repo.state(deviceId)
        val today = LocalDate.now(parseZone(state?.timeZoneId))
        val apps = repo.inventory(deviceId, includeUninstalled).map { app ->
            val rule = repo.rule(deviceId, app.packageName)
            AppCatalogOut(
                app.packageName, app.appName, app.versionName, app.versionCode, app.installedAt?.toString(),
                app.isSystemApp, app.category, app.installed,
                repo.usageTodaySeconds(deviceId, app.packageName, today),
                repo.usageWeekSeconds(deviceId, app.packageName, today.minusDays(6), today),
                rule?.let(::toOut),
            )
        }
        val mode = state?.managementMode ?: "unknown"
        val capability = when {
            mode == "managed" -> "managed_device_owner_reported"
            mode == "normal" -> "advisory_only"
            else -> "unsupported_or_unknown"
        }
        return AppCatalogListOut(
            apps, mode, capability, state?.usageAccessGranted ?: false,
            state?.ruleVersion ?: 0, state?.inventoryUpdatedAt?.toString(), state?.lastSyncAt?.toString(),
        )
    }

    fun usageHistory(deviceId: UUID, pkg: String, days: Int): AppUsageHistoryOut {
        if (!PKG_RX.matches(pkg)) throw ApiError.validation("invalid package name")
        if (days !in 1..30) throw ApiError.validation("days must be 1..30")
        val zone = parseZone(repo.state(deviceId)?.timeZoneId)
        val to = LocalDate.now(zone)
        return AppUsageHistoryOut(pkg, repo.usageHistory(deviceId, pkg, to.minusDays(days - 1L), to), zone.id)
    }

    fun events(deviceId: UUID, requestedLimit: Int): AppRestrictionEventsOut {
        val limit = requestedLimit.coerceIn(1, 200)
        val rows = repo.restrictionEvents(deviceId, limit)
        return AppRestrictionEventsOut(rows.map {
            AppRestrictionEventOut(it.id, it.packageName, it.eventType, it.ts, Json.readMap(it.metadata))
        }, rows.size)
    }

    fun create(deviceId: UUID, parentId: UUID, req: AppRuleReq): AppRuleOut {
        val normalized = validateRule(req)
        val app = repo.inventoryItem(deviceId, req.packageName)
            ?: throw ApiError.notFound("app not present in this device inventory")
        if (!app.installed) throw ApiError.notFound("app is not installed")
        if (repo.rule(deviceId, req.packageName) != null) throw ApiError.conflict(ErrCode.APP_RULE_CONFLICT, "rule already exists")
        if (repo.rules(deviceId).size >= MAX_RULES_PER_DEVICE) throw ApiError.conflict(ErrCode.APP_RULE_CONFLICT, "rule limit reached")
        val version = deps.db.tx { c ->
            val next = repo.bumpVersion(c, deviceId)
            val id = repo.createRule(c, deviceId, req.packageName, normalized.type, normalized.limit,
                normalized.near, normalized.critical, normalized.enabled, parentId, next, normalized.schedules)
            emitRuleChange(c, deviceId, req.packageName, id, next, "created")
            next
        }
        return repo.rule(deviceId, req.packageName)?.let(::toOut) ?: throw IllegalStateException("rule $version was not saved")
    }

    fun update(deviceId: UUID, ruleId: UUID, req: AppRuleReq): AppRuleOut {
        val old = repo.ruleById(deviceId, ruleId) ?: throw ApiError.notFound("app rule not found")
        val expected = req.expectedVersion ?: throw ApiError.validation("expectedVersion is required")
        if (expected != old.version) throw ApiError.conflict(ErrCode.APP_RULE_CONFLICT, "rule version changed; refresh before editing")
        if (req.packageName != old.packageName) throw ApiError.validation("packageName cannot be changed")
        val normalized = validateRule(req.copy(enabled = req.enabled ?: old.enabled))
        deps.db.tx { c ->
            val version = repo.bumpVersion(c, deviceId)
            if (!repo.updateRule(c, ruleId, expected, old.packageName, normalized.type, normalized.limit,
                    normalized.near, normalized.critical, normalized.enabled, version, normalized.schedules)) {
                throw ApiError.conflict(ErrCode.APP_RULE_CONFLICT, "rule version changed; refresh before editing")
            }
            val type = if (old.enabled && !normalized.enabled) EventType.APP_RULE_DISABLED else EventType.APP_RULE_CHANGED
            emitRuleChange(c, deviceId, old.packageName, ruleId, version, type)
        }
        return repo.ruleById(deviceId, ruleId)?.let(::toOut) ?: throw ApiError.notFound("app rule not found")
    }

    fun delete(deviceId: UUID, ruleId: UUID, expectedVersion: Long) {
        val old = repo.ruleById(deviceId, ruleId) ?: throw ApiError.notFound("app rule not found")
        deps.db.tx { c ->
            if (!repo.deleteRule(c, deviceId, ruleId, expectedVersion)) {
                throw ApiError.conflict(ErrCode.APP_RULE_CONFLICT, "rule version changed; refresh before deleting")
            }
            val version = repo.bumpVersion(c, deviceId)
            events.emit(c, deviceId, EventType.APP_RULE_DISABLED,
                Json.write(mapOf("package_name" to old.packageName, "rule_version" to version.toString(), "source" to "parent")),
                "$deviceId:rule:$version:${old.packageName}:deleted")
        }
    }

    /** Called after Phase 3 usage ingestion; each crossing is stored once per rule and local date. */
    fun evaluateUploadedUsage(deviceId: UUID) {
        val state = repo.state(deviceId) ?: return
        val date = LocalDate.now(parseZone(state.timeZoneId))
        // Usage threshold state and app-specific event metadata are retained for 90 days.
        deps.db.tx { c ->
            c.exec("DELETE FROM app_rule_usage_state WHERE usage_date < ?", date.minusDays(90))
            c.exec("DELETE FROM app_restriction_events WHERE device_id=? AND ts < ?", deviceId, OffsetDateTime.now(ZoneOffset.UTC).minusDays(90))
        }
        val rules = repo.rules(deviceId).filter { it.enabled && it.ruleType == "LIMITED" && it.dailyLimitSeconds != null }
        for (rule in rules) {
            val used = repo.usageTodaySeconds(deviceId, rule.packageName, date) ?: continue
            val stage = AppManagementRepo.thresholdState(rule, used)
            val previous = deps.db.one(
                "SELECT last_stage FROM app_rule_usage_state WHERE rule_id=? AND usage_date=?", rule.id, date,
            ) { it.getInt("last_stage") } ?: 0
            if (stage <= previous) continue
            deps.db.tx { c ->
                val current = c.one(
                    "SELECT last_stage FROM app_rule_usage_state WHERE rule_id=? AND usage_date=? FOR UPDATE", rule.id, date,
                ) { it.getInt("last_stage") } ?: 0
                if (stage <= current) return@tx
                c.exec(
                    """INSERT INTO app_rule_usage_state(rule_id,usage_date,last_stage) VALUES(?,?,?)
                       ON CONFLICT(rule_id,usage_date) DO UPDATE SET last_stage=GREATEST(app_rule_usage_state.last_stage,EXCLUDED.last_stage),updated_at=now()""", 
                    rule.id, date, stage,
                )
                val crossings = (current + 1..stage).mapNotNull { s ->
                    when (s) {
                        1 -> EventType.APP_USAGE_LIMIT_NEAR
                        2 -> EventType.APP_USAGE_LIMIT_CRITICAL
                        3 -> EventType.APP_USAGE_LIMIT_REACHED
                        else -> null
                    }
                }
                for (type in crossings) {
                    val meta = mapOf(
                        "package_name" to rule.packageName,
                        "used_seconds" to used.toString(),
                        "daily_limit_seconds" to rule.dailyLimitSeconds.toString(),
                        "date" to date.toString(),
                        "threshold_pct" to when (type) {
                            EventType.APP_USAGE_LIMIT_NEAR -> rule.nearPct.toString()
                            EventType.APP_USAGE_LIMIT_CRITICAL -> rule.criticalPct.toString()
                            else -> "100"
                        },
                        "source" to "usage_stats",
                    )
                    val dedupe = "$deviceId:${rule.id}:$date:$type"
                    repo.insertRestrictionEvent(c, deviceId, rule.id, rule.packageName, type, OffsetDateTime.now(ZoneOffset.UTC), Json.write(meta), dedupe)
                    events.emit(c, deviceId, type, Json.write(meta), dedupe)
                }
            }
        }
    }

    fun zoneId(deviceId: UUID): String = repo.state(deviceId)?.timeZoneId ?: "UTC"

    private data class ValidRule(val type: String, val limit: Int?, val near: Int, val critical: Int, val enabled: Boolean, val schedules: List<AppRuleScheduleReq>)

    private fun validateRule(req: AppRuleReq): ValidRule {
        if (!PKG_RX.matches(req.packageName)) throw ApiError.validation("invalid package name")
        if (req.packageName.equals(AGENT_PACKAGE, ignoreCase = true)) {
            throw ApiError.validation("the Child agent cannot be targeted by an app rule")
        }
        val type = req.ruleType.uppercase()
        if (type !in RULE_TYPES) throw ApiError.validation("invalid ruleType")
        val limit = req.dailyLimitSeconds
        if (type == "LIMITED" && (limit == null || limit !in 60..86400)) throw ApiError.validation("LIMITED requires dailyLimitSeconds 60..86400")
        if (type != "LIMITED" && limit != null) throw ApiError.validation("dailyLimitSeconds only applies to LIMITED")
        val near = req.nearPct ?: 80
        val critical = req.criticalPct ?: 90
        if (near !in 1..99 || critical !in 1..100 || critical <= near) throw ApiError.validation("thresholds must satisfy 1 <= near < critical <= 100")
        val schedules = req.schedules ?: emptyList()
        if (type == "SCHEDULED" && schedules.isEmpty()) throw ApiError.validation("SCHEDULED requires at least one schedule")
        if (type != "SCHEDULED" && schedules.isNotEmpty()) throw ApiError.validation("schedules only apply to SCHEDULED")
        if (schedules.size > 21) throw ApiError.validation("too many schedule windows")
        schedules.forEach { s ->
            if (s.daysOfWeek !in 1..127) throw ApiError.validation("daysOfWeek must be a seven-bit mask")
            if (!Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(s.startTime) || !Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(s.endTime)) throw ApiError.validation("schedule time must use HH:mm")
            if (s.startTime == s.endTime) throw ApiError.validation("schedule start and end must differ")
        }
        return ValidRule(type, limit, near, critical, req.enabled ?: true, schedules)
    }

    private fun emitRuleChange(c: java.sql.Connection, deviceId: UUID, pkg: String, ruleId: UUID, version: Long, action: String) {
        val type = if (action == "created" || action == "updated") EventType.APP_RULE_CHANGED else action
        events.emit(c, deviceId, type,
            Json.write(mapOf("package_name" to pkg, "rule_id" to ruleId.toString(), "rule_version" to version.toString(), "action" to action, "source" to "parent")),
            "$deviceId:rule:$version:$pkg:$action")
    }

    private fun toOut(rule: AppRuleRow): AppRuleOut {
        val appName = repo.inventoryItem(rule.deviceId, rule.packageName)?.appName ?: rule.packageName
        val schedules = repo.schedules(rule.id)
        val combined = linkedMapOf<String, AppRuleScheduleOut>()
        schedules.forEach { s ->
            val key = "${s.startTime}|${s.endTime}|${s.enabled}"
            val old = combined[key]
            val mask = (old?.dayOfWeek ?: 0) or (1 shl (s.dayOfWeek - 1))
            combined[key] = AppRuleScheduleOut(old?.id ?: s.id, mask, s.startTime, s.endTime, s.enabled)
        }
        return AppRuleOut(rule.id, rule.deviceId, rule.packageName, appName, rule.ruleType, rule.dailyLimitSeconds,
            rule.nearPct, rule.criticalPct, rule.enabled, rule.version, rule.lastEnforcementStatus,
            rule.lastEnforcementAt?.toString(), combined.values.toList(), rule.createdAt.toString(), rule.updatedAt.toString())
    }

    private fun parseZone(id: String?): ZoneId {
        if (id == null) return ZoneId.of("UTC")
        return runCatching { ZoneId.of(id) }.getOrElse { throw ApiError.validation("invalid timeZoneId") }
    }
}
