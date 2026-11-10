package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

/** endpointهای مانیتورینگ سمت Parent — همه با بررسی مالکیت Family. */
fun Route.monitoringRoutes(deps: AppDeps) {
    route("families/me/devices/{id}") {
        get("health") {
            val p = call.requireUser(deps)
            val d = deps.ownedDevice(p, call.parameters["id"])
            val st = deps.monitoring.refreshHealth(d.id) ?: throw ApiError.notFound()
            val set = deps.monitoring.settings(d.familyId)
            call.respond(
                HealthOut(
                    deviceId = d.id,
                    health = st.healthState,
                    connectionState = st.connectionState,
                    lastHeartbeat = st.lastHeartbeat,
                    lastSync = st.lastSync,
                    batteryLevel = st.batteryLevel,
                    batteryBand = st.batteryBand,
                    syncDelayedSec = set.syncDelayedSec,
                    offlineSec = set.offlineSec,
                    heartbeatIntervalSec = set.heartbeatIntervalSec,
                ),
            )
        }
        get("permissions") {
            val p = call.requireUser(deps)
            val d = deps.ownedDevice(p, call.parameters["id"])
            val st = deps.statuses.byDevice(d.id)
            call.respond(mapOf("deviceId" to d.id.toString(), "permissions" to Json.readMap(st?.permissions)))
        }
        get("events") {
            val p = call.requireUser(deps)
            val d = deps.ownedDevice(p, call.parameters["id"])
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val list = deps.events.recent(d.id, limit).map {
                EventOut(it.id, it.deviceId, it.eventType, it.ts, Json.readMap(it.metadata))
            }
            call.respond(list)
        }
    }

    route("families/me/settings") {
        get {
            val p = call.requireUser(deps)
            val fam = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            val s = deps.monitoring.settings(fam)
            call.respond(
                SettingsOut(s.batteryLowPct, s.batteryCriticalPct, s.syncDelayedSec, s.offlineSec, s.heartbeatIntervalSec),
            )
        }
        put {
            val p = call.requireUser(deps)
            val fam = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            val req = call.receive<SettingsUpdateReq>()
            val cur = deps.monitoring.settings(fam)
            val low = req.batteryLowPct ?: cur.batteryLowPct
            val critical = req.batteryCriticalPct ?: cur.batteryCriticalPct
            val delayed = req.syncDelayedSec ?: cur.syncDelayedSec
            val offline = req.offlineSec ?: cur.offlineSec
            val interval = req.heartbeatIntervalSec ?: cur.heartbeatIntervalSec
            if (low !in 5..90) throw ApiError.validation("battery_low_pct must be 5..90")
            if (critical !in 1 until low) throw ApiError.validation("battery_critical_pct must be below low")
            if (delayed !in 60..86400) throw ApiError.validation("sync_delayed_sec range")
            if (offline < delayed || offline > 604800) throw ApiError.validation("offline_sec must be >= sync_delayed_sec")
            // WorkManager حداقل ۱۵ دقیقه برای کار دوره‌ای دارد
            if (interval !in 900..86400) throw ApiError.validation("heartbeat_interval_sec must be 900..86400")
            val row = FamilySettingsRow(fam, low, critical, delayed, offline, interval)
            deps.db.tx { c -> deps.familySettings.upsert(c, row) }
            log.info("family settings updated", "family" to maskDeviceId(fam))
            call.respond(SettingsOut(low, critical, delayed, offline, interval))
        }
    }

    route("families/me/events") {
        get {
            val p = call.requireUser(deps)
            val fam = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val list = deps.events.recentForFamily(fam, limit).map {
                EventOut(it.id, it.deviceId, it.eventType, it.ts, Json.readMap(it.metadata))
            }
            call.respond(list)
        }
    }
}

/** دستگاه متعلق به خانواده‌ی خود والد؟ در غیر این صورت 404 (نشتی اطلاعات نیست). */
fun AppDeps.ownedDevice(p: UserPrincipal, rawId: String?): DeviceRow {
    val fam = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
    val id = rawId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw ApiError.validation("bad device id")
    val d = devices.byId(id)
    if (d == null || d.familyId != fam || d.status != "active") throw ApiError.notFound()
    return d
}
