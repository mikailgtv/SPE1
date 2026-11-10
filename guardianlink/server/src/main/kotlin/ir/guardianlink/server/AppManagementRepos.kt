package ir.guardianlink.server

import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class AppManagementRepo(private val db: Db) {
    companion object {
        fun thresholdState(rule: AppRuleRow, usedSeconds: Int): Int {
            val limit = rule.dailyLimitSeconds ?: return 0
            return when {
                usedSeconds >= limit -> 3
                usedSeconds >= limit * rule.criticalPct / 100 -> 2
                usedSeconds >= limit * rule.nearPct / 100 -> 1
                else -> 0
            }
        }
    }
    fun state(deviceId: UUID): AppManagementStateRow? =
        db.one("SELECT * FROM app_management_state WHERE device_id=?", deviceId) { it.appManagementStateRow() }

    fun ensureState(conn: Connection, deviceId: UUID) {
        conn.exec("INSERT INTO app_management_state(device_id) VALUES(?) ON CONFLICT(device_id) DO NOTHING", deviceId)
    }

    fun setChildStatus(conn: Connection, deviceId: UUID, mode: String, usageAccess: Boolean, zoneId: String) {
        conn.exec(
            """INSERT INTO app_management_state(device_id,management_mode,usage_access_granted,timezone_id,last_sync_at)
               VALUES(?,?,?,?,now())
               ON CONFLICT(device_id) DO UPDATE SET management_mode=EXCLUDED.management_mode,
                 usage_access_granted=EXCLUDED.usage_access_granted,timezone_id=EXCLUDED.timezone_id,last_sync_at=now(),updated_at=now()""",
            deviceId, mode, usageAccess, zoneId,
        )
    }

    fun markInventorySnapshot(conn: Connection, deviceId: UUID) {
        conn.exec(
            "UPDATE app_management_state SET inventory_updated_at=now(),updated_at=now() WHERE device_id=?",
            deviceId,
        )
    }

    fun upsertInventory(conn: Connection, deviceId: UUID, a: AppInventoryItemIn, installedAt: OffsetDateTime?) {
        conn.exec(
            """INSERT INTO app_inventory(device_id,package_name,app_name,version_name,version_code,installed_at,is_system_app,category,installed)
               VALUES(?,?,?,?,?,?,?,?,true)
               ON CONFLICT(device_id,package_name) DO UPDATE SET app_name=EXCLUDED.app_name,
                 version_name=EXCLUDED.version_name,version_code=EXCLUDED.version_code,
                 installed_at=COALESCE(EXCLUDED.installed_at,app_inventory.installed_at),
                 is_system_app=EXCLUDED.is_system_app,category=EXCLUDED.category,installed=true,uninstalled_at=NULL,updated_at=now()""", 
            deviceId, a.packageName, a.appName, a.versionName ?: "", a.versionCode, installedAt,
            a.isSystemApp, (a.category ?: "undefined").lowercase(),
        )
    }

    fun markAllInventoryNotInstalled(conn: Connection, deviceId: UUID) {
        conn.exec("UPDATE app_inventory SET installed=false,uninstalled_at=now(),updated_at=now() WHERE device_id=? AND installed=true", deviceId)
    }

    fun purgeOldUninstalled(conn: Connection, deviceId: UUID) {
        conn.exec("DELETE FROM app_inventory WHERE device_id=? AND installed=false AND uninstalled_at < now()-interval '30 days'", deviceId)
    }

    fun inventory(deviceId: UUID, includeUninstalled: Boolean = false): List<AppInventoryRow> =
        db.query(
            "SELECT * FROM app_inventory WHERE device_id=? AND (? OR installed=true) ORDER BY lower(app_name),package_name",
            deviceId, includeUninstalled,
        ) { it.appInventoryRow() }

    fun inventoryItem(deviceId: UUID, pkg: String): AppInventoryRow? =
        db.one("SELECT * FROM app_inventory WHERE device_id=? AND package_name=?", deviceId, pkg) { it.appInventoryRow() }

    fun rule(deviceId: UUID, pkg: String): AppRuleRow? =
        db.one("SELECT * FROM app_rules WHERE device_id=? AND package_name=?", deviceId, pkg) { it.appRuleRow() }

    fun ruleById(deviceId: UUID, id: UUID): AppRuleRow? =
        db.one("SELECT * FROM app_rules WHERE device_id=? AND id=?", deviceId, id) { it.appRuleRow() }

    fun rules(deviceId: UUID): List<AppRuleRow> =
        db.query("SELECT * FROM app_rules WHERE device_id=? ORDER BY package_name", deviceId) { it.appRuleRow() }

    /** Increment device-level version in the same transaction as each parent mutation. */
    fun bumpVersion(conn: Connection, deviceId: UUID): Long =
        conn.one(
            """INSERT INTO app_management_state(device_id,rule_version) VALUES(?,1)
               ON CONFLICT(device_id) DO UPDATE SET rule_version=app_management_state.rule_version+1,updated_at=now()
               RETURNING rule_version""",
            deviceId,
        ) { it.getLong("rule_version") } ?: error("rule version update failed")

    fun createRule(
        conn: Connection,
        deviceId: UUID,
        pkg: String,
        type: String,
        limitSeconds: Int?,
        nearPct: Int,
        criticalPct: Int,
        enabled: Boolean,
        parentId: UUID,
        version: Long,
        schedules: List<AppRuleScheduleReq>,
    ): UUID {
        val id = conn.execReturningUuid(
            """INSERT INTO app_rules(device_id,package_name,rule_type,daily_limit_seconds,near_pct,critical_pct,enabled,created_by,version)
               VALUES(?,?,?,?,?,?,?,?,?) RETURNING id""",
            deviceId, pkg, type, limitSeconds, nearPct, criticalPct, enabled, parentId, version,
        )
        replaceSchedules(conn, id, schedules)
        return id
    }

    fun updateRule(
        conn: Connection,
        ruleId: UUID,
        expectedRuleVersion: Long,
        pkg: String,
        type: String,
        limitSeconds: Int?,
        nearPct: Int,
        criticalPct: Int,
        enabled: Boolean,
        version: Long,
        schedules: List<AppRuleScheduleReq>,
    ): Boolean {
        val changed = conn.exec(
            """UPDATE app_rules SET rule_type=?,daily_limit_seconds=?,near_pct=?,critical_pct=?,enabled=?,version=?,updated_at=now()
               WHERE id=? AND package_name=? AND version=?""",
            type, limitSeconds, nearPct, criticalPct, enabled, version, ruleId, pkg, expectedRuleVersion,
        )
        if (changed == 0) return false
        replaceSchedules(conn, ruleId, schedules)
        return true
    }

    private fun replaceSchedules(conn: Connection, ruleId: UUID, schedules: List<AppRuleScheduleReq>) {
        conn.exec("DELETE FROM app_rule_schedules WHERE rule_id=?", ruleId)
        for (s in schedules) {
            for (day in 1..7) {
                if ((s.daysOfWeek and (1 shl (day - 1))) != 0) {
                    conn.exec(
                        "INSERT INTO app_rule_schedules(rule_id,day_of_week,start_time,end_time,enabled) VALUES(?,?,?::time,?::time,?)",
                        ruleId, day, s.startTime, s.endTime, s.enabled,
                    )
                }
            }
        }
    }

    fun schedules(ruleId: UUID): List<AppRuleScheduleRow> =
        db.query("SELECT * FROM app_rule_schedules WHERE rule_id=? ORDER BY day_of_week,start_time", ruleId) { it.appRuleScheduleRow() }

    fun deleteRule(conn: Connection, deviceId: UUID, ruleId: UUID, expectedVersion: Long): Boolean =
        conn.exec("DELETE FROM app_rules WHERE device_id=? AND id=? AND version=?", deviceId, ruleId, expectedVersion) > 0

    fun usageTodaySeconds(deviceId: UUID, pkg: String, day: LocalDate): Int? =
        db.one(
            "SELECT (SUM(usage_minutes)::INT * 60) AS s FROM app_usage_daily WHERE device_id=? AND package_name=? AND usage_date=?",
            deviceId, pkg, day,
        ) { it.intN("s") }

    fun usageWeekSeconds(deviceId: UUID, pkg: String, from: LocalDate, to: LocalDate): Int? =
        db.one(
            "SELECT (SUM(usage_minutes)::INT * 60) AS s FROM app_usage_daily WHERE device_id=? AND package_name=? AND usage_date BETWEEN ? AND ?",
            deviceId, pkg, from, to,
        ) { it.intN("s") }

    fun usageHistory(deviceId: UUID, pkg: String, from: LocalDate, to: LocalDate): List<AppUsageHistoryDayOut> =
        db.query(
            """SELECT usage_date,COALESCE(SUM(usage_minutes),0)::INT * 60 AS s FROM app_usage_daily
               WHERE device_id=? AND package_name=? AND usage_date BETWEEN ? AND ? GROUP BY usage_date ORDER BY usage_date""",
            deviceId, pkg, from, to,
        ) { AppUsageHistoryDayOut(it.getObject("usage_date", LocalDate::class.java).toString(), it.getInt("s")) }

    fun updateEnforcementStatus(deviceId: UUID, pkg: String, status: String, at: OffsetDateTime) {
        db.exec(
            "UPDATE app_rules SET last_enforcement_status=?,last_enforcement_at=?,updated_at=now() WHERE device_id=? AND package_name=?",
            status, at, deviceId, pkg,
        )
    }

    fun insertRestrictionEvent(
        conn: Connection,
        deviceId: UUID,
        ruleId: UUID?,
        pkg: String,
        type: String,
        ts: OffsetDateTime,
        metadata: String,
        dedupeKey: String,
    ): UUID? = conn.one(
        """INSERT INTO app_restriction_events(device_id,rule_id,package_name,event_type,ts,metadata,dedupe_key)
           VALUES(?,?,?,?,?,?,?) ON CONFLICT(dedupe_key) DO NOTHING RETURNING id""",
        deviceId, ruleId, pkg, type, ts, metadata, dedupeKey,
    ) { it.uuid("id") }

    fun restrictionEvents(deviceId: UUID, limit: Int): List<AppRestrictionEventRow> =
        db.query(
            "SELECT * FROM app_restriction_events WHERE device_id=? ORDER BY ts DESC LIMIT ?",
            deviceId, limit,
        ) { AppRestrictionEventRow(it.uuid("id"), it.uuid("device_id"), it.getString("package_name"), it.getString("event_type"), it.odt("ts"), it.getString("metadata")) }

    fun maxVersion(deviceId: UUID): Long = state(deviceId)?.ruleVersion ?: 0L

}

