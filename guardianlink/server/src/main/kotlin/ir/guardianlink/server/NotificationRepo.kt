package ir.guardianlink.server

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

private data class NotificationClass(val category: String, val severity: String, val title: String, val body: String)

/** Notification projection is intentionally separate from the immutable device event/audit stream. */
class NotificationRepo(private val db: Db) {
    fun project(conn: java.sql.Connection, eventId: UUID, deviceId: UUID, type: String, metadata: String, at: OffsetDateTime) {
        if (type == "WEB_PROTECTION_CONFIG_CHANGED") {
            conn.exec("UPDATE family_notifications SET status='RESOLVED',resolved_at=COALESCE(resolved_at,now()) WHERE device_id=? AND event_type='WEB_PROTECTION_DISABLED' AND status<>'RESOLVED'",deviceId)
            return
        }
        val cls = classify(type) ?: return
        if (type in setOf("GEOFENCE_ENTER","GEOFENCE_EXIT") && !Regex("\\\"notify\\\"\\s*:\\s*true").containsMatchIn(metadata)) return
        val familyId = conn.one("SELECT family_id FROM devices WHERE id=? AND status='active'", deviceId) { it.uuid("family_id") } ?: return
        conn.exec(
            """INSERT INTO family_notifications(family_id,device_id,event_id,event_type,category,severity,title_key,body_key,metadata,occurred_at)
               VALUES(?,?,?,?,?,?,?, ?,?::jsonb,?) ON CONFLICT(event_id) DO NOTHING""",
            familyId, deviceId, eventId, type, cls.category, cls.severity, cls.title, cls.body,
            metadata.ifBlank { "{}" }, at,
        )
        // App restriction recovery is package-scoped; never clear another app's alert.
        if (type == "APP_RESTRICTION_ENFORCED") {
            conn.exec("UPDATE family_notifications SET status='RESOLVED',resolved_at=now() WHERE device_id=? AND event_type IN ('APP_RESTRICTION_FAILED','APP_RESTRICTION_UNSUPPORTED') AND metadata->>'package_name'=(?::jsonb->>'package_name') AND status<>'RESOLVED'",deviceId,metadata)
        }
        // Recovery/healthy transitions resolve only the corresponding open alert for this device.
        val resolved = when (type) {
            "BATTERY_RECOVERED" -> listOf("BATTERY_LOW", "BATTERY_CRITICAL")
            "DEVICE_ONLINE", "DEVICE_CONNECTED" -> listOf("DEVICE_OFFLINE", "DEVICE_DISCONNECTED")
            "SYNC_RECOVERED" -> listOf("SYNC_DELAYED")
            "LOCATION_PERMISSION_GRANTED" -> listOf("LOCATION_PERMISSION_REVOKED", "LOCATION_DISABLED")
            "APP_RESTRICTION_ENFORCED" -> emptyList()
            else -> emptyList()
        }
        if (resolved.isNotEmpty()) conn.exec(
            "UPDATE family_notifications SET status='RESOLVED',resolved_at=now() WHERE device_id=? AND event_type=ANY(?) AND status<>'RESOLVED'",
            deviceId, resolved.toTypedArray(),
        )
    }

