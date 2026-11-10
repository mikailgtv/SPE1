package ir.guardianlink.server

import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID

class LocationRepo(private val db: Db) {
    /** درج دسته‌ای با ON CONFLICT DO NOTHING ⇒ بازگشت تعداد رکوردهای واقعاً جدید (dedupe). */
    fun insertPoints(conn: Connection, deviceId: UUID, points: List<LocationPointIn>): Int {
        var inserted = 0
        for (p in points) {
            inserted += conn.exec(
                """INSERT INTO locations(device_id,latitude,longitude,accuracy_m,client_ts,source)
                   VALUES(?,?,?,?,?,?)
                   ON CONFLICT (device_id, client_ts) DO NOTHING""",
                deviceId, p.latitude, p.longitude, p.accuracyM ?: -1f,
                OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(p.clientTs), java.time.ZoneOffset.UTC),
                (p.source ?: "fused").take(20),
            )
        }
        return inserted
    }

    fun latestFix(deviceId: UUID): LocationRow? =
        db.one(
            """SELECT * FROM locations WHERE device_id=? ORDER BY client_ts DESC LIMIT 1""",
            deviceId,
        ) { it.locationRow() }

    fun history(deviceId: UUID, from: OffsetDateTime?, to: OffsetDateTime?, limit: Int): List<LocationRow> =
        db.query(
            """SELECT * FROM locations
               WHERE device_id=? AND (?::timestamptz IS NULL OR client_ts >= ?)
                 AND (?::timestamptz IS NULL OR client_ts <= ?)
               ORDER BY client_ts DESC LIMIT ?""",
            deviceId, from, from, to, to, limit,
        ) { it.locationRow() }

    fun countAll(deviceId: UUID): Int =
        db.one("SELECT COUNT(*)::INT AS c FROM locations WHERE device_id=?", deviceId) { it.getInt("c") } ?: 0

    fun deleteRange(deviceId: UUID, from: OffsetDateTime?): Int = db.tx { c -> deleteRange(c, deviceId, from) }

    fun deleteRange(conn: Connection, deviceId: UUID, from: OffsetDateTime?): Int =
        if (from == null) {
            conn.exec("DELETE FROM locations WHERE device_id=?", deviceId)
        } else {
            conn.exec("DELETE FROM locations WHERE device_id=? AND client_ts>=?", deviceId, from)
        }

    fun purgeOlderThan(deviceId: UUID, cutoff: OffsetDateTime): Int =
        db.exec("DELETE FROM locations WHERE device_id=? AND client_ts<?", deviceId, cutoff)

    /** retention=0 ⇒ فقط آخرین موقعیت نگه داشته می‌شود (بدون تاریخچه). */
    fun keepOnlyLatest(deviceId: UUID): Int =
        db.exec(
            """DELETE FROM locations WHERE device_id=? AND id <> COALESCE(
                 (SELECT id FROM locations WHERE device_id=? ORDER BY client_ts DESC LIMIT 1), id)""",
            deviceId, deviceId,
        )

    // ---------- settings ----------
    fun settings(deviceId: UUID): LocationSettingsRow? =
        db.one("SELECT * FROM location_settings WHERE device_id=?", deviceId) {
            LocationSettingsRow(it.uuid("device_id"), it.getBoolean("sharing_enabled"), it.getInt("retention_days"))
        }

    fun upsertSettings(conn: Connection, deviceId: UUID, sharing: Boolean, retentionDays: Int): LocationSettingsRow {
        conn.exec(
            """INSERT INTO location_settings(device_id,sharing_enabled,retention_days) VALUES(?,?,?)
               ON CONFLICT (device_id) DO UPDATE SET sharing_enabled=EXCLUDED.sharing_enabled,
                 retention_days=EXCLUDED.retention_days, updated_at=now()""",
            deviceId, sharing, retentionDays,
        )
        return LocationSettingsRow(deviceId, sharing, retentionDays)
    }

    // ---------- status ----------
    fun status(deviceId: UUID): LocationStatusRow? =
        db.one("SELECT * FROM location_status WHERE device_id=?", deviceId) { it.locationStatusRow() }

    fun upsertStatus(
        conn: Connection,
        deviceId: UUID,
        permission: String,
        provider: String,
        lastFixTs: OffsetDateTime?,
        health: String,
        reportNow: Boolean = true,
    ) {
        conn.exec(
            """INSERT INTO location_status(device_id,permission_status,provider_status,last_fix_ts,last_report_ts,health_state)
               VALUES(?,?,?,?,CASE WHEN ? THEN now() ELSE NULL END,?)
               ON CONFLICT (device_id) DO UPDATE SET
                 permission_status=EXCLUDED.permission_status,
                 provider_status=EXCLUDED.provider_status,
                 last_fix_ts=GREATEST(
                   COALESCE(location_status.last_fix_ts, EXCLUDED.last_fix_ts),
                   COALESCE(EXCLUDED.last_fix_ts, location_status.last_fix_ts)),
                 last_report_ts=CASE WHEN ? THEN now() ELSE location_status.last_report_ts END,
                 health_state=EXCLUDED.health_state,
                 updated_at=now()""",
            deviceId, permission, provider, lastFixTs, reportNow, health, reportNow,
        )
    }
}

private fun java.sql.ResultSet.locationRow() = LocationRow(
    uuid("id"), uuid("device_id"), getDouble("latitude"), getDouble("longitude"),
    getFloat("accuracy_m"), odt("client_ts"), odt("server_ts"), getString("source"),
)

private fun java.sql.ResultSet.locationStatusRow() = LocationStatusRow(
    uuid("device_id"), getString("permission_status"), getString("provider_status"),
    odtN("last_fix_ts"), odtN("last_report_ts"), getString("health_state"),
)
