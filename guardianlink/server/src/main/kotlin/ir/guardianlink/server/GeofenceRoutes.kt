package ir.guardianlink.server

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Phase 5 — Geofence endpoints.
 * هویت دستگاه فقط از توکن؛ دسترسی والد فقط به دستگاه خانواده‌ی خودش (ownedDevice ⇒ 404).
 * کودک: فقط zoneهای خودش، فقط خواندنی + POST رویداد با اعتبارسنجی هندسی سمت سرور.
 */
fun Route.geofenceRoutes(deps: AppDeps) {
    // ---------- Child ----------
    route("device") {
        get("geofences") {
            val p = call.requireDevice(deps)
            if (!deps.limiter.allow("geoget:" + p.deviceId, 60, 300)) throw ApiError.rateLimited()
            call.respond(deps.geofenceService.childZones(p.deviceId))
        }
        // «sync» کودک = pull وضعیت zoneها (کودک هیچ geofence محلی ثبت نمی‌کند)
        post("geofences/sync") {
            val p = call.requireDevice(deps)
            if (!deps.limiter.allow("geosync:" + p.deviceId, 60, 300)) throw ApiError.rateLimited()
            call.respond(deps.geofenceService.childZones(p.deviceId))
        }
        post("geofences/events") {
            val p = call.requireDevice(deps)
            if (!deps.limiter.allow("geoevent:" + p.deviceId, 60, 300)) throw ApiError.rateLimited()
            val req = call.receive<GeofenceEventPost>()
            call.respond(deps.geofenceService.handleChildEvent(p.deviceId, req))
        }
    }

    // ---------- Parent ----------
    route("families/me/devices/{id}") {
        get("geofences") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("georead:${d.id}",120,300)) throw ApiError.rateLimited()
            call.respond(deps.geofenceService.list(d.id))
        }
        post("geofences") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("geowrite:${d.id}",30,300)) throw ApiError.rateLimited()
            val req = call.receive<GeofenceReq>()
            plog.info("geofence create", "device" to maskDeviceId(d.id), "zone" to "created")
            call.respond(deps.geofenceService.create(d.id, req))
        }
        put("geofences/{gid}") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("geowrite:${d.id}",30,300)) throw ApiError.rateLimited()
            val gid = call.zoneParam()
            val req = call.receive<GeofenceReq>()
            plog.info("geofence update", "device" to maskDeviceId(d.id), "zone" to maskDeviceId(gid))
            call.respond(deps.geofenceService.update(d.id, gid, req))
        }
        delete("geofences/{gid}") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("geowrite:${d.id}",30,300)) throw ApiError.rateLimited()
            val gid = call.zoneParam()
            plog.info("geofence delete", "device" to maskDeviceId(d.id), "zone" to maskDeviceId(gid))
            call.respond(deps.geofenceService.delete(d.id, gid))
        }
        get("geofences/{gid}/events") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("georead:${d.id}",120,300)) throw ApiError.rateLimited()
            val gid = call.zoneParam()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
            call.respond(deps.geofenceService.zoneEvents(d.id, gid, limit))
        }
        get("geofence-events") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("georead:${d.id}",120,300)) throw ApiError.rateLimited()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 500
            call.respond(
                deps.geofenceService.history(d.id, call.odtParamG("from"), call.odtParamG("to"), limit),
            )
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.zoneParam(): UUID {
    val raw = parameters["gid"] ?: throw ApiError.validation("bad zone id")
    return runCatching { UUID.fromString(raw) }.getOrNull() ?: throw ApiError.validation("bad zone id")
}

private fun io.ktor.server.application.ApplicationCall.odtParamG(name: String): OffsetDateTime? {
    val raw = request.queryParameters[name] ?: return null
    return runCatching { OffsetDateTime.parse(raw) }.getOrNull()
        ?: throw ApiError.validation("$name must be ISO-8601 datetime")
}
