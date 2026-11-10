package ir.guardianlink.server

import io.ktor.http.ContentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.time.LocalDate
import java.util.UUID

/**
 * Phase 3 — Screen Time endpoints.
 * هویت دستگاه فقط از توکن (requireDevice)؛ هیچ deviceId از client پذیرفته نمی‌شود.
 * مسیرهای Parent با بررسی مالکیت Family (ownedDevice ⇒ 404 برای دستگاه بیگانه).
 */
fun Route.usageRoutes(deps: AppDeps) {
    // ---------- Child ----------
    route("device") {
        post("usage/upload") {
            val p = call.requireDevice(deps)
            if (!deps.limiter.allow("usage:" + p.deviceId, 30, 300)) throw ApiError.rateLimited()
            val req = call.receive<UsageUploadReq>()
            val stored = deps.usageService.upload(p.deviceId, req)
            dlog.info("usage upload", "device" to maskDeviceId(p.deviceId), "rows" to stored.toString())
            call.respond(mapOf("stored" to stored))
        }
    }

    // ---------- Parent ----------
    route("families/me/devices/{id}") {
        get("usage/today") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("usage-read:${d.id}",120,300)) throw ApiError.rateLimited()
            call.respond(deps.usageService.today(d.id))
        }
        get("usage/weekly") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("usage-read:${d.id}",120,300)) throw ApiError.rateLimited()
            call.respond(deps.usageService.weekly(d.id))
        }
        get("usage/apps") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("usage-read:${d.id}",120,300)) throw ApiError.rateLimited()
            call.respond(deps.usageService.appsFor(d.id, call.dateParam()))
        }
        get("usage/apps/{pkg}/detail") {
            val d = call.parentDevice(deps)
            call.respond(deps.usageService.appDetail(d.id, call.pkgParam()))
        }
        get("usage/apps/{pkg}/icon") {
            val d = call.parentDevice(deps)
            val png = deps.usageService.icon(d.id, call.pkgParam()) ?: throw ApiError.notFound()
            call.respondBytes(png, ContentType.Image.PNG)
        }
        get("usage/report/daily") {
            val d = call.parentDevice(deps)
            call.respond(deps.usageService.dailyReport(d.id, call.dateParam()))
        }
        get("screen-time/report") {
            val d = call.parentDevice(deps)
            call.respond(deps.usageService.report(d.id))
        }
        get("screen-time/settings") {
            val d = call.parentDevice(deps)
            val s = deps.usageService.report(d.id).settings
            call.respond(s)
        }
        post("screen-time/settings") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("usage-config:${d.id}",30,300)) throw ApiError.rateLimited()
            val req = call.receive<ScreenTimeSettingsReq>()
            plog.info("screen time settings", "device" to maskDeviceId(d.id))
            call.respond(deps.usageService.saveSettings(d.id, req))
        }
        get("screen-time/schedules") {
            val d = call.parentDevice(deps)
            call.respond(deps.usageService.report(d.id).schedules)
        }
        post("screen-time/schedules") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("usage-config:${d.id}",30,300)) throw ApiError.rateLimited()
            val req = call.receive<ScheduleReq>()
            plog.info("schedule created", "device" to maskDeviceId(d.id))
            call.respond(deps.usageService.createSchedule(d.id, req))
        }
        put("screen-time/schedules/{sid}") {
            val d = call.parentDevice(deps)
            if(!deps.limiter.allow("usage-config:${d.id}",30,300)) throw ApiError.rateLimited()
            val sid = call.parameters["sid"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw ApiError.validation("bad schedule id")
            val req = call.receive<ScheduleReq>()
            plog.info("schedule updated", "device" to maskDeviceId(d.id), "schedule" to maskDeviceId(sid))
            call.respond(deps.usageService.updateSchedule(d.id, sid, req))
        }
    }
}

suspend fun io.ktor.server.application.ApplicationCall.parentDevice(deps: AppDeps): DeviceRow {
    val p = requireUser(deps)
    return deps.ownedDevice(p, parameters["id"])
}

private fun io.ktor.server.application.ApplicationCall.dateParam(): LocalDate? {
    val raw = request.queryParameters["date"] ?: return null
    return runCatching { LocalDate.parse(raw) }.getOrNull()
        ?: throw ApiError.validation("date must be YYYY-MM-DD")
}

private fun io.ktor.server.application.ApplicationCall.pkgParam(): String {
    val pkg = parameters["pkg"] ?: throw ApiError.validation("package required")
    if (!pkg.matches(Regex("^[A-Za-z0-9_.${'$'}]{1,100}$"))) throw ApiError.validation("bad package name")
    return pkg
}
