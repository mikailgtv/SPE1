package ir.guardianlink.server

import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID

data class DeviceStatusRow(
    val deviceId: UUID,
    val batteryLevel: Int?,
    val batteryBand: String,
    val connectionState: String,
    val healthState: String,
    val lastHeartbeat: OffsetDateTime?,
    val lastSync: OffsetDateTime?,
    val appVersion: String,
    val androidVersion: String,
    val model: String,
    val manufacturer: String,
    val permissions: String,
    val updatedAt: OffsetDateTime,
)

data class DeviceEventRow(
    val id: UUID,
    val deviceId: UUID,
    val eventType: String,
    val ts: OffsetDateTime,
    val metadata: String,
)

data class FamilySettingsRow(
    val familyId: UUID,
    val batteryLowPct: Int,
    val batteryCriticalPct: Int,
    val syncDelayedSec: Int,
    val offlineSec: Int,
    val heartbeatIntervalSec: Int,
)

class DeviceStatusRepo(private val db: Db) {
    fun byDevice(deviceId: UUID): DeviceStatusRow? =
        db.one("SELECT * FROM device_status WHERE device_id=?", deviceId) { it.statusRow() }

    fun upsertHeartbeat(conn: Connection, deviceId: UUID, battery: Int?, band: String, connState: String, health: String) {
        conn.exec(
            """INSERT INTO device_status(device_id,battery_level,battery_band,connection_state,health_state,last_heartbeat)
               VALUES(?,?,?,?,?,now())
               ON CONFLICT(device_id) DO UPDATE SET
                 battery_level=COALESCE(EXCLUDED.battery_level, device_status.battery_level),
                 battery_band=EXCLUDED.battery_band,
                 connection_state=EXCLUDED.connection_state,
                 health_state=EXCLUDED.health_state,
                 last_heartbeat=now(),
                 updated_at=now()""",
            deviceId, battery, band, connState, health,
        )
    }

    fun upsertSnapshot(
        conn: Connection,
        deviceId: UUID,
        appVersion: String?,
        androidVersion: String?,
        model: String?,
        manufacturer: String?,
        permissions: String?,
        withSync: Boolean,
    ) {
        val syncColumn = if (withSync) "last_sync," else ""
        val syncValue = if (withSync) "now()," else ""
        val syncUpdate = if (withSync) "last_sync=now()," else ""
        conn.exec(
            """INSERT INTO device_status(device_id,app_version,android_version,model,manufacturer,permissions,$syncColumn updated_at)
               VALUES(?,?,?,?,?,?,$syncValue now())
               ON CONFLICT(device_id) DO UPDATE SET
                 app_version=COALESCE(NULLIF(EXCLUDED.app_version,''), device_status.app_version),
                 android_version=COALESCE(NULLIF(EXCLUDED.android_version,''), device_status.android_version),
                 model=COALESCE(NULLIF(EXCLUDED.model,''), device_status.model),
                 manufacturer=COALESCE(NULLIF(EXCLUDED.manufacturer,''), device_status.manufacturer),
                 permissions=COALESCE(NULLIF(EXCLUDED.permissions,'{}'), device_status.permissions),
                 $syncUpdate
                 updated_at=now()""",
            deviceId, appVersion.orEmpty(), androidVersion.orEmpty(), model.orEmpty(), manufacturer.orEmpty(), permissions ?: "{}",
        )
    }

    fun setHealth(conn: Connection, deviceId: UUID, health: String) {
        conn.exec(
            "UPDATE device_status SET health_state=?, updated_at=now() WHERE device_id=?",
            health, deviceId,
        )
    }

    fun setBand(conn: Connection, deviceId: UUID, band: String) {
        conn.exec(
            "UPDATE device_status SET battery_band=?, updated_at=now() WHERE device_id=?",
            band, deviceId,
        )
    }
}

