package ir.guardianlink.server

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.OffsetDateTime

/**
 * Phase 4 — Location endpoints.
 * هویت دستگاه فقط از توکن؛ دسترسی والد فقط به دستگاه خانواده‌ی خودش (ownedDevice ⇒ 404).
 */
fun Route.locationRoutes(deps: AppDeps) {
    // ---------- Child ----------
    route("device") {
        post("location/sync") {
            val p = call.requireDevice(deps)
            if (!deps.limiter.allow("locsync:" + p.deviceId, 60, 300)) throw ApiError.rateLimited()
            val req = call.receive<LocationSyncIn>()
            val stored = deps.locationService.sync(p.deviceId, req)
            dlog.info("location sync", "device" to maskDeviceId(p.deviceId), "stored" to stored.toString())
            call.respond(mapOf("stored" to stored))
        }
    }

    // ---------- Parent ----------
    route("families/me/devices/{id}") {
        get("location/current") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("locread:${d.id}",120,300)) throw ApiError.rateLimited()
            call.respond(deps.locationService.current(d.id))
        }
        get("location/history") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("locread:${d.id}",120,300)) throw ApiError.rateLimited()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 500
            call.respond(deps.locationService.history(d.id, call.odtParam("from"), call.odtParam("to"), limit))
        }
        get("location/settings") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("locread:${d.id}",120,300)) throw ApiError.rateLimited()
            call.respond(deps.locationService.settingsOut(d.id))
        }
        post("location/settings") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("locwrite:${d.id}",30,300)) throw ApiError.rateLimited()
            val req = call.receive<LocationSettingsReq>()
            plog.info("location settings", "device" to maskDeviceId(d.id))
            call.respond(deps.locationService.saveSettings(d.id, req))
        }
        delete("location/history") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("locwrite:${d.id}",30,300)) throw ApiError.rateLimited()
            val scope = call.request.queryParameters["scope"] ?: throw ApiError.validation("scope is required (today|last7|all)")
            val deleted = deps.locationService.deleteHistory(d.id, scope)
            alog.info("location history deletion completed", "operation" to "location_history_delete", "scope" to scope, "count" to deleted.toString())
            call.respond(mapOf("deleted" to deleted, "scope" to scope))
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.odtParam(name: String): OffsetDateTime? {
    val raw = request.queryParameters[name] ?: return null
    return runCatching { OffsetDateTime.parse(raw) }.getOrNull()
        ?: throw ApiError.validation("$name must be ISO-8601 datetime")
}
