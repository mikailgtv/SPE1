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

/**
 * Phase 6 APIs. Parent routes use parentDevice() (family ownership => 404); child identity is only
 * taken from a valid device session. The direct child sync endpoint is optional: normal operation
 * piggybacks the same payload/rules on /device/sync to avoid another request.
 */
fun Route.appManagementRoutes(deps: AppDeps) {
    route("device") {
        get("app-rules") {
            val p = call.requireDevice(deps)
            if (!deps.limiter.allow("apprules-child:${p.deviceId}", 60, 300)) throw ApiError.rateLimited()
            call.respond(deps.appManagementService.rulesSync(p.deviceId))
        }
        post("app-management/sync") {
            val p = call.requireDevice(deps)
            if (!deps.limiter.allow("appsync-child:${p.deviceId}", 30, 300)) throw ApiError.rateLimited()
            call.respond(deps.appManagementService.syncFromChild(p.deviceId, call.receive<AppManagementSyncIn>()))
        }
    }

    route("families/me/devices/{id}") {
        get("apps") {
            val d = call.parentDevice(deps)
            if (!deps.limiter.allow("app-catalog:${d.id}", 30, 60)) throw ApiError.rateLimited()
            val includeUninstalled = call.request.queryParameters["includeUninstalled"]?.toBooleanStrictOrNull() ?: false
            call.respond(deps.appManagementService.catalog(d.id, includeUninstalled))
        }
        get("apps/{packageName}/usage") {
            val d = call.parentDevice(deps)
            val pkg = call.parameters["packageName"] ?: throw ApiError.validation("packageName is required")
            val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7
            call.respond(deps.appManagementService.usageHistory(d.id, pkg, days))
        }
        get("app-rules") {
            val d = call.parentDevice(deps)
            call.respond(deps.appManagementService.parentRules(d.id))
        }
        post("app-rules") {
            val d = call.parentDevice(deps)
            val p = call.requireUser(deps)
            if(!deps.limiter.allow("app-rule:${p.userId}",30,300)) throw ApiError.rateLimited()
            call.respond(deps.appManagementService.create(d.id, p.userId, call.receive<AppRuleReq>()))
        }
        put("app-rules/{ruleId}") {
            val d = call.parentDevice(deps)
            val p = call.requireUser(deps)
            if(!deps.limiter.allow("app-rule:${p.userId}",30,300)) throw ApiError.rateLimited()
            val id = call.appRuleParam()
            call.respond(deps.appManagementService.update(d.id, id, call.receive<AppRuleReq>()))
        }
        delete("app-rules/{ruleId}") {
            val d = call.parentDevice(deps)
            val p = call.requireUser(deps)
            if(!deps.limiter.allow("app-rule:${p.userId}",30,300)) throw ApiError.rateLimited()
            val id = call.appRuleParam()
            val expected = call.request.queryParameters["expectedVersion"]?.toLongOrNull()
                ?: throw ApiError.validation("expectedVersion is required")
            deps.appManagementService.delete(d.id, id, expected)
            call.respond(mapOf("deleted" to true))
        }
        get("app-events") {
            val d = call.parentDevice(deps)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            call.respond(deps.appManagementService.events(d.id, limit))
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.appRuleParam(): UUID {
    val raw = parameters["ruleId"] ?: throw ApiError.validation("bad app rule id")
    return runCatching { UUID.fromString(raw) }.getOrNull() ?: throw ApiError.validation("bad app rule id")
}
