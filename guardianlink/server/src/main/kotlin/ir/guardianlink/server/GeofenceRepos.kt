package ir.guardianlink.server

import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID

class GeofenceRepo(private val db: Db) {
    // ---------- zones ----------
    fun zones(deviceId: UUID): List<GeofenceRow> =
        db.query("SELECT * FROM geofences WHERE device_id=? ORDER BY created_at", deviceId) { it.zoneRow() }

    fun zone(id: UUID): GeofenceRow? =
        db.one("SELECT * FROM geofences WHERE id=?", id) { it.zoneRow() }

    fun zoneByName(deviceId: UUID, name: String): GeofenceRow? =
        db.one("SELECT * FROM geofences WHERE device_id=? AND lower(name)=lower(?)", deviceId, name) { it.zoneRow() }

    fun countZones(deviceId: UUID): Int =
        db.one("SELECT COUNT(*)::INT AS c FROM geofences WHERE device_id=?", deviceId) { it.getInt("c") } ?: 0

    fun insertZone(
        deviceId: UUID,
        name: String,
        lat: Double,
        lon: Double,
        radiusM: Int,
        enabled: Boolean,
        notifyOnEntry: Boolean,
        notifyOnExit: Boolean,
    ): UUID = db.tx { c ->
        val id = c.execReturningUuid(
            "INSERT INTO geofences(device_id,name,latitude,longitude,radius_m,enabled) VALUES(?,?,?,?,?,?) RETURNING id",
            deviceId, name, lat, lon, radiusM, enabled,
        )
        c.exec(
            "INSERT INTO geofence_states(geofence_id,state) VALUES(?,'unknown')",
            id,
        )
        c.exec(
            "INSERT INTO geofence_notification_settings(geofence_id,notify_on_entry,notify_on_exit) VALUES(?,?,?)",
            id, notifyOnEntry, notifyOnExit,
        )
        id
    }

    fun updateZone(
        id: UUID,
        name: String,
        lat: Double,
        lon: Double,
        radiusM: Int,
        enabled: Boolean,
        notifyOnEntry: Boolean,
        notifyOnExit: Boolean,
    ) {
        db.tx { c ->
            c.exec(
                """UPDATE geofences SET name=?, latitude=?, longitude=?, radius_m=?, enabled=?, updated_at=now()
                   WHERE id=?""",
                name, lat, lon, radiusM, enabled, id,
            )
            c.exec(
                """UPDATE geofence_notification_settings
                   SET notify_on_entry=?, notify_on_exit=? WHERE geofence_id=?""",
                notifyOnEntry, notifyOnExit, id,
            )
        }
    }

    fun deleteZone(id: UUID): Int = db.exec("DELETE FROM geofences WHERE id=?", id)

    // ---------- states ----------
    fun state(zoneId: UUID): GeofenceStateRow? =
        db.one("SELECT * FROM geofence_states WHERE geofence_id=?", zoneId) { it.stateRow() }

    /** zone + state + notification settings برای همه‌ی zoneهای «فعال» یک دستگاه. */
    fun enabledZonesFull(deviceId: UUID): List<ZoneFull> {
        val zones = db.query(
            "SELECT * FROM geofences WHERE device_id=? AND enabled=true ORDER BY created_at",
            deviceId,
        ) { it.zoneRow() }
        return zones.map { z ->
            ZoneFull(
                z,
                state(z.id) ?: GeofenceStateRow(z.id, "unknown", null, null, OffsetDateTime.now()),
                notif(z.id) ?: GeofenceNotifRow(z.id, true, true, true),
            )
        }
    }

    fun resetStatesForDevice(deviceId: UUID) {
        db.exec(
            """UPDATE geofence_states SET state='unknown', last_transition_at=NULL, updated_at=now()
               WHERE geofence_id IN (SELECT id FROM geofences WHERE device_id=?)""",
            deviceId,
        )
    }

    /** گذار حالت + ثبت رویداد در یک تراکنش (اتمی ⇒ duplicate transition غیرممکن). */
    fun transition(
        zoneId: UUID,
        deviceId: UUID,
        expectedState: String,
        expectedTransitionAt: OffsetDateTime?,
        newState: String,
        eventType: String,
        ts: OffsetDateTime,
        accuracyM: Float?,
        dwellMin: Int?,
    ): UUID? = db.tx { c ->
        // compare-and-set prevents simultaneous/retried syncs from duplicating the same transition.
        c.one(
            """WITH changed AS (
                   UPDATE geofence_states SET state=?, last_transition_at=?, last_known_accuracy=?, updated_at=now()
                   WHERE geofence_id=? AND state=? AND last_transition_at IS NOT DISTINCT FROM ?
                   RETURNING geofence_id
               )
               INSERT INTO geofence_events(geofence_id,device_id,event_type,ts,accuracy_m,dwell_min)
               SELECT geofence_id, ?, ?, ?, ?, ? FROM changed RETURNING id""",
            newState, ts, accuracyM, zoneId, expectedState, expectedTransitionAt,
            deviceId, eventType, ts, accuracyM, dwellMin,
        ) { it.uuid("id") }
    }

    /** فقط به‌روزرسانی دقت/زمان (بدون گذار) — برای fixهای مبهم یا کم‌دقت. */
    fun touchAccuracy(zoneId: UUID, accuracyM: Float?) {
        db.exec(
            """UPDATE geofence_states SET last_known_accuracy=?, updated_at=now() WHERE geofence_id=?""",
            accuracyM, zoneId,
        )
    }

