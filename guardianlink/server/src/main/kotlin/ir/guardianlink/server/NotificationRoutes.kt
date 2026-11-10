package ir.guardianlink.server

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class NotificationPreferencesRequest(
    val enabledCategories: String,
    val quietHoursEnabled: Boolean,
    val quietStart: String,
    val quietEnd: String,
    val timezone: String,
    val criticalDuringQuietHours: Boolean,
    val dailySummaryEnabled: Boolean,
    val cooldownMinutes: Int = 10,
)

fun Route.notificationRoutes(deps: AppDeps) {
    route("families/me/notifications") {
        get {
            val (user,family) = call.notificationPrincipal(deps)
            val p=call.request.queryParameters
            val device=p["device_id"]?.let(::uuidParam)
            if (device != null && deps.devices.byId(device)?.familyId != family) throw ApiError.notFound()
            val category=p["category"]?.uppercase()?.also { if(it !in setOf("BATTERY","CONNECTIVITY","LOCATION","GEOFENCE","APP_USAGE","WEB_SAFETY","PERMISSION","SECURITY","EMERGENCY")) throw ApiError.validation("invalid category") }
            val severity=p["severity"]?.uppercase()?.also { if(it !in setOf("INFO","WARNING","CRITICAL")) throw ApiError.validation("invalid severity") }
            val unread=p["unread"]?.let { it.toBooleanStrictOrNull() ?: throw ApiError.validation("unread must be true or false") } ?: false
            val from=p["from"]?.let { dateParam(it) }; val to=p["to"]?.let { dateParam(it) }
            if(from!=null && to!=null && from.isAfter(to)) throw ApiError.validation("from must not follow to")
            val search=p["q"]?.trim()?.takeIf { it.isNotEmpty() }?.also { if(it.length>100) throw ApiError.validation("search too long") }
            val cursor=p["before"]?.let { raw ->
                val parts=raw.split('|',limit=2)
                if(parts.size!=2) throw ApiError.validation("before must be timestamp|id")
                val ts=runCatching { OffsetDateTime.parse(parts[0]) }.getOrNull() ?: throw ApiError.validation("before timestamp must be ISO-8601")
                val id=uuidParam(parts[1]); ts to id
            }
            val limit=(p["limit"]?.toIntOrNull() ?: 30).also { if(it !in 1..100) throw ApiError.validation("limit must be 1..100") }
            val items=deps.notifications.list(user.userId,family,device,category,severity,unread,from,to,search,cursor?.first,cursor?.second,limit)
            val last=items.lastOrNull()
            call.respond(NotificationPage(items,last?.let { "${it.occurredAt}|${it.id}" },deps.notifications.unreadCount(user.userId,family)))
        }
        get("unread-count") { val (u,f)=call.notificationPrincipal(deps); call.respond(mapOf("unread" to deps.notifications.unreadCount(u.userId,f))) }
        get("{id}") { val (u,f)=call.notificationPrincipal(deps); val id=call.notificationId(); call.respond(deps.notifications.detail(u.userId,f,id) ?: throw ApiError.notFound()) }
        post("{id}/read") { val (u,f)=call.notificationPrincipal(deps); if(!deps.notifications.markRead(u.userId,f,call.notificationId())) throw ApiError.notFound(); call.respond(mapOf("ok" to true)) }
        post("read-all") { val (u,f)=call.notificationPrincipal(deps); deps.notifications.markAllRead(u.userId,f); call.respond(mapOf("ok" to true)) }
        post("{id}/acknowledge") { val (u,f)=call.notificationPrincipal(deps); if(!deps.notifications.transition(u.userId,f,call.notificationId(),"ACKNOWLEDGED")) throw ApiError.notFound(); call.respond(mapOf("ok" to true)) }
        post("{id}/resolve") { val (u,f)=call.notificationPrincipal(deps); if(!deps.notifications.transition(u.userId,f,call.notificationId(),"RESOLVED")) throw ApiError.notFound(); call.respond(mapOf("ok" to true)) }
        get("preferences") { val (u,f)=call.notificationPrincipal(deps); call.respond(deps.notifications.preferences(u.userId,f)) }
        put("preferences") {
            val (_,f)=call.notificationPrincipal(deps)
            val req=call.receive<NotificationPreferencesRequest>()
            if(req.enabledCategories.length>2000 || req.enabledCategories.trim().let { !it.startsWith("{") || !it.endsWith("}") }) throw ApiError.validation("enabled_categories must be a JSON object")
            val saved=runCatching { deps.notifications.savePreferences(f,NotificationPreferences(req.enabledCategories,req.quietHoursEnabled,req.quietStart,req.quietEnd,req.timezone,req.criticalDuringQuietHours,req.dailySummaryEnabled,req.cooldownMinutes)) }.getOrElse { throw ApiError.validation("invalid notification preferences") }
            call.respond(saved)
        }
    }
}

data class NotificationPage(val items: List<NotificationRow>,val nextBefore: String?,val unreadCount: Int)

private suspend fun io.ktor.server.application.ApplicationCall.notificationPrincipal(deps: AppDeps): Pair<UserPrincipal,UUID> {
    val u=requireUser(deps); val f=u.familyId ?: throw ApiError.forbidden("family required"); return u to f
}
private fun io.ktor.server.application.ApplicationCall.notificationId()=parameters["id"]?.let(::uuidParam) ?: throw ApiError.validation("bad notification id")
private fun uuidParam(s:String)=runCatching { UUID.fromString(s) }.getOrNull() ?: throw ApiError.validation("invalid UUID")
private fun dateParam(s:String)=runCatching { LocalDate.parse(s) }.getOrNull() ?: throw ApiError.validation("date must be YYYY-MM-DD")