private fun java.sql.ResultSet.appInventoryRow() = AppInventoryRow(
    uuid("device_id"), getString("package_name"), getString("app_name"), getString("version_name"), getLong("version_code"),
    odtN("installed_at"), getBoolean("is_system_app"), getString("category"), getBoolean("installed"), odt("updated_at"),
)
private fun java.sql.ResultSet.appRuleRow() = AppRuleRow(
    uuid("id"), uuid("device_id"), getString("package_name"), getString("rule_type"), intN("daily_limit_seconds"),
    getInt("near_pct"), getInt("critical_pct"), getBoolean("enabled"), uuid("created_by"), getLong("version"),
    getString("last_enforcement_status"), odtN("last_enforcement_at"), odt("created_at"), odt("updated_at"),
)
private fun java.sql.ResultSet.appRuleScheduleRow() = AppRuleScheduleRow(
    uuid("id"), uuid("rule_id"), getInt("day_of_week"), getObject("start_time").toString(), getObject("end_time").toString(), getBoolean("enabled"),
)
private fun java.sql.ResultSet.appManagementStateRow() = AppManagementStateRow(
    uuid("device_id"), getLong("rule_version"), getString("management_mode"), getBoolean("usage_access_granted"),
    getString("timezone_id"), odtN("inventory_updated_at"), odtN("last_sync_at"),
)