    fun list(
        userId: UUID, familyId: UUID, deviceId: UUID?, category: String?, severity: String?, unreadOnly: Boolean,
        from: LocalDate?, to: LocalDate?, search: String?, before: OffsetDateTime?, beforeId: UUID?, limit: Int,
    ): List<NotificationRow> {
        val zoneId = db.one("SELECT timezone FROM notification_preferences WHERE family_id=?",familyId) { it.getString("timezone") }
            ?.let { runCatching { java.time.ZoneId.of(it) }.getOrDefault(java.time.ZoneId.of("UTC")) }
            ?: java.time.ZoneId.of("UTC")
        val fromAt = from?.atStartOfDay(zoneId)?.toOffsetDateTime()
        val toExclusive = to?.plusDays(1)?.atStartOfDay(zoneId)?.toOffsetDateTime()
        val sql = """SELECT n.id,n.device_id,n.event_id,n.event_type,n.category,n.severity,n.title_key,n.body_key,
              n.metadata::text AS metadata,n.occurred_at,n.status,n.acknowledged_at,n.resolved_at,
              (s.read_at IS NOT NULL) AS is_read,d.device_name
            FROM family_notifications n JOIN devices d ON d.id=n.device_id
            LEFT JOIN notification_user_state s ON s.notification_id=n.id AND s.user_id=?
            WHERE n.family_id=? AND d.status='active'
              AND (?::uuid IS NULL OR n.device_id=?) AND (?::text IS NULL OR n.category=?) AND (?::text IS NULL OR n.severity=?)
              AND (NOT ? OR s.read_at IS NULL)
              AND (?::timestamptz IS NULL OR n.occurred_at >= ?)
              AND (?::timestamptz IS NULL OR n.occurred_at < ?)
              AND (?::text IS NULL OR (n.event_type ILIKE ('%'||?||'%') OR COALESCE(n.metadata->>'package_name',n.metadata->>'zone',n.metadata->>'reason_code',n.metadata->>'permission','') ILIKE ('%'||?||'%') OR d.device_name ILIKE ('%'||?||'%')))
              AND (?::timestamptz IS NULL OR (n.occurred_at,n.id) < (?,?::uuid))
            ORDER BY n.occurred_at DESC,n.id DESC LIMIT ?"""
        return db.query(sql,userId,familyId,deviceId,deviceId,category,category,severity,severity,unreadOnly,
            fromAt,fromAt,toExclusive,toExclusive,search,search,search,search,before,before,beforeId,limit) { it.notificationRow() }
    }

    fun detail(userId: UUID, familyId: UUID, id: UUID): NotificationRow? = db.one(
        """SELECT n.id,n.device_id,n.event_id,n.event_type,n.category,n.severity,n.title_key,n.body_key,n.metadata::text AS metadata,
           n.occurred_at,n.status,n.acknowledged_at,n.resolved_at,(s.read_at IS NOT NULL) AS is_read,d.device_name
           FROM family_notifications n JOIN devices d ON d.id=n.device_id
           LEFT JOIN notification_user_state s ON s.notification_id=n.id AND s.user_id=?
           WHERE n.id=? AND n.family_id=? AND d.status='active'""", userId,id,familyId,
    ) { it.notificationRow() }

    fun unreadCount(userId: UUID, familyId: UUID): Int = db.one(
        """SELECT count(*) AS n FROM family_notifications n JOIN devices d ON d.id=n.device_id
           LEFT JOIN notification_user_state s ON s.notification_id=n.id AND s.user_id=?
           WHERE n.family_id=? AND d.status='active' AND s.read_at IS NULL""",userId,familyId,
    ) { it.getInt("n") } ?: 0

    fun markRead(userId: UUID, familyId: UUID, id: UUID): Boolean = db.tx { c ->
        val exists = c.one("SELECT 1 AS n FROM family_notifications n JOIN devices d ON d.id=n.device_id WHERE n.id=? AND n.family_id=? AND d.status='active'",id,familyId) { it.getInt("n") } != null
        if (exists) c.exec("INSERT INTO notification_user_state(notification_id,user_id,read_at) VALUES(?,?,now()) ON CONFLICT(notification_id,user_id) DO UPDATE SET read_at=COALESCE(notification_user_state.read_at,now())",id,userId)
        exists
    }

    fun markAllRead(userId: UUID, familyId: UUID) = db.tx { c ->
        c.exec("""INSERT INTO notification_user_state(notification_id,user_id,read_at)
          SELECT n.id,?,now() FROM family_notifications n JOIN devices d ON d.id=n.device_id
          LEFT JOIN notification_user_state s ON s.notification_id=n.id AND s.user_id=?
          WHERE n.family_id=? AND d.status='active' AND s.read_at IS NULL
          ON CONFLICT(notification_id,user_id) DO NOTHING""",userId,userId,familyId)
    }

