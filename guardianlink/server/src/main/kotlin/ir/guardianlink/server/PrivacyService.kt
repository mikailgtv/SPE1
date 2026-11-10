package ir.guardianlink.server

import java.sql.Connection
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID
import org.postgresql.util.PGobject

/** Export explicitly allowlists family-scoped columns. Authentication material and binary assets are never selected. */
class PrivacyService(private val deps: AppDeps) {
    data class ExportResult(val document: Map<String, Any?>, val records: Int)

    private data class Dataset(val name: String, val sql: String, val args: (UUID, UUID) -> Array<Any?> = { family, _ -> arrayOf(family) })

    companion object {
        const val MAX_RECORDS = 10_000
        const val MAX_BYTES = 5 * 1024 * 1024
        private const val ROW_TIMEOUT_SECONDS = 5
    }

    fun exportFamily(familyId: UUID, requesterId: UUID, format: String, scope: String): ExportResult {
        if (format != "json") throw ApiError.validation("format must be json")
        if (scope != "family") throw ApiError.validation("scope must be family")

        val export = deps.db.tx { c ->
            c.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            c.isReadOnly = true
            c.exec("SET LOCAL statement_timeout = '5000ms'")
            c.exec("SET LOCAL transaction_timeout = '30000ms'")
            val family = c.one(
                "SELECT id,name,created_at FROM families WHERE id=?",
                familyId,
            ) { linkedMapOf<String, Any?>(
                "id" to it.getObject("id").toString(),
                "name" to it.getString("name"),
                "created_at" to it.getObject("created_at").toString(),
            ) } ?: throw ApiError.notFound(ErrCode.NO_FAMILY)

            val members = c.query(
                "SELECT id,user_id,role,created_at FROM family_members WHERE family_id=? ORDER BY created_at,id LIMIT ?",
                familyId, MAX_RECORDS + 1,
                map = ::rowMap,
            )
            var recordCount = members.size + 1
            if (recordCount > MAX_RECORDS) throw tooLarge()
            val data = linkedMapOf<String, List<Map<String, Any?>>>()
            for (dataset in datasets) {
                val remaining = MAX_RECORDS - recordCount
                if (remaining <= 0) throw tooLarge()
                val rows = c.query(
                    "${dataset.sql} LIMIT ?",
                    *dataset.args(familyId, requesterId), remaining + 1,
                    map = ::rowMap,
                )
                if (rows.size > remaining) throw tooLarge()
                data[dataset.name] = rows
                recordCount += rows.size
            }
            Triple(family, members, data) to recordCount
        }

        val document = linkedMapOf<String, Any?>(
            "schema_version" to 1,
            "generated_at" to OffsetDateTime.now().toString(),
            "scope" to "family",
            "family" to export.first.first,
            "members" to export.first.second,
            "data" to export.first.third,
            "record_count" to export.second,
        )
        if (Json.write(document).toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw ApiError(io.ktor.http.HttpStatusCode.PayloadTooLarge, "EXPORT_TOO_LARGE", "export exceeds the 5 MiB limit")
        }
        return ExportResult(document, export.second)
    }

