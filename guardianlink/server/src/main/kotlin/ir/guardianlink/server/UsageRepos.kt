package ir.guardianlink.server

import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class UsageRow(
    val packageName: String,
    val appName: String,
    val category: String,
    val usageDate: LocalDate,
    val usageMinutes: Int,
    val launchCount: Int,
    val firstUsedAt: OffsetDateTime?,
    val lastUsedAt: OffsetDateTime?,
)

data class LimitRow(val id: UUID, val deviceId: UUID, val dailyLimitMinutes: Int, val nearPct: Int, val enabled: Boolean)
data class ScheduleRow(
    val id: UUID,
    val deviceId: UUID,
    val name: String,
    val startTime: String,
    val endTime: String,
    val daysOfWeek: Int,
    val enabled: Boolean,
)
data class ScreenStateRow(val deviceId: UUID, val day: LocalDate, val limitState: String, val scheduleKeys: String)

class UsageRepo(private val db: Db) {
    fun upsertDay(
        conn: Connection,
        deviceId: UUID,
        r: UsageRow,
    ) {
        conn.exec(
            """INSERT INTO app_usage_daily(device_id,package_name,app_name,category,usage_date,usage_minutes,launch_count,first_used_at,last_used_at)
               VALUES(?,?,?,?,?,?,?,?,?)
               ON CONFLICT (device_id, package_name, usage_date) DO UPDATE SET
                 app_name=EXCLUDED.app_name,
                 category=EXCLUDED.category,
                 usage_minutes=EXCLUDED.usage_minutes,
                 launch_count=EXCLUDED.launch_count,
                 first_used_at = CASE
                   WHEN app_usage_daily.first_used_at IS NULL THEN EXCLUDED.first_used_at
                   WHEN EXCLUDED.first_used_at IS NULL THEN app_usage_daily.first_used_at
                   ELSE LEAST(app_usage_daily.first_used_at, EXCLUDED.first_used_at) END,
                 last_used_at = CASE
                   WHEN app_usage_daily.last_used_at IS NULL THEN EXCLUDED.last_used_at
                   WHEN EXCLUDED.last_used_at IS NULL THEN app_usage_daily.last_used_at
                   ELSE GREATEST(app_usage_daily.last_used_at, EXCLUDED.last_used_at) END,
                 updated_at=now()""",
            deviceId, r.packageName, r.appName, r.category, r.usageDate, r.usageMinutes, r.launchCount,
            r.firstUsedAt, r.lastUsedAt,
        )
    }

    fun dayTotal(deviceId: UUID, date: LocalDate): Int =
        db.one(
            "SELECT COALESCE(SUM(usage_minutes),0)::INT AS t FROM app_usage_daily WHERE device_id=? AND usage_date=?",
            deviceId, date,
        ) { it.getInt("t") } ?: 0

    fun appsForDay(deviceId: UUID, date: LocalDate): List<UsageRow> =
        db.query(
            """SELECT package_name,app_name,category,usage_date,usage_minutes,launch_count,first_used_at,last_used_at
               FROM app_usage_daily WHERE device_id=? AND usage_date=? ORDER BY usage_minutes DESC""",
            deviceId, date,
        ) { it.usageRow() }

    fun appWeek(deviceId: UUID, pkg: String, from: LocalDate, to: LocalDate): List<UsageRow> =
        db.query(
            """SELECT package_name,app_name,category,usage_date,usage_minutes,launch_count,first_used_at,last_used_at
               FROM app_usage_daily WHERE device_id=? AND package_name=? AND usage_date BETWEEN ? AND ?
               ORDER BY usage_date""",
            deviceId, pkg, from, to,
        ) { it.usageRow() }

    fun categoriesForDay(deviceId: UUID, date: LocalDate): List<Pair<String, Int>> =
        db.query(
            "SELECT category, COALESCE(SUM(usage_minutes),0)::INT AS t FROM app_usage_daily WHERE device_id=? AND usage_date=? GROUP BY category ORDER BY t DESC",
            deviceId, date,
        ) { it.getString("category") to it.getInt("t") }

    fun upsertIcon(conn: Connection, deviceId: UUID, pkg: String, version: Int, png: ByteArray) {
        conn.exec(
            """INSERT INTO app_icons(device_id,package_name,version_code,icon_png) VALUES(?,?,?,?)
               ON CONFLICT (device_id, package_name) DO UPDATE SET
                 version_code=EXCLUDED.version_code, icon_png=EXCLUDED.icon_png, updated_at=now()
               WHERE EXCLUDED.version_code >= app_icons.version_code""",
            deviceId, pkg, version, png,
        )
    }

