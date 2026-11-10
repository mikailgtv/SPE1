package ir.guardianlink.server

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

fun Route.sosRoutes(deps:AppDeps) {
    route("device") {
        post("sos") {
            val device=call.requireDevice(deps)
            if(!deps.limiter.allow("sos-activate:${device.deviceId}",5,300)) throw ApiError.rateLimited()
            call.respond(deps.sosService.activate(device.deviceId,call.receive<SosActivateReq>()))
        }
        get("sos/active") {
            val device=call.requireDevice(deps)
            call.respond(mapOf("session" to deps.sosService.activeForDevice(device.deviceId)))
        }
        get("sos/{sosId}") {
            val device=call.requireDevice(deps)
            call.respond(deps.sosService.status(device.deviceId,call.sosId()))
        }
        post("sos/{sosId}/cancel") {
            val device=call.requireDevice(deps)
            if(!deps.limiter.allow("sos-cancel:${device.deviceId}",10,300)) throw ApiError.rateLimited()
            call.respond(deps.sosService.cancel(device.deviceId,call.sosId(),call.receive<SosCancelReq>()))
        }
        post("sos/{sosId}/tracking") {
            val device=call.requireDevice(deps)
            if(!deps.limiter.allow("sos-tracking:${device.deviceId}",30,300)) throw ApiError.rateLimited()
            call.respond(deps.sosService.updateTracking(device.deviceId,call.sosId(),call.receive<SosTrackingReq>()))
        }
        post("sos/{sosId}/network") {
            val device=call.requireDevice(deps)
            if(!deps.limiter.allow("sos-network:${device.deviceId}",120,300)) throw ApiError.rateLimited()
            call.respond(deps.sosService.reportNetworkState(device.deviceId,call.sosId(),call.receive<SosNetworkStateReq>()).let { mapOf("ok" to true) })
        }
        post("sos/{sosId}/locations") {
            val device=call.requireDevice(deps)
            if(!deps.limiter.allow("sos-location:${device.deviceId}",120,300)) throw ApiError.rateLimited()
            call.respond(mapOf("stored" to deps.sosService.addLocations(device.deviceId,call.sosId(),call.receive<SosLocationBatchReq>())))
        }
    }
    route("families/me") {
        get("sos/preferences") { val p=call.requireUser(deps); val f=p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY); call.respond(deps.sosService.preferences(f)) }
        put("sos/preferences") { val p=call.requireUser(deps); val f=p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY); call.respond(deps.sosService.savePreferences(f,call.receive<SosPreferencesReq>())) }
        get("sos/active") {
            val p=call.requireUser(deps); val family=p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            call.respond(deps.sosService.activeForFamily(family))
        }
        route("devices/{id}/sos") {
            get("history") {
                val p=call.requireUser(deps); val d=deps.ownedDevice(p,call.parameters["id"])
                val limit=call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                if(limit !in 1..100) throw ApiError.validation("limit must be 1..100")
                call.respond(deps.sosService.history(d.familyId,d.id,limit))
            }
        }
        route("sos/{sosId}") {
            get { val p=call.requireUser(deps); val f=p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY); call.respond(deps.sosService.detail(f,call.sosId()) ?: throw ApiError.notFound()) }
            get("location") { val p=call.requireUser(deps); val f=p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY); val limit=call.request.queryParameters["limit"]?.toIntOrNull() ?: 100; call.respond(deps.sosService.locations(f,call.sosId(),limit)) }
            get("events") { val p=call.requireUser(deps); val f=p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY); call.respond(deps.sosService.events(f,call.sosId())) }
            get("notes") { val p=call.requireUser(deps); val f=p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY); call.respond(deps.sosService.notes(f,call.sosId())) }
            post("notes") { val p=call.requireUser(deps); val f=p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY); if(!deps.limiter.allow("sos-note:${p.userId}",30,300)) throw ApiError.rateLimited(); call.respond(deps.sosService.addNote(f,call.sosId(),p.userId,call.receive<SosNotesReq>().note)) }
            post("acknowledge") { val p=call.requireUser(deps); val f=p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY); call.respond(deps.sosService.acknowledge(f,call.sosId(),p.userId)) }
            post("resolve") { val p=call.requireUser(deps); val f=p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY); call.respond(deps.sosService.resolve(f,call.sosId(),p.userId,call.receive<SosResolveReq>().note)) }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.sosId():UUID = parameters["sosId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ApiError.validation("invalid SOS id")
