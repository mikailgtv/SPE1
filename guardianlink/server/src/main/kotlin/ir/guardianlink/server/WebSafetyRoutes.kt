package ir.guardianlink.server

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

fun Route.webSafetyRoutes(deps: AppDeps) {
    route("device") {
        post("web-safety/sync") {
            val principal = call.requireDevice(deps)
            if (!deps.limiter.allow("websync:${principal.deviceId}", 30, 300)) throw ApiError.rateLimited()
            call.respond(deps.webSafetyService.syncFromChild(principal.deviceId, call.receive<WebSafetySyncIn>()))
        }
    }
    route("families/me/devices/{id}/web-safety") {
        get {
            val device = call.parentDevice(deps)
            call.respond(deps.webSafetyService.settings(device.id))
        }
        put {
            val device = call.parentDevice(deps)
            val user = call.requireUser(deps)
            if(!deps.limiter.allow("web-config:${user.userId}",30,300)) throw ApiError.rateLimited()
            call.respond(deps.webSafetyService.updateSettings(device.id, user.userId, call.receive<WebSafetySettingsReq>()))
        }
        get("rules") {
            val device = call.parentDevice(deps)
            call.respond(deps.webSafetyService.settings(device.id).rules)
        }
        post("rules") {
            val device = call.parentDevice(deps)
            val user = call.requireUser(deps)
            if(!deps.limiter.allow("web-config:${user.userId}",30,300)) throw ApiError.rateLimited()
            call.respond(deps.webSafetyService.createRule(device.id, user.userId, call.receive<WebSafetyRuleReq>()))
        }
        put("rules/{ruleId}") {
            val device = call.parentDevice(deps)
            val user = call.requireUser(deps)
            if(!deps.limiter.allow("web-config:${user.userId}",30,300)) throw ApiError.rateLimited()
            call.respond(deps.webSafetyService.updateRule(device.id, call.webRuleId(), call.receive<WebSafetyRuleReq>()))
        }
        delete("rules/{ruleId}") {
            val device = call.parentDevice(deps)
            val user = call.requireUser(deps)
            if(!deps.limiter.allow("web-config:${user.userId}",30,300)) throw ApiError.rateLimited()
            val version = call.request.queryParameters["expectedVersion"]?.toLongOrNull()
                ?: throw ApiError.validation("expectedVersion is required")
            deps.webSafetyService.deleteRule(device.id, call.webRuleId(), version)
            call.respond(mapOf("deleted" to true))
        }
        post("overrides") {
            val device = call.parentDevice(deps)
            val user = call.requireUser(deps)
            if(!deps.limiter.allow("web-config:${user.userId}",30,300)) throw ApiError.rateLimited()
            call.respond(deps.webSafetyService.createOverride(device.id, user.userId, call.receive<WebSafetyOverrideReq>()))
        }
        delete("overrides/{overrideId}") {
            val device = call.parentDevice(deps)
            val user = call.requireUser(deps)
            if(!deps.limiter.allow("web-config:${user.userId}",30,300)) throw ApiError.rateLimited()
            val raw = call.parameters["overrideId"] ?: throw ApiError.validation("bad override id")
            val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: throw ApiError.validation("bad override id")
            call.respond(mapOf("revoked" to deps.webSafetyService.revokeOverride(device.id, id)))
        }
        get("events") {
            val device = call.parentDevice(deps)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            call.respond(deps.webSafetyService.events(device.id, limit))
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.webRuleId(): UUID {
    val raw = parameters["ruleId"] ?: throw ApiError.validation("bad web rule id")
    return runCatching { UUID.fromString(raw) }.getOrNull() ?: throw ApiError.validation("bad web rule id")
}