private fun java.sql.ResultSet.statusRow() = DeviceStatusRow(
    uuid("device_id"), intN("battery_level"), getString("battery_band"), getString("connection_state"),
    getString("health_state"), odtN("last_heartbeat"), odtN("last_sync"), getString("app_version"),
    getString("android_version"), getString("model"), getString("manufacturer"), getString("permissions"),
    odt("updated_at"),
)

class DeviceEventRepo(private val db: Db) {
    /** درج رویداد با dedupe؛ اگر dedupe_key تکراری باشد بی‌صدا رد می‌شود. */
    fun emit(conn: Connection, deviceId: UUID, type: String, metadataJson: String, dedupeKey: String?): Boolean {
        // INSERT ... ON CONFLICT makes de-duplication atomic under concurrent emitters.
        val inserted = conn.one(
            "INSERT INTO device_events(device_id,event_type,metadata,dedupe_key) VALUES(?,?,?,?) ON CONFLICT(dedupe_key) DO NOTHING RETURNING id,ts",
            deviceId, type, metadataJson, dedupeKey,
        ) { it.uuid("id") to it.odt("ts") } ?: return false
        NotificationRepo(db).project(conn, inserted.first, deviceId, type, metadataJson, inserted.second)
        return true
    }

    fun recent(deviceId: UUID, limit: Int): List<DeviceEventRow> =
        db.query(
            "SELECT id,device_id,event_type,ts,metadata FROM device_events WHERE device_id=? ORDER BY ts DESC LIMIT ?",
            deviceId, limit,
        ) { DeviceEventRow(it.uuid("id"), it.uuid("device_id"), it.getString("event_type"), it.odt("ts"), it.getString("metadata")) }

    fun recentForFamily(familyId: UUID, limit: Int): List<DeviceEventRow> =
        db.query(
            """SELECT e.id,e.device_id,e.event_type,e.ts,e.metadata FROM device_events e
               JOIN devices d ON d.id=e.device_id
               WHERE d.family_id=? AND d.status='active' ORDER BY e.ts DESC LIMIT ?""",
            familyId, limit,
        ) { DeviceEventRow(it.uuid("id"), it.uuid("device_id"), it.getString("event_type"), it.odt("ts"), it.getString("metadata")) }
}

class FamilySettingsRepo(private val db: Db) {
    fun get(familyId: UUID, defaults: AppConfig): FamilySettingsRow =
        db.one("SELECT * FROM family_settings WHERE family_id=?", familyId) {
            FamilySettingsRow(
                it.uuid("family_id"), it.getInt("battery_low_pct"), it.getInt("battery_critical_pct"),
                it.getInt("sync_delayed_sec"), it.getInt("offline_sec"), it.getInt("heartbeat_interval_sec"),
            )
        } ?: FamilySettingsRow(
            familyId, defaults.defaultBatteryLowPct, defaults.defaultBatteryCriticalPct,
            defaults.defaultSyncDelayedSec, defaults.defaultOfflineSec, defaults.defaultHeartbeatIntervalSec,
        )

    fun upsert(conn: Connection, s: FamilySettingsRow) {
        conn.exec(
            """INSERT INTO family_settings(family_id,battery_low_pct,battery_critical_pct,sync_delayed_sec,offline_sec,heartbeat_interval_sec,updated_at)
               VALUES(?,?,?,?,?,?,now())
               ON CONFLICT(family_id) DO UPDATE SET
                 battery_low_pct=EXCLUDED.battery_low_pct,
                 battery_critical_pct=EXCLUDED.battery_critical_pct,
                 sync_delayed_sec=EXCLUDED.sync_delayed_sec,
                 offline_sec=EXCLUDED.offline_sec,
                 heartbeat_interval_sec=EXCLUDED.heartbeat_interval_sec,
                 updated_at=now()""",
            s.familyId, s.batteryLowPct, s.batteryCriticalPct, s.syncDelayedSec, s.offlineSec, s.heartbeatIntervalSec,
        )
    }
}