    fun icon(deviceId: UUID, pkg: String): ByteArray? =
        db.one("SELECT icon_png FROM app_icons WHERE device_id=? AND package_name=?", deviceId, pkg) {
            it.getBytes("icon_png")
        }

    // ---------- limits / schedules / state ----------
    fun limit(deviceId: UUID): LimitRow? =
        db.one("SELECT * FROM screen_time_limits WHERE device_id=?", deviceId) {
            LimitRow(it.uuid("id"), it.uuid("device_id"), it.getInt("daily_limit_minutes"), it.getInt("near_pct"), it.getBoolean("enabled"))
        }

    fun upsertLimit(conn: Connection, deviceId: UUID, minutes: Int, nearPct: Int, enabled: Boolean): LimitRow {
        conn.exec(
            """INSERT INTO screen_time_limits(device_id,daily_limit_minutes,near_pct,enabled) VALUES(?,?,?,?)
               ON CONFLICT (device_id) DO UPDATE SET
                 daily_limit_minutes=EXCLUDED.daily_limit_minutes,
                 near_pct=EXCLUDED.near_pct,
                 enabled=EXCLUDED.enabled,
                 updated_at=now()""",
            deviceId, minutes, nearPct, enabled,
        )
        return conn.one("SELECT id,device_id,daily_limit_minutes,near_pct,enabled FROM screen_time_limits WHERE device_id=?", deviceId) {
            LimitRow(it.uuid("id"), it.uuid("device_id"), it.getInt("daily_limit_minutes"), it.getInt("near_pct"), it.getBoolean("enabled"))
        } ?: error("screen time settings upsert did not return a row")
    }

    fun schedules(deviceId: UUID): List<ScheduleRow> =
        db.query("SELECT * FROM screen_time_schedules WHERE device_id=? ORDER BY created_at", deviceId) { it.scheduleRow() }

    fun scheduleById(id: UUID): ScheduleRow? =
        db.one("SELECT * FROM screen_time_schedules WHERE id=?", id) { it.scheduleRow() }

    fun insertSchedule(conn: Connection, deviceId: UUID, name: String, start: String, end: String, days: Int, enabled: Boolean): UUID =
        conn.execReturningUuid(
            "INSERT INTO screen_time_schedules(device_id,name,start_time,end_time,days_of_week,enabled) VALUES(?,?,?,?,?,?) RETURNING id",
            deviceId, name, start, end, days, enabled,
        )

    fun updateSchedule(conn: Connection, id: UUID, name: String, start: String, end: String, days: Int, enabled: Boolean) {
        conn.exec(
            "UPDATE screen_time_schedules SET name=?, start_time=?, end_time=?, days_of_week=?, enabled=?, updated_at=now() WHERE id=?",
            name, start, end, days, enabled, id,
        )
    }

    fun state(deviceId: UUID, day: LocalDate): ScreenStateRow? =
        db.one("SELECT * FROM screen_time_state WHERE device_id=? AND day=?", deviceId, day) {
            ScreenStateRow(it.uuid("device_id"), it.getObject("day", LocalDate::class.java), it.getString("limit_state"), it.getString("schedule_keys"))
        }

    fun upsertState(conn: Connection, deviceId: UUID, day: LocalDate, limitState: String, scheduleKeys: String) {
        conn.exec(
            """INSERT INTO screen_time_state(device_id,day,limit_state,schedule_keys) VALUES(?,?,?,?)
               ON CONFLICT (device_id, day) DO UPDATE SET limit_state=EXCLUDED.limit_state, schedule_keys=EXCLUDED.schedule_keys""",
            deviceId, day, limitState, scheduleKeys,
        )
    }
}

private fun java.sql.ResultSet.usageRow() = UsageRow(
    getString("package_name"), getString("app_name"), getString("category"),
    getObject("usage_date", LocalDate::class.java), getInt("usage_minutes"), getInt("launch_count"),
    odtN("first_used_at"), odtN("last_used_at"),
)

private fun java.sql.ResultSet.scheduleRow() = ScheduleRow(
    uuid("id"), uuid("device_id"), getString("name"), getString("start_time"), getString("end_time"),
    getInt("days_of_week"), getBoolean("enabled"),
)