    /** Explicit family and device joins prevent a client-supplied identifier from widening export scope. */
    private val datasets = listOf(
        Dataset("devices", """SELECT id,family_id,device_name,manufacturer,model,android_version,app_version,battery_level,last_seen,status,created_at
            FROM devices WHERE family_id=? ORDER BY created_at,id"""),
        Dataset("device_status", """SELECT s.device_id,s.battery_level,s.battery_band,s.connection_state,s.health_state,s.last_heartbeat,s.last_sync,
            s.app_version,s.android_version,s.model,s.manufacturer,s.permissions,s.updated_at
            FROM device_status s JOIN devices d ON d.id=s.device_id WHERE d.family_id=? ORDER BY s.device_id"""),
        Dataset("device_events", """SELECT e.id,e.device_id,e.event_type,e.ts,e.metadata
            FROM device_events e JOIN devices d ON d.id=e.device_id WHERE d.family_id=? ORDER BY e.ts,e.id"""),
        Dataset("family_settings", "SELECT family_id,battery_low_pct,battery_critical_pct,sync_delayed_sec,offline_sec,heartbeat_interval_sec,updated_at FROM family_settings WHERE family_id=? ORDER BY family_id"),
        Dataset("app_usage_daily", """SELECT u.id,u.device_id,u.package_name,u.app_name,u.category,u.usage_date,u.usage_minutes,u.launch_count,u.first_used_at,u.last_used_at,u.updated_at
            FROM app_usage_daily u JOIN devices d ON d.id=u.device_id WHERE d.family_id=? ORDER BY u.usage_date,u.device_id,u.package_name"""),
        Dataset("screen_time_limits", """SELECT s.id,s.device_id,s.daily_limit_minutes,s.near_pct,s.enabled,s.created_at,s.updated_at
            FROM screen_time_limits s JOIN devices d ON d.id=s.device_id WHERE d.family_id=? ORDER BY s.device_id"""),
        Dataset("screen_time_schedules", """SELECT s.id,s.device_id,s.name,s.start_time,s.end_time,s.days_of_week,s.enabled,s.created_at,s.updated_at
            FROM screen_time_schedules s JOIN devices d ON d.id=s.device_id WHERE d.family_id=? ORDER BY s.device_id,s.id"""),
        Dataset("screen_time_state", """SELECT s.device_id,s.day,s.limit_state,s.schedule_keys FROM screen_time_state s
            JOIN devices d ON d.id=s.device_id WHERE d.family_id=? ORDER BY s.device_id,s.day"""),
        Dataset("locations", """SELECT l.id,l.device_id,l.latitude,l.longitude,l.accuracy_m,l.client_ts,l.server_ts,l.source FROM locations l
            JOIN devices d ON d.id=l.device_id WHERE d.family_id=? ORDER BY l.client_ts,l.id"""),
        Dataset("location_settings", """SELECT s.device_id,s.sharing_enabled,s.retention_days,s.updated_at FROM location_settings s
            JOIN devices d ON d.id=s.device_id WHERE d.family_id=? ORDER BY s.device_id"""),
        Dataset("location_status", """SELECT s.device_id,s.permission_status,s.provider_status,s.last_fix_ts,s.last_report_ts,s.health_state,s.updated_at
            FROM location_status s JOIN devices d ON d.id=s.device_id WHERE d.family_id=? ORDER BY s.device_id"""),
        Dataset("geofences", """SELECT g.id,g.device_id,g.name,g.latitude,g.longitude,g.radius_m,g.enabled,g.created_at,g.updated_at FROM geofences g
            JOIN devices d ON d.id=g.device_id WHERE d.family_id=? ORDER BY g.device_id,g.id"""),
        Dataset("geofence_states", """SELECT s.geofence_id,s.state,s.last_transition_at,s.last_known_accuracy,s.updated_at FROM geofence_states s
            JOIN geofences g ON g.id=s.geofence_id JOIN devices d ON d.id=g.device_id WHERE d.family_id=? ORDER BY s.geofence_id"""),
        Dataset("geofence_events", """SELECT e.id,e.geofence_id,e.device_id,e.event_type,e.ts,e.accuracy_m,e.dwell_min,e.processed_at FROM geofence_events e
            JOIN devices d ON d.id=e.device_id WHERE d.family_id=? ORDER BY e.ts,e.id"""),
        Dataset("geofence_notification_settings", """SELECT s.geofence_id,s.notify_on_entry,s.notify_on_exit,s.enabled FROM geofence_notification_settings s
            JOIN geofences g ON g.id=s.geofence_id JOIN devices d ON d.id=g.device_id WHERE d.family_id=? ORDER BY s.geofence_id"""),
        Dataset("app_inventory", """SELECT a.device_id,a.package_name,a.app_name,a.version_name,a.version_code,a.installed_at,a.is_system_app,a.category,a.installed,a.uninstalled_at,a.first_seen_at,a.updated_at
            FROM app_inventory a JOIN devices d ON d.id=a.device_id WHERE d.family_id=? ORDER BY a.device_id,a.package_name"""),
        Dataset("app_management_state", """SELECT a.device_id,a.rule_version,a.management_mode,a.usage_access_granted,a.timezone_id,a.inventory_updated_at,a.last_sync_at,a.updated_at
            FROM app_management_state a JOIN devices d ON d.id=a.device_id WHERE d.family_id=? ORDER BY a.device_id"""),
        Dataset("app_rules", """SELECT r.id,r.device_id,r.package_name,r.rule_type,r.daily_limit_seconds,r.near_pct,r.critical_pct,r.enabled,r.created_by,r.version,r.created_at,r.updated_at,r.last_enforcement_status,r.last_enforcement_at
            FROM app_rules r JOIN devices d ON d.id=r.device_id WHERE d.family_id=? ORDER BY r.device_id,r.id"""),
        Dataset("app_rule_schedules", """SELECT s.id,s.rule_id,s.day_of_week,s.start_time,s.end_time,s.enabled FROM app_rule_schedules s
            JOIN app_rules r ON r.id=s.rule_id JOIN devices d ON d.id=r.device_id WHERE d.family_id=? ORDER BY s.rule_id,s.id"""),
        Dataset("app_rule_usage_state", """SELECT s.rule_id,s.usage_date,s.last_stage,s.updated_at FROM app_rule_usage_state s
            JOIN app_rules r ON r.id=s.rule_id JOIN devices d ON d.id=r.device_id WHERE d.family_id=? ORDER BY s.rule_id,s.usage_date"""),
        Dataset("app_restriction_events", """SELECT e.id,e.device_id,e.rule_id,e.package_name,e.event_type,e.ts,e.metadata,e.processed_at FROM app_restriction_events e
            JOIN devices d ON d.id=e.device_id WHERE d.family_id=? ORDER BY e.ts,e.id"""),
        Dataset("web_protection_settings", """SELECT s.device_id,s.enabled,s.default_policy,s.requested_mode,s.alerts_enabled,s.rule_version,s.reported_mode,s.reported_status,s.reported_reason,s.dns_status,s.vpn_status,s.timezone_id,s.last_reported_at,s.updated_at
            FROM web_protection_settings s JOIN devices d ON d.id=s.device_id WHERE d.family_id=? ORDER BY s.device_id"""),
        Dataset("web_rules", """SELECT r.id,r.device_id,r.rule_type,r.domain,r.scope,r.action,r.category,r.enabled,r.source,r.priority,r.created_by,r.version,r.created_at,r.updated_at
            FROM web_rules r JOIN devices d ON d.id=r.device_id WHERE d.family_id=? ORDER BY r.device_id,r.id"""),
        Dataset("web_rule_schedules", """SELECT s.id,s.rule_id,s.day_of_week,s.start_time,s.end_time,s.action,s.timezone_id,s.enabled FROM web_rule_schedules s
            JOIN web_rules r ON r.id=s.rule_id JOIN devices d ON d.id=r.device_id WHERE d.family_id=? ORDER BY s.rule_id,s.id"""),
        Dataset("web_overrides", """SELECT w.id,w.device_id,w.rule_id,w.domain,w.scope,w.action,w.expires_at,w.created_by,w.created_at,w.revoked_at FROM web_overrides w
            JOIN devices d ON d.id=w.device_id WHERE d.family_id=? ORDER BY w.device_id,w.id"""),
        Dataset("web_filter_events", """SELECT e.id,e.device_id,e.rule_id,e.event_type,e.ts,e.metadata,e.source FROM web_filter_events e
            JOIN devices d ON d.id=e.device_id WHERE d.family_id=? ORDER BY e.ts,e.id"""),
        Dataset("family_notifications", """SELECT n.id,n.family_id,n.device_id,n.event_id,n.event_type,n.category,n.severity,n.title_key,n.body_key,n.metadata,n.occurred_at,n.status,n.acknowledged_at,n.resolved_at,n.created_at
            FROM family_notifications n WHERE n.family_id=? ORDER BY n.occurred_at,n.id"""),
        Dataset("notification_user_state", """SELECT s.notification_id,s.user_id,s.read_at FROM notification_user_state s
            JOIN family_notifications n ON n.id=s.notification_id WHERE n.family_id=? AND s.user_id=? ORDER BY s.notification_id""",
            { family, requester -> arrayOf(family, requester) }),
        Dataset("notification_preferences", "SELECT family_id,enabled_categories,quiet_hours_enabled,quiet_start,quiet_end,timezone,critical_during_quiet_hours,daily_summary_enabled,cooldown_minutes,updated_at FROM notification_preferences WHERE family_id=? ORDER BY family_id"),
        Dataset("sos_preferences", "SELECT family_id,auto_expiration_hours,updated_at FROM sos_preferences WHERE family_id=? ORDER BY family_id"),
        Dataset("sos_sessions", """SELECT s.id,s.family_id,s.device_id,s.client_request_id,s.source,s.status,s.activated_at,s.acknowledged_at,s.acknowledged_by,s.resolved_at,s.resolved_by,s.cancelled_at,s.cancelled_by_device,s.expires_at,s.tracking_enabled,s.tracking_started_at,s.tracking_ends_at,s.tracking_interval_sec,s.tracking_status,s.tracking_reason,s.battery_level,s.network_state,s.device_state,s.permission_status,s.provider_status,s.resolve_note,s.created_at,s.updated_at
            FROM sos_sessions s WHERE s.family_id=? ORDER BY s.activated_at,s.id"""),
        Dataset("sos_location_points", """SELECT p.id,p.session_id,p.latitude,p.longitude,p.accuracy_m,p.client_ts,p.server_ts,p.provider,p.source FROM sos_location_points p
            JOIN sos_sessions s ON s.id=p.session_id WHERE s.family_id=? ORDER BY p.client_ts,p.id"""),
        Dataset("sos_events", """SELECT e.id,e.session_id,e.family_id,e.device_id,e.event_type,e.actor_type,e.actor_user_id,e.occurred_at,e.metadata FROM sos_events e
            WHERE e.family_id=? ORDER BY e.occurred_at,e.id"""),
        Dataset("sos_notes", """SELECT n.id,n.session_id,n.family_id,n.author_user_id,n.note,n.created_at FROM sos_notes n
            WHERE n.family_id=? ORDER BY n.created_at,n.id"""),
    )

