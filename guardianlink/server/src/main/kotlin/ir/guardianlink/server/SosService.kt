package ir.guardianlink.server

import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Durable SOS workflow. Coordinates are stored only in sos_location_points, never logs/event metadata. */
class SosService(private val deps: AppDeps) {
    private val eventRepo=DeviceEventRepo(deps.db)

    fun activate(deviceId:UUID,req:SosActivateReq):SosActivateOut {
        validateActivation(req)
        var sessionId=UUID.randomUUID(); var already=false
        deps.db.tx { c ->
            c.one("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",deviceId.toString()) { 1 }
            val existingRequest=c.one("SELECT id FROM sos_sessions WHERE device_id=? AND client_request_id=?",deviceId,req.clientRequestId) { it.uuid("id") }
            if(existingRequest!=null) { sessionId=existingRequest; return@tx }
            val active=c.one("SELECT id FROM sos_sessions WHERE device_id=? AND status IN ('ACTIVATING','ACTIVE','ACKNOWLEDGED') ORDER BY activated_at DESC LIMIT 1",deviceId) { it.uuid("id") }
            if(active!=null) {
                sessionId=active; already=true
                addEvent(c,active,deviceId,"SOS_ALREADY_ACTIVE","device",null,mapOf("client_request_id" to req.clientRequestId.toString()),"already:${req.clientRequestId}")
                return@tx
            }
            val dev=c.one("SELECT family_id,status FROM devices WHERE id=?",deviceId) { it.uuid("family_id") to it.getString("status") }
                ?: throw ApiError.removed()
            if(dev.second!="active") throw ApiError.removed()
            val now=OffsetDateTime.now(ZoneOffset.UTC)
            val clientTriggeredAt=if(req.clientTriggeredAt>0) OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(req.clientTriggeredAt),ZoneOffset.UTC) else now
            val queuedDelaySec=((now.toInstant().toEpochMilli()-clientTriggeredAt.toInstant().toEpochMilli())/1000L).coerceAtLeast(0L)
            val expirationHours=c.one("SELECT auto_expiration_hours FROM sos_preferences WHERE family_id=?",dev.first) { it.getInt("auto_expiration_hours") } ?: 24
            val locationEnabled=c.one("SELECT sharing_enabled FROM location_settings WHERE device_id=?",deviceId) { it.getBoolean("sharing_enabled") } ?: true
            val last=c.one("""SELECT latitude,longitude,accuracy_m,client_ts,source FROM locations WHERE device_id=? ORDER BY client_ts DESC LIMIT 1""",deviceId) {
                SosLocationIn(it.getDouble("latitude"),it.getDouble("longitude"),it.getFloat("accuracy_m").takeIf { a -> a>=0f },it.odt("client_ts").toInstant().toEpochMilli(),it.getString("source"))
            }
            val candidate=when {
                !locationEnabled -> null
                req.initialLocation!=null && validLocation(req.initialLocation,now) -> req.initialLocation
                last!=null && validLocation(last,now) -> last
                else -> null
            }
            val trackRequested=req.trackingDurationMinutes>0
            val trackingAllowed=trackRequested && locationEnabled && req.permissionStatus=="background_granted" && req.providerStatus=="enabled"
            val trackingStatus=if(trackingAllowed) "ACTIVE" else if(trackRequested) "LIMITED" else "STOPPED"
            val reason=when {
                !trackRequested -> "not_requested"
                !locationEnabled -> "location_sharing_disabled"
req.permissionStatus!="background_granted" -> "background_permission_unavailable"
                req.providerStatus!="enabled" -> "location_provider_disabled"
                else -> null
            }
            val trackEnd=if(trackingAllowed) now.plusMinutes(req.trackingDurationMinutes.toLong()) else null
            c.exec("""INSERT INTO sos_sessions(id,family_id,device_id,client_request_id,source,status,activated_at,expires_at,
               tracking_enabled,tracking_started_at,tracking_ends_at,tracking_interval_sec,tracking_status,tracking_reason,
               battery_level,network_state,device_state,permission_status,provider_status)
               VALUES(?,?,?,?,?,'ACTIVATING',?,?, ?,?,?,?, ?,?,?, ?,?,?,?)""",
                sessionId,dev.first,deviceId,req.clientRequestId,req.source,now,now.plusHours(expirationHours.toLong()),trackingAllowed,
                if(trackingAllowed) now else null,trackEnd,req.trackingIntervalSec,trackingStatus,reason,
                req.batteryLevel,req.networkState,req.deviceState,req.permissionStatus,req.providerStatus)
            if(candidate!=null) insertPoint(c,sessionId,candidate,"activation")
            if(!SosStateMachine.transition(SosStatus.ACTIVATING,SosStatus.ACTIVE,"system")) throw ApiError.conflict("SOS_STATE_CONFLICT","activation transition rejected")
            c.exec("UPDATE sos_sessions SET status='ACTIVE',updated_at=now() WHERE id=? AND status='ACTIVATING'",sessionId)
            addEvent(c,sessionId,deviceId,"SOS_ACTIVATED","device",null,mapOf(
                "source" to req.source,"battery_level" to (req.batteryLevel?.toString() ?: "unknown"),
                "network_state" to req.networkState,"device_state" to req.deviceState,
                "location_available" to (candidate!=null).toString(),"tracking_status" to trackingStatus,
                "tracking_reason" to (reason ?: "none"),"client_triggered_at" to clientTriggeredAt.toString(),
                "queued_delay_sec" to queuedDelaySec.toString(),"delayed_sync" to (queuedDelaySec>=120).toString(),
            ),"activated:${req.clientRequestId}")
            if(candidate==null) addEvent(c,sessionId,deviceId,"SOS_LOCATION_UNAVAILABLE","system",null,mapOf("reason" to if(locationEnabled) "no_valid_fix" else "sharing_disabled"),"loc-unavailable:${req.clientRequestId}")
            if(trackingAllowed) addEvent(c,sessionId,deviceId,"SOS_TRACKING_STARTED","system",null,mapOf("interval_sec" to req.trackingIntervalSec.toString(),"duration_min" to req.trackingDurationMinutes.toString()),"tracking-start:${req.clientRequestId}")
            if((req.batteryLevel ?: 100)<15) addEvent(c,sessionId,deviceId,"SOS_BATTERY_CRITICAL","system",null,mapOf("battery_level" to req.batteryLevel.toString()),"battery-critical:${req.clientRequestId}")
            if(req.networkState=="none") addEvent(c,sessionId,deviceId,"SOS_NETWORK_LOST","device",null,mapOf("queued" to "false"),"network-lost:${req.clientRequestId}")
        }
        val session=getById(sessionId) ?: throw ApiError.notFound()
        return SosActivateOut(session,already)
    }

    fun status(deviceId:UUID,sosId:UUID):SosSessionOut { expireDue(deviceId); return session(sosId,deviceId) ?: throw ApiError.notFound() }
    fun activeForDevice(deviceId:UUID):SosSessionOut? { expireDue(deviceId); val id=deps.db.one("SELECT id FROM sos_sessions WHERE device_id=? AND status IN ('ACTIVATING','ACTIVE','ACKNOWLEDGED') ORDER BY activated_at DESC LIMIT 1",deviceId) { it.uuid("id") } ?: return null; return getById(id) }
    fun preferences(familyId:UUID):SosPreferencesOut {
        deps.db.exec("INSERT INTO sos_preferences(family_id) VALUES(?) ON CONFLICT DO NOTHING",familyId)
        return deps.db.one("SELECT auto_expiration_hours FROM sos_preferences WHERE family_id=?",familyId) { SosPreferencesOut(it.getInt("auto_expiration_hours")) } ?: SosPreferencesOut(24)
    }
    fun savePreferences(familyId:UUID,p:SosPreferencesReq):SosPreferencesOut {
        if(p.autoExpirationHours !in 1..72) throw ApiError.validation("auto_expiration_hours must be 1..72")
        deps.db.exec("INSERT INTO sos_preferences(family_id,auto_expiration_hours,updated_at) VALUES(?,?,now()) ON CONFLICT(family_id) DO UPDATE SET auto_expiration_hours=EXCLUDED.auto_expiration_hours,updated_at=now()",familyId,p.autoExpirationHours)
        return SosPreferencesOut(p.autoExpirationHours)
    }
    fun activeForFamily(familyId:UUID):List<SosSessionOut> { expireDue(familyId=familyId); return dbSessions("""SELECT id FROM sos_sessions WHERE family_id=? AND status IN ('ACTIVATING','ACTIVE','ACKNOWLEDGED') ORDER BY activated_at DESC""",familyId) }
    fun history(familyId:UUID,deviceId:UUID,limit:Int):List<SosSessionOut> {
        if(deps.devices.byId(deviceId)?.familyId!=familyId) throw ApiError.notFound()
        expireDue(deviceId)
        return dbSessions("SELECT id FROM sos_sessions WHERE family_id=? AND device_id=? ORDER BY activated_at DESC LIMIT ?",familyId,deviceId,limit.coerceIn(1,100))
    }
    fun detail(familyId:UUID,sosId:UUID):SosSessionOut? { expireDue(familyId=familyId); val row=deps.db.one("SELECT id,device_id,family_id FROM sos_sessions WHERE id=?",sosId) { Triple(it.uuid("id"),it.uuid("device_id"),it.uuid("family_id")) } ?: return null; if(row.third!=familyId) return null; return getById(row.first) }

    fun acknowledge(familyId:UUID,sosId:UUID,userId:UUID):SosSessionOut = transitionParent(familyId,sosId,userId,SosStatus.ACKNOWLEDGED,null)
    fun resolve(familyId:UUID,sosId:UUID,userId:UUID,note:String?):SosSessionOut {
        val clean=note?.trim()?.takeIf { it.isNotEmpty() }
        if(clean!=null && clean.length>1000) throw ApiError.validation("note must be <= 1000 characters")
        return transitionParent(familyId,sosId,userId,SosStatus.RESOLVED,clean)
    }
    fun cancel(deviceId:UUID,sosId:UUID,req:SosCancelReq):SosSessionOut {
        val reason=req.reason.trim(); if(reason.isEmpty() || reason.length>200) throw ApiError.validation("reason must be 1..200 chars")
        expireDue(deviceId)
        deps.db.tx { c ->
            val row=c.one("SELECT family_id,status FROM sos_sessions WHERE id=? AND device_id=? FOR UPDATE",sosId,deviceId) { it.uuid("family_id") to it.getString("status") } ?: throw ApiError.notFound()
            if(!SosStateMachine.transition(row.second,SosStatus.CANCELLED,"device")) throw ApiError.conflict("SOS_STATE_CONFLICT","SOS cannot be cancelled from ${row.second}")
            c.exec("UPDATE sos_sessions SET status='CANCELLED',cancelled_at=now(),cancelled_by_device=true,tracking_enabled=false,tracking_status='STOPPED',tracking_reason='cancelled',updated_at=now() WHERE id=?",sosId)
            addEvent(c,sosId,deviceId,"SOS_CANCELLED","device",null,mapOf("reason" to reason),"cancel:${sosId}")
            addEvent(c,sosId,deviceId,"SOS_TRACKING_STOPPED","system",null,mapOf("reason" to "cancelled"),"tracking-stop:${sosId}")
        }
        return getById(sosId)!!
    }

    private fun transitionParent(familyId:UUID,sosId:UUID,userId:UUID,to:String,note:String?):SosSessionOut {
        expireDue(familyId=familyId)
        deps.db.tx { c ->
            val row=c.one("SELECT device_id,status FROM sos_sessions WHERE id=? AND family_id=? FOR UPDATE",sosId,familyId) { it.uuid("device_id") to it.getString("status") } ?: throw ApiError.notFound()
            if(!SosStateMachine.transition(row.second,to,"parent")) throw ApiError.conflict("SOS_STATE_CONFLICT","invalid SOS transition")
            if(to==SosStatus.ACKNOWLEDGED) c.exec("UPDATE sos_sessions SET status='ACKNOWLEDGED',acknowledged_at=now(),acknowledged_by=?,updated_at=now() WHERE id=?",userId,sosId)
            else c.exec("UPDATE sos_sessions SET status='RESOLVED',resolved_at=now(),resolved_by=?,resolve_note=?,tracking_enabled=false,tracking_status='STOPPED',tracking_reason='resolved',updated_at=now() WHERE id=?",userId,note,sosId)
            addEvent(c,sosId,row.first,if(to==SosStatus.ACKNOWLEDGED) "SOS_ACKNOWLEDGED" else "SOS_RESOLVED","parent",userId,if(to==SosStatus.RESOLVED) mapOf("note_added" to (note!=null).toString()) else emptyMap(),"${to.lowercase()}:$sosId")
            if(to==SosStatus.RESOLVED) addEvent(c,sosId,row.first,"SOS_TRACKING_STOPPED","system",null,mapOf("reason" to "resolved"),"tracking-stop:$sosId")
            c.exec("""INSERT INTO notification_user_state(notification_id,user_id,read_at)
                SELECT n.id,?,now() FROM family_notifications n WHERE n.family_id=? AND n.category='EMERGENCY' AND n.metadata->>'sos_session_id'=?
                ON CONFLICT(notification_id,user_id) DO UPDATE SET read_at=COALESCE(notification_user_state.read_at,now())""",userId,familyId,sosId.toString())
        }
        return getById(sosId)!!
    }

    fun updateTracking(deviceId:UUID,sosId:UUID,req:SosTrackingReq):SosTrackingOut {
        if(req.durationMinutes !in setOf(0,15,30,60) || req.intervalSec !in setOf(30,60)) throw ApiError.validation("invalid tracking duration or interval")
        if(req.permissionStatus !in setOf("granted","background_granted","denied","unknown") || req.providerStatus !in setOf("enabled","disabled","unknown")) throw ApiError.validation("invalid location state")
        val sharing=deps.db.one("SELECT sharing_enabled FROM location_settings WHERE device_id=?",deviceId) { it.getBoolean("sharing_enabled") } ?: true
        val allowed=req.durationMinutes>0 && sharing && req.permissionStatus=="background_granted" && req.providerStatus=="enabled" && req.capabilityReason==null
        val allowedReasons=setOf("fgs_start_failure","background_permission_unavailable","provider_failure")
        if(req.capabilityReason!=null && req.capabilityReason !in allowedReasons) throw ApiError.validation("invalid capability reason")
        val reason=when { req.capabilityReason!=null -> req.capabilityReason; req.durationMinutes==0 -> "not_requested"; !sharing -> "location_sharing_disabled"; req.permissionStatus!="background_granted" -> "background_permission_unavailable"; req.providerStatus!="enabled" -> "location_provider_disabled"; else -> null }
        val ends=if(allowed) OffsetDateTime.now().plusMinutes(req.durationMinutes.toLong()) else null
        deps.db.tx { c ->
            val status=c.one("SELECT status FROM sos_sessions WHERE id=? AND device_id=? FOR UPDATE",sosId,deviceId) { it.getString("status") } ?: throw ApiError.notFound()
            if(status !in SosStatus.open) throw ApiError.conflict("SOS_STATE_CONFLICT","SOS is not active")
            c.exec("UPDATE sos_sessions SET tracking_enabled=?,tracking_started_at=CASE WHEN ? THEN now() ELSE tracking_started_at END,tracking_ends_at=?,tracking_interval_sec=?,tracking_status=?,tracking_reason=?,permission_status=?,provider_status=?,updated_at=now() WHERE id=?",allowed,allowed,ends,req.intervalSec,if(allowed) "ACTIVE" else if(req.durationMinutes==0) "STOPPED" else "LIMITED",reason,req.permissionStatus,req.providerStatus,sosId)
            addEvent(c,sosId,deviceId,if(allowed) "SOS_TRACKING_STARTED" else "SOS_TRACKING_STOPPED","device",null,mapOf("interval_sec" to req.intervalSec.toString(),"reason" to (reason ?: "none")),"tracking-update:${sosId}:${ends ?: "off"}")
        }
        val s=getById(sosId)!!; return SosTrackingOut(s.trackingStatus,s.trackingReason,s.trackingEndsAt,s.trackingIntervalSec)
    }

    fun reportNetworkState(deviceId:UUID,sosId:UUID,req:SosNetworkStateReq) {
        if(req.networkState !in setOf("wifi","cellular","ethernet","none","limited","unknown")) throw ApiError.validation("invalid network state")
        val eventTime=OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(req.occurredAt),ZoneOffset.UTC)
        if(eventTime.isBefore(OffsetDateTime.now().minusHours(24)) || eventTime.isAfter(OffsetDateTime.now().plusMinutes(5))) throw ApiError.validation("network timestamp out of range")
        deps.db.tx { c ->
            val row=c.one("SELECT network_state,status FROM sos_sessions WHERE id=? AND device_id=? FOR UPDATE",sosId,deviceId) { it.getString("network_state") to it.getString("status") } ?: throw ApiError.notFound()
            if(row.second !in SosStatus.open) throw ApiError.conflict("SOS_STATE_CONFLICT","SOS is not active")
            if(row.first==req.networkState) return@tx
            c.exec("UPDATE sos_sessions SET network_state=?,updated_at=now() WHERE id=?",req.networkState,sosId)
            val type=when { req.networkState=="none" -> "SOS_NETWORK_LOST"; row.first=="none" && req.networkState!="none" -> "SOS_NETWORK_RESTORED"; else -> null }
            if(type!=null) addEvent(c,sosId,deviceId,type,"device",null,mapOf("network_state" to req.networkState,"client_timestamp" to eventTime.toString()),"network:$sosId:${req.occurredAt}:$type")
        }
    }

    fun addLocations(deviceId:UUID,sosId:UUID,req:SosLocationBatchReq):Int {
        if(req.points.isEmpty() || req.points.size>20) throw ApiError.validation("points must be 1..20")
        val now=OffsetDateTime.now(); var inserted=0; var trackingEnded=false
        deps.db.tx { c ->
            val s=c.one("SELECT status,tracking_enabled,tracking_ends_at FROM sos_sessions WHERE id=? AND device_id=? FOR UPDATE",sosId,deviceId) { Triple(it.getString("status"),it.getBoolean("tracking_enabled"),it.odtN("tracking_ends_at")) } ?: throw ApiError.notFound()
            if(s.first !in SosStatus.open) throw ApiError.conflict("SOS_STATE_CONFLICT","SOS is not active")
            if(!s.second || s.third==null || s.third!!.isBefore(now)) {
                c.exec("UPDATE sos_sessions SET tracking_enabled=false,tracking_status='STOPPED',tracking_reason='tracking_expired',updated_at=now() WHERE id=?",sosId)
                addEvent(c,sosId,deviceId,"SOS_TRACKING_STOPPED","system",null,mapOf("reason" to "tracking_expired"),"tracking-expired:$sosId")
                trackingEnded=true
                return@tx
            }
            for(p in req.points) {
                if(!validLocation(p,now)) throw ApiError.validation("invalid or stale location point")
                val id=insertPoint(c,sosId,p,"tracking")
                if(id!=null) {
                    inserted++
                    addEvent(c,sosId,deviceId,"SOS_LOCATION_UPDATED","device",null,mapOf("accuracy_m" to (p.accuracyM?.toString() ?: "unknown"),"provider" to p.provider),"loc:$sosId:${p.clientTs}")
                }
            }
            if(inserted>0) {
                val previous=c.one("SELECT tracking_status FROM sos_sessions WHERE id=?",sosId) { it.getString(1) }
                if(previous=="LIMITED") {
                    c.exec("UPDATE sos_sessions SET tracking_status='ACTIVE',tracking_reason=NULL,updated_at=now() WHERE id=?",sosId)
                    addEvent(c,sosId,deviceId,"SOS_TRACKING_STARTED","system",null,mapOf("reason" to "location_updates_resumed"),"tracking-recovered:$sosId:${req.points.last().clientTs}")
                }
            }
        }
        if(trackingEnded) throw ApiError.conflict("SOS_TRACKING_STOPPED","tracking window ended")
        return inserted
    }

    fun locations(familyId:UUID,sosId:UUID,limit:Int):List<SosLocationOut> {
        requireFamily(familyId,sosId)
        return deps.db.query("SELECT latitude,longitude,accuracy_m,client_ts,provider,source FROM sos_location_points WHERE session_id=? ORDER BY client_ts DESC LIMIT ?",sosId,limit.coerceIn(1,500)) {
            SosLocationOut(it.getDouble("latitude"),it.getDouble("longitude"),it.getFloat("accuracy_m").takeIf { a -> !it.wasNull() },it.odt("client_ts"),it.getString("provider"),it.getString("source"))
        }
    }
    fun events(familyId:UUID,sosId:UUID):List<SosEventOut> {
        requireFamily(familyId,sosId)
        expireDue(familyId=familyId)
        return deps.db.query("SELECT id,event_type,actor_type,actor_user_id,occurred_at,metadata::text AS metadata FROM sos_events WHERE session_id=? ORDER BY occurred_at,id",sosId) {
            SosEventOut(it.uuid("id"),it.getString("event_type"),it.getString("actor_type"),it.uuidN("actor_user_id"),it.odt("occurred_at"),it.getString("metadata"))
        }
    }
    fun addNote(familyId:UUID,sosId:UUID,userId:UUID,note:String):SosNoteOut {
        val clean=note.trim(); if(clean.isEmpty() || clean.length>1000) throw ApiError.validation("note must be 1..1000 chars")
        requireFamily(familyId,sosId)
        val result=deps.db.tx { c ->
            val family=c.one("SELECT device_id FROM sos_sessions WHERE id=? AND family_id=?",sosId,familyId) { it.uuid("device_id") } ?: throw ApiError.notFound()
            val id=c.execReturningUuid("INSERT INTO sos_notes(session_id,family_id,author_user_id,note) VALUES(?,?,?,?) RETURNING id",sosId,familyId,userId,clean)
            addEvent(c,sosId,family,"SOS_NOTE_ADDED","parent",userId,mapOf("note_id" to id.toString()),"note:$id")
            c.one("SELECT id,author_user_id,note,created_at FROM sos_notes WHERE id=?",id) { SosNoteOut(it.uuid("id"),it.uuid("author_user_id"),it.getString("note"),it.odt("created_at")) }!!
        }
        return result
    }
    fun notes(familyId:UUID,sosId:UUID):List<SosNoteOut> { requireFamily(familyId,sosId); return deps.db.query("SELECT id,author_user_id,note,created_at FROM sos_notes WHERE session_id=? ORDER BY created_at",sosId) { SosNoteOut(it.uuid("id"),it.uuid("author_user_id"),it.getString("note"),it.odt("created_at")) } }

    private fun expireDue(deviceId:UUID?=null,familyId:UUID?=null) {
        deps.db.tx { c ->
            val rows=c.query("""SELECT id,device_id,status FROM sos_sessions WHERE status IN ('ACTIVATING','ACTIVE','ACKNOWLEDGED') AND expires_at<=now()
               AND (?::uuid IS NULL OR device_id=?) AND (?::uuid IS NULL OR family_id=?) FOR UPDATE""",deviceId,deviceId,familyId,familyId) { Triple(it.uuid("id"),it.uuid("device_id"),it.getString("status")) }
            for((id,device,status) in rows) {
                if(!SosStateMachine.transition(status,SosStatus.EXPIRED,"system")) continue
                c.exec("UPDATE sos_sessions SET status='EXPIRED',tracking_enabled=false,tracking_status='STOPPED',tracking_reason='expired',updated_at=now() WHERE id=?",id)
                addEvent(c,id,device,"SOS_EXPIRED","system",null,mapOf("reason" to "configured_expiration"),"expired:$id")
                addEvent(c,id,device,"SOS_TRACKING_STOPPED","system",null,mapOf("reason" to "expired"),"tracking-stop:$id")
            }
            val tracking=c.query("""SELECT id,device_id FROM sos_sessions WHERE status IN ('ACTIVE','ACKNOWLEDGED') AND tracking_enabled=true AND tracking_ends_at<=now()
                AND (?::uuid IS NULL OR device_id=?) AND (?::uuid IS NULL OR family_id=?) FOR UPDATE""",deviceId,deviceId,familyId,familyId) { it.uuid("id") to it.uuid("device_id") }
            for((id,device) in tracking) {
                c.exec("UPDATE sos_sessions SET tracking_enabled=false,tracking_status='STOPPED',tracking_reason='duration_elapsed',updated_at=now() WHERE id=?",id)
                addEvent(c,id,device,"SOS_TRACKING_STOPPED","system",null,mapOf("reason" to "duration_elapsed"),"tracking-duration-stop:$id")
            }
        }
    }

    private fun validateActivation(r:SosActivateReq) {
        if(r.clientTriggeredAt>0 && (r.clientTriggeredAt<System.currentTimeMillis()-48L*60*60*1000 || r.clientTriggeredAt>System.currentTimeMillis()+5L*60*1000)) throw ApiError.validation("invalid SOS trigger timestamp")
        if(r.source !in setOf("child_ui","hardware","other")) throw ApiError.validation("invalid SOS source")
        if(r.batteryLevel!=null && r.batteryLevel !in 0..100) throw ApiError.validation("battery out of range")
        if(r.networkState !in setOf("wifi","cellular","ethernet","none","limited","unknown")) throw ApiError.validation("invalid network state")
        if(r.permissionStatus !in setOf("granted","background_granted","denied","unknown") || r.providerStatus !in setOf("enabled","disabled","unknown")) throw ApiError.validation("invalid location state")
        if(r.trackingDurationMinutes !in setOf(0,15,30,60) || r.trackingIntervalSec !in setOf(30,60)) throw ApiError.validation("invalid tracking options")
        r.initialLocation?.let { if(!validLocation(it,OffsetDateTime.now())) throw ApiError.validation("invalid initial location") }
        if(r.deviceState.length>80 || r.deviceState.any { it.isISOControl() }) throw ApiError.validation("invalid device state")
    }
    private fun validLocation(p:SosLocationIn,now:OffsetDateTime):Boolean {
        if(p.latitude !in -90.0..90.0 || p.longitude !in -180.0..180.0) return false
        if(p.accuracyM!=null && (!p.accuracyM.isFinite() || p.accuracyM<0f || p.accuracyM>100000f)) return false
        if(p.provider !in setOf("fused","gps","network","last_known","passive")) return false
        val ts=runCatching { OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(p.clientTs),ZoneOffset.UTC) }.getOrNull() ?: return false
        return !ts.isBefore(now.minusHours(48)) && !ts.isAfter(now.plusMinutes(5))
    }
    private fun insertPoint(c:java.sql.Connection,sessionId:UUID,p:SosLocationIn,source:String):UUID? {
        return c.one("""INSERT INTO sos_location_points(session_id,latitude,longitude,accuracy_m,client_ts,provider,source)
           VALUES(?,?,?,?,?,?,?) ON CONFLICT(session_id,client_ts) DO NOTHING RETURNING id""",sessionId,p.latitude,p.longitude,p.accuracyM,OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(p.clientTs),ZoneOffset.UTC),p.provider,source) { it.uuid("id") }
    }
    private fun addEvent(c:java.sql.Connection,sessionId:UUID,deviceId:UUID,type:String,actor:String,userId:UUID?,metadata:Map<String,String>,dedupe:String) {
        val family=c.one("SELECT family_id FROM sos_sessions WHERE id=?",sessionId) { it.uuid("family_id") } ?: return
        val eventMetadata=metadata + ("sos_session_id" to sessionId.toString())
        val json=Json.write(eventMetadata)
        val inserted=c.one("INSERT INTO sos_events(session_id,family_id,device_id,event_type,actor_type,actor_user_id,metadata,dedupe_key) VALUES(?,?,?,?,?,?,?::jsonb,?) ON CONFLICT(dedupe_key) DO NOTHING RETURNING id",sessionId,family,deviceId,type,actor,userId,json,dedupe) { it.uuid("id") }
        if(inserted!=null) eventRepo.emit(c,deviceId,type,json,"sos:$dedupe")
    }
    private fun requireFamily(familyId:UUID,sosId:UUID) { if(deps.db.one("SELECT 1 AS n FROM sos_sessions WHERE id=? AND family_id=?",sosId,familyId) { it.getInt("n") }==null) throw ApiError.notFound() }
    private fun session(sosId:UUID,deviceId:UUID):SosSessionOut? = deps.db.one("SELECT id FROM sos_sessions WHERE id=? AND device_id=?",sosId,deviceId) { it.uuid("id") }?.let(::getById)
    private fun getById(id:UUID):SosSessionOut? {
        refreshStaleTracking(id)
        return deps.db.one("""SELECT s.*,d.device_name,
       p.latitude AS loc_lat,p.longitude AS loc_lon,p.accuracy_m AS loc_accuracy,p.client_ts AS loc_ts,p.provider AS loc_provider,p.source AS loc_source,
       z.name AS current_zone
       FROM sos_sessions s JOIN devices d ON d.id=s.device_id
       LEFT JOIN LATERAL (SELECT * FROM sos_location_points WHERE session_id=s.id ORDER BY client_ts DESC,id DESC LIMIT 1) p ON true
       LEFT JOIN LATERAL (SELECT CASE WHEN EXISTS(SELECT 1 FROM geofences gx WHERE gx.device_id=s.device_id AND gx.enabled=true) THEN COALESCE((SELECT g.name FROM geofences g JOIN geofence_states gs ON gs.geofence_id=g.id WHERE g.device_id=s.device_id AND g.enabled=true AND gs.state='inside' ORDER BY g.name LIMIT 1),'Outside Safe Zones') END AS name) z ON true
       WHERE s.id=?""",id) { rs ->
        val loc=rs.getObject("loc_ts")?.let { SosLocationOut(rs.getDouble("loc_lat"),rs.getDouble("loc_lon"),rs.getFloat("loc_accuracy").takeIf { a -> !rs.wasNull() },rs.odt("loc_ts"),rs.getString("loc_provider"),rs.getString("loc_source")) }
        SosSessionOut(rs.uuid("id"),rs.uuid("device_id"),rs.getString("device_name"),rs.getString("status"),rs.getString("source"),rs.odt("activated_at"),rs.odtN("acknowledged_at"),rs.uuidN("acknowledged_by"),rs.odtN("resolved_at"),rs.uuidN("resolved_by"),rs.odtN("cancelled_at"),rs.odtN("expires_at"),rs.getBoolean("tracking_enabled"),rs.getString("tracking_status"),rs.getString("tracking_reason"),rs.odtN("tracking_started_at"),rs.odtN("tracking_ends_at"),rs.getInt("tracking_interval_sec"),rs.intN("battery_level"),rs.getString("network_state"),rs.getString("device_state"),rs.getString("permission_status"),rs.getString("provider_status"),rs.getString("resolve_note"),loc,rs.getString("current_zone"),rs.odt("updated_at"))
        }
    }
    private fun refreshStaleTracking(id:UUID) {
        deps.db.tx { c ->
            data class Health(val device:UUID,val enabled:Boolean,val interval:Int,val status:String,val trackingStatus:String,val last:OffsetDateTime?)
            val h=c.one("""SELECT device_id,tracking_enabled,tracking_interval_sec,status,tracking_status,
                (SELECT max(client_ts) FROM sos_location_points WHERE session_id=sos_sessions.id) AS last_point
                FROM sos_sessions WHERE id=? FOR UPDATE""",id) {
                Health(it.uuid("device_id"),it.getBoolean("tracking_enabled"),it.getInt("tracking_interval_sec"),it.getString("status"),it.getString("tracking_status"),it.odtN("last_point"))
            } ?: return@tx
            if(!h.enabled || h.status !in setOf("ACTIVE","ACKNOWLEDGED")) return@tx
            val cutoff=OffsetDateTime.now().minusSeconds(maxOf(180,h.interval*3).toLong())
            if(h.last==null || h.last.isBefore(cutoff)) {
                if(h.trackingStatus!="LIMITED") {
                    c.exec("UPDATE sos_sessions SET tracking_status='LIMITED',tracking_reason='location_updates_delayed',updated_at=now() WHERE id=?",id)
                    addEvent(c,id,h.device,"SOS_LOCATION_UNAVAILABLE","system",null,mapOf("reason" to "location_updates_delayed"),"location-stale:$id")
                }
            } else if(h.trackingStatus=="LIMITED") {
                c.exec("UPDATE sos_sessions SET tracking_status='ACTIVE',tracking_reason=NULL,updated_at=now() WHERE id=?",id)
                addEvent(c,id,h.device,"SOS_TRACKING_STARTED","system",null,mapOf("reason" to "location_updates_resumed"),"tracking-recovered:$id:${h.last.toEpochSecond()}")
            }
        }
    }
    private fun dbSessions(sql:String,vararg params:Any?):List<SosSessionOut> = deps.db.query(sql,*params) { it.uuid("id") }.mapNotNull(::getById)
}