    /** ثبت یک گذار ERROR به‌صورت compare-and-set؛ خطاهای همزمان یک‌بار شمرده می‌شوند. */
    fun markError(zoneId: UUID, deviceId: UUID, ts: OffsetDateTime, accuracyM: Float?): UUID? = db.tx { c ->
        c.one(
            """WITH changed AS (
                   UPDATE geofence_states SET state='error', last_transition_at=?, last_known_accuracy=?, updated_at=now()
                   WHERE geofence_id=? AND state <> 'error' RETURNING geofence_id
               )
               INSERT INTO geofence_events(geofence_id,device_id,event_type,ts,accuracy_m)
               SELECT geofence_id, ?, 'GEOFENCE_ERROR', ?, ? FROM changed RETURNING id""",
            ts, accuracyM, zoneId, deviceId, ts, accuracyM,
        ) { it.uuid("id") }
    }

    /** بازنشانی حالت به unknown (بند ۱۰) — پس از تغییر هندسه یا غیرفعال→فعال. */
    fun resetState(zoneId: UUID) {
        db.exec(
            "UPDATE geofence_states SET state='unknown', last_transition_at=NULL, updated_at=now() WHERE geofence_id=?",
            zoneId,
        )
    }

    fun setStateOnly(zoneId: UUID, state: String, ts: OffsetDateTime?) {
        db.exec(
            "UPDATE geofence_states SET state=?, last_transition_at=COALESCE(?, last_transition_at), updated_at=now() WHERE geofence_id=?",
            state, ts, zoneId,
        )
    }

    // ---------- notification settings ----------
    fun notif(zoneId: UUID): GeofenceNotifRow? =
        db.one("SELECT * FROM geofence_notification_settings WHERE geofence_id=?", zoneId) {
            GeofenceNotifRow(it.uuid("geofence_id"), it.getBoolean("notify_on_entry"), it.getBoolean("notify_on_exit"), it.getBoolean("enabled"))
        }

    fun setNotifEnabled(zoneId: UUID, enabled: Boolean) {
        db.exec("UPDATE geofence_notification_settings SET enabled=? WHERE geofence_id=?", enabled, zoneId)
    }

    // ---------- events ----------
    fun insertEvent(
        zoneId: UUID,
        deviceId: UUID,
        eventType: String,
        ts: OffsetDateTime?,
        accuracyM: Float?,
        dwellMin: Int?,
    ): UUID = db.tx { c ->
        c.execReturningUuid(
            """INSERT INTO geofence_events(geofence_id,device_id,event_type,ts,accuracy_m,dwell_min)
               VALUES(?,?,?,COALESCE(?,now()),?,?) RETURNING id""",
            zoneId, deviceId, eventType, ts, accuracyM, dwellMin,
        )
    }

    fun eventsForZone(zoneId: UUID, limit: Int): List<GeofenceEventRow> =
        db.query(
            "SELECT * FROM geofence_events WHERE geofence_id=? ORDER BY ts DESC, processed_at DESC LIMIT ?",
            zoneId, limit,
        ) { it.eventRow() }

    fun eventsForDevice(
        deviceId: UUID,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
        limit: Int,
    ): List<GeofenceEventRow> =
        db.query(
            """SELECT * FROM geofence_events
               WHERE device_id=? AND (?::timestamptz IS NULL OR ts >= ?)
                 AND (?::timestamptz IS NULL OR ts <= ?)
               ORDER BY ts DESC, processed_at DESC LIMIT ?""",
            deviceId, from, from, to, to, limit,
        ) { it.eventRow() }

    /** Retention for movement history and corresponding notification feed metadata. */
    fun purgeOldEvents(deviceId: UUID, cutoff: OffsetDateTime): Int = db.tx { c ->
        val geo = c.exec("DELETE FROM geofence_events WHERE device_id=? AND ts<?", deviceId, cutoff)
        c.exec(
            """DELETE FROM device_events WHERE device_id=? AND ts<?
               AND event_type IN ('GEOFENCE_CREATED','GEOFENCE_UPDATED','GEOFENCE_DELETED','GEOFENCE_ENTER','GEOFENCE_EXIT','GEOFENCE_ERROR')""",
            deviceId, cutoff,
        )
        geo
    }

    fun transitionTimes(deviceId: UUID): Map<UUID, Pair<OffsetDateTime?, OffsetDateTime?>> =
        db.query(
            """SELECT geofence_id,
                      MAX(ts) FILTER (WHERE event_type='GEOFENCE_ENTER') AS last_entry,
                      MAX(ts) FILTER (WHERE event_type='GEOFENCE_EXIT') AS last_exit
               FROM geofence_events WHERE device_id=? GROUP BY geofence_id""",
            deviceId,
        ) { it.uuid("geofence_id") to Pair(it.odtN("last_entry"), it.odtN("last_exit")) }.toMap()

    fun lastEventForZone(zoneId: UUID): GeofenceEventRow? =
        db.one(
            """SELECT * FROM geofence_events
               WHERE geofence_id=? AND event_type IN ('GEOFENCE_ENTER','GEOFENCE_EXIT')
               ORDER BY ts DESC, processed_at DESC LIMIT 1""",
            zoneId,
        ) { it.eventRow() }
}

private fun java.sql.ResultSet.zoneRow() = GeofenceRow(
    uuid("id"), uuid("device_id"), getString("name"), getDouble("latitude"), getDouble("longitude"),
    getInt("radius_m"), getBoolean("enabled"), odt("created_at"), odt("updated_at"),
)

private fun java.sql.ResultSet.stateRow() = GeofenceStateRow(
    uuid("geofence_id"), getString("state"), odtN("last_transition_at"),
    getObject("last_known_accuracy") as Float?, odt("updated_at"),
)

private fun java.sql.ResultSet.eventRow() = GeofenceEventRow(
    uuid("id"), uuid("geofence_id"), uuid("device_id"), getString("event_type"),
    odt("ts"), getObject("accuracy_m") as Float?, intN("dwell_min"), odt("processed_at"),
)