    fun deleteFamily(familyId: UUID, requesterId: UUID): Int = deps.db.tx { c ->
        c.one("SELECT id FROM families WHERE id=? FOR UPDATE", familyId) { it.uuid("id") }
        val members = c.one("SELECT count(*)::INT AS n FROM family_members WHERE family_id=?", familyId) { it.getInt("n") } ?: 0
        if (members != 1 || c.one("SELECT 1 AS n FROM family_members WHERE family_id=? AND user_id=?", familyId, requesterId) { it.getInt("n") } == null) {
            throw ApiError.conflict("FAMILY_SHARED", "family deletion requires the requester to be its only member")
        }
        val devices = c.one("SELECT count(*)::INT AS n FROM devices WHERE family_id=?", familyId) { it.getInt("n") } ?: 0
        if (c.exec("DELETE FROM families WHERE id=?", familyId) != 1) throw ApiError.notFound(ErrCode.NO_FAMILY)
        devices
    }

    fun deleteAccount(userId: UUID): Int = deps.db.tx { c ->
        c.one("SELECT id FROM users WHERE id=? FOR UPDATE", userId) { it.uuid("id") } ?: throw ApiError.notFound()
        val familyId = c.one("SELECT family_id FROM family_members WHERE user_id=? FOR UPDATE", userId) { it.uuid("family_id") }
        var removedDevices = 0
        if (familyId != null) {
            c.one("SELECT id FROM families WHERE id=? FOR UPDATE", familyId) { it.uuid("id") } ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            val members = c.one("SELECT count(*)::INT AS n FROM family_members WHERE family_id=?", familyId) { it.getInt("n") } ?: 0
            if (members != 1) throw ApiError.conflict("FAMILY_SHARED", "account deletion is unavailable while the family has other members")
            removedDevices = c.one("SELECT count(*)::INT AS n FROM devices WHERE family_id=?", familyId) { it.getInt("n") } ?: 0
            if (c.exec("DELETE FROM families WHERE id=?", familyId) != 1) throw ApiError.notFound(ErrCode.NO_FAMILY)
        }
        if (c.exec("DELETE FROM users WHERE id=?", userId) != 1) throw ApiError.notFound()
        removedDevices
    }

    private fun tooLarge() = ApiError(
        io.ktor.http.HttpStatusCode.PayloadTooLarge,
        "EXPORT_TOO_LARGE",
        "export exceeds the 10,000-record limit",
    )

    private fun rowMap(rs: java.sql.ResultSet): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        for (i in 1..rs.metaData.columnCount) {
            val name = rs.metaData.getColumnLabel(i)
            val value = rs.getObject(i)
            out[name] = when (value) {
                null -> null
                is UUID -> value.toString()
                is Timestamp -> value.toInstant().toString()
                is Date -> value.toLocalDate().toString()
                is Time -> value.toLocalTime().toString()
                is ByteArray -> "[omitted]" // binary data is intentionally never exported
                is PGobject -> value.value
                else -> value
            }
        }
        return out
    }
}