    fun transition(userId: UUID, familyId: UUID, id: UUID, status: String): Boolean = db.tx { c ->
        val changed = c.exec("""UPDATE family_notifications n SET status=?,
             acknowledged_at=CASE WHEN ?='ACKNOWLEDGED' THEN COALESCE(n.acknowledged_at,now()) ELSE n.acknowledged_at END,
             resolved_at=CASE WHEN ?='RESOLVED' THEN COALESCE(n.resolved_at,now()) ELSE n.resolved_at END
             FROM devices d WHERE n.device_id=d.id AND n.id=? AND n.family_id=? AND d.status='active'""",status,status,status,id,familyId)
        if (changed > 0) c.exec("INSERT INTO notification_user_state(notification_id,user_id,read_at) VALUES(?,?,now()) ON CONFLICT(notification_id,user_id) DO UPDATE SET read_at=COALESCE(notification_user_state.read_at,now())",id,userId)
        changed > 0
    }

    fun preferences(userId: UUID,familyId: UUID): NotificationPreferences {
        ensurePreferences(familyId)
        return db.one("SELECT enabled_categories::text AS categories,quiet_hours_enabled,quiet_start,quiet_end,timezone,critical_during_quiet_hours,daily_summary_enabled,cooldown_minutes FROM notification_preferences WHERE family_id=?",familyId) {
            NotificationPreferences(it.getString("categories"),it.getBoolean("quiet_hours_enabled"),it.getObject("quiet_start",LocalTime::class.java).toString(),it.getObject("quiet_end",LocalTime::class.java).toString(),it.getString("timezone"),it.getBoolean("critical_during_quiet_hours"),it.getBoolean("daily_summary_enabled"),it.getInt("cooldown_minutes"))
        } ?: NotificationPreferences("{}",false,"22:00","07:00","UTC",true,false,10)
    }

    fun savePreferences(familyId: UUID,p: NotificationPreferences): NotificationPreferences {
        require(p.enabledCategories.length <= 2000)
        require(p.cooldownMinutes in 0..240)
        val start=LocalTime.parse(p.quietStart); val end=LocalTime.parse(p.quietEnd)
        require(java.time.ZoneId.getAvailableZoneIds().contains(p.timezone))
        db.tx { c -> c.exec("""INSERT INTO notification_preferences(family_id,enabled_categories,quiet_hours_enabled,quiet_start,quiet_end,timezone,critical_during_quiet_hours,daily_summary_enabled,cooldown_minutes,updated_at)
          VALUES(?,?::jsonb,?,?,?,?,?,?,?,now()) ON CONFLICT(family_id) DO UPDATE SET enabled_categories=EXCLUDED.enabled_categories,quiet_hours_enabled=EXCLUDED.quiet_hours_enabled,quiet_start=EXCLUDED.quiet_start,quiet_end=EXCLUDED.quiet_end,timezone=EXCLUDED.timezone,critical_during_quiet_hours=EXCLUDED.critical_during_quiet_hours,daily_summary_enabled=EXCLUDED.daily_summary_enabled,cooldown_minutes=EXCLUDED.cooldown_minutes,updated_at=now()""",familyId,p.enabledCategories,p.quietHoursEnabled,start,end,p.timezone,p.criticalDuringQuietHours,p.dailySummaryEnabled,p.cooldownMinutes) }
        return preferences(UUID(0,0),familyId)
    }

    private fun ensurePreferences(familyId: UUID) { db.tx { it.exec("INSERT INTO notification_preferences(family_id) VALUES(?) ON CONFLICT DO NOTHING",familyId) } }

    private fun classify(t: String): NotificationClass? {
        val pair = when (t) {
            "BATTERY_LOW" -> "BATTERY" to "WARNING"
            "BATTERY_CRITICAL" -> "BATTERY" to "CRITICAL"
            "BATTERY_RECOVERED" -> "BATTERY" to "INFO"
            "DEVICE_OFFLINE" -> "CONNECTIVITY" to "CRITICAL"
            "DEVICE_ONLINE","DEVICE_CONNECTED","DEVICE_DISCONNECTED" -> "CONNECTIVITY" to if (t=="DEVICE_DISCONNECTED") "WARNING" else "INFO"
            "SYNC_DELAYED" -> "CONNECTIVITY" to "WARNING"
            "SYNC_RECOVERED" -> "CONNECTIVITY" to "INFO"
            "LOCATION_PERMISSION_REVOKED","LOCATION_DISABLED","LOCATION_UNAVAILABLE","LOCATION_SYNC_DELAYED" -> "LOCATION" to "WARNING"
            "LOCATION_PERMISSION_GRANTED" -> "LOCATION" to "INFO"
            "GEOFENCE_ENTER","GEOFENCE_EXIT" -> "GEOFENCE" to "INFO"
            "GEOFENCE_ERROR" -> "GEOFENCE" to "WARNING"
            "APP_USAGE_LIMIT_NEAR","SCREEN_TIME_LIMIT_NEAR","SCHEDULE_STARTED","SCHEDULE_ENDED" -> "APP_USAGE" to "INFO"
            "APP_USAGE_LIMIT_CRITICAL","APP_USAGE_LIMIT_REACHED","SCREEN_TIME_LIMIT_REACHED","USAGE_ACCESS_REVOKED","USAGE_REVOKED" -> "APP_USAGE" to "WARNING"
            "APP_RESTRICTION_FAILED","APP_RESTRICTION_UNSUPPORTED" -> "APP_USAGE" to "WARNING"
            "APP_RESTRICTION_ENFORCED" -> "APP_USAGE" to "INFO"
            "WEB_PROTECTION_DISABLED" -> "WEB_SAFETY" to "CRITICAL"
            "WEB_FILTERING_ERROR","WEB_FILTERING_UNAVAILABLE" -> "WEB_SAFETY" to "WARNING"
            "PERMISSION_CHANGED" -> "PERMISSION" to "WARNING"
            "SOS_ACTIVATED" -> "EMERGENCY" to "CRITICAL"
            "SOS_ACTIVATION_FAILED","SOS_BATTERY_CRITICAL" -> "EMERGENCY" to "CRITICAL"
            "SOS_LOCATION_UNAVAILABLE","SOS_NETWORK_LOST" -> "EMERGENCY" to "WARNING"
            "SOS_ACKNOWLEDGED","SOS_RESOLVED","SOS_CANCELLED","SOS_NETWORK_RESTORED","SOS_EXPIRED" -> "EMERGENCY" to "INFO"
            else -> return null
        }
        return NotificationClass(pair.first,pair.second,t.lowercase()+".title",t.lowercase()+".body")
    }
}

data class NotificationRow(val id: UUID,val deviceId: UUID,val eventId: UUID,val eventType: String,val category: String,val severity: String,val titleKey: String,val bodyKey: String,val metadata: String,val occurredAt: OffsetDateTime,val status: String,val acknowledgedAt: OffsetDateTime?,val resolvedAt: OffsetDateTime?,val isRead: Boolean,val deviceName: String)
data class NotificationPreferences(val enabledCategories: String,val quietHoursEnabled: Boolean,val quietStart: String,val quietEnd: String,val timezone: String,val criticalDuringQuietHours: Boolean,val dailySummaryEnabled: Boolean,val cooldownMinutes: Int = 10)
private fun java.sql.ResultSet.notificationRow()=NotificationRow(uuid("id"),uuid("device_id"),uuid("event_id"),getString("event_type"),getString("category"),getString("severity"),getString("title_key"),getString("body_key"),getString("metadata"),odt("occurred_at"),getString("status"),odtN("acknowledged_at"),odtN("resolved_at"),getBoolean("is_read"),getString("device_name"))
