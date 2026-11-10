package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.OffsetDateTime
import java.util.UUID

private val EMAIL_RX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

private fun checkEmailPassword(email: String, password: String) {
    if (email.length !in 3..254 || !EMAIL_RX.matches(email.trim())) throw ApiError.validation("email format invalid")
    if (password.length !in 12..128) throw ApiError.validation("password must be 12..128 characters")
}

private fun AppDeps.issueUserSession(userId: UUID): TokenResp {
    val refresh = tokens.newOpaque()
    val expires = OffsetDateTime.now().plusSeconds(cfg.refreshTokenTtlSec)
    val sid = db.tx { c -> userSessions.create(c, userId, Digest.sha256(refresh), expires) }
    return TokenResp(tokens.access(userId.toString(), "user", sid), refresh, cfg.accessTokenTtlSec)
}

private fun confirmDestructiveAction(deps: AppDeps, userId: UUID, password: String) {
    val user = deps.users.byId(userId) ?: throw ApiError.unauthorized()
    if (password.length !in 12..128 || !PasswordHasher.verify(user.passwordHash, password)) {
        throw ApiError.unauthorized("credentials invalid")
    }
}

fun Route.userRoutes(deps: AppDeps) {
    route("auth") {
        post("register") {
            val req = call.receive<RegisterReq>()
            if (!deps.limiter.allow("reg:" + call.clientIp(), 5, 300)) throw ApiError.rateLimited()
            val email=req.email.trim().lowercase()
            checkEmailPassword(email, req.password)
            if (deps.users.byEmail(email) != null) {
                throw ApiError.conflict(ErrCode.EMAIL_EXISTS, "email already registered")
            }
            val hash = PasswordHasher.hash(req.password)
            val userId = deps.db.tx { c -> deps.users.create(c, email, hash) }
            log.info("user registered", "user" to maskDeviceId(userId), "email" to Redact.email(email))
            call.respond(HttpStatusCode.Created, deps.issueUserSession(userId))
        }

        post("login") {
            val req = call.receive<LoginReq>()
            val email=req.email.trim().lowercase().take(254)
            if (!deps.limiter.allow("login:" + call.clientIp() + ":" + email, 8, 300)) {
                slog.warn("login rate limited", "email" to Redact.email(email))
                throw ApiError.rateLimited()
            }
            if(req.password.length>128) throw ApiError.unauthorized("invalid credentials")
            val u=if(req.email.length<=254 && EMAIL_RX.matches(email)) deps.users.byEmail(email) else null
            // هزینهٔ hash برای حساب ناشناخته هم اجرا می‌شود تا اختلاف زمان ساده کاهش یابد.
            val ok=if(u!=null) PasswordHasher.verify(u.passwordHash,req.password) else { PasswordHasher.verifyUnknownAccount(req.password);false }
            if (!ok) {
                slog.warn("login failed", "email" to Redact.email(email))
                throw ApiError.unauthorized("invalid credentials")
            }
            log.info("login ok", "user" to maskDeviceId(u!!.id))
            call.respond(deps.issueUserSession(u.id))
        }

        post("refresh") {
            val req = call.receive<RefreshReq>()
            if (!deps.limiter.allow("ref:" + call.clientIp(), 30, 300)) throw ApiError.rateLimited()
            val hash = Digest.sha256(req.refreshToken)
            val out = deps.db.tx { c ->
                val s=c.one("SELECT id,user_id,refresh_hash,expires_at,revoked_at FROM user_sessions WHERE refresh_hash=? FOR UPDATE",hash) { SessionRow(it.uuid("id"),it.uuid("user_id"),it.getString("refresh_hash"),it.odt("expires_at"),it.odtN("revoked_at")) }
                if (s == null || s.revokedAt != null) throw ApiError.sessionRevoked()
                if (s.expiresAt.isBefore(OffsetDateTime.now())) throw ApiError.sessionExpired()
                if(c.exec("UPDATE user_sessions SET revoked_at=now() WHERE id=? AND revoked_at IS NULL",s.id)!=1) throw ApiError.sessionRevoked() // serialize concurrent rotation
                val newRefresh = deps.tokens.newOpaque()
                val sid = deps.userSessions.create(
                    c, s.ownerId, Digest.sha256(newRefresh),
                    OffsetDateTime.now().plusSeconds(deps.cfg.refreshTokenTtlSec),
                )
                TokenResp(deps.tokens.access(s.ownerId.toString(), "user", sid), newRefresh, deps.cfg.accessTokenTtlSec)
            }
            log.info("refresh rotated")
            call.respond(out)
        }

        post("logout") {
            val req = call.receive<LogoutReq>()
            val s = deps.userSessions.byHash(Digest.sha256(req.refreshToken))
            if (s != null) deps.db.tx { c -> deps.userSessions.revoke(c, s.id) }
            log.info("logout")
            call.respond(HttpStatusCode.NoContent)
        }
    }

    get("me") {
        val p = call.requireUser(deps)
        val u = deps.users.byId(p.userId) ?: throw ApiError.unauthorized()
        call.respond(UserOut(u.id, u.email, u.createdAt))
    }
    delete("me") {
        val p = call.requireUser(deps)
        if (!deps.limiter.allow("account-delete:${p.userId}", 3, 3600)) throw ApiError.rateLimited()
        confirmDestructiveAction(deps, p.userId, call.receive<DestructiveActionConfirmationReq>().password)
        val removedDevices = deps.privacyService.deleteAccount(p.userId)
        alog.info("account deletion completed", "operation" to "account_delete", "count" to removedDevices.toString())
        call.respond(HttpStatusCode.NoContent)
    }

    route("families") {
        post {
            val p = call.requireUser(deps)
            val req = call.receive<CreateFamilyReq>()
            if (req.name.isBlank() || req.name.length > 60) throw ApiError.validation("family name invalid")
            if (p.familyId != null) throw ApiError.conflict(ErrCode.FAMILY_EXISTS, "user already has a family")
            val famId = deps.db.tx { c ->
                val f = deps.families.create(c, req.name.trim())
                deps.families.addMember(c, f, p.userId, "parent")
                f
            }
            log.info("family created", "family" to maskDeviceId(famId))
            val f = deps.families.byId(famId)!!
            call.respond(HttpStatusCode.Created, FamilyOut(f.id, f.name, f.createdAt))
        }
        get("me") {
            val p = call.requireUser(deps)
            val fam = p.familyId?.let { deps.families.byId(it) } ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            call.respond(FamilyDetailOut(FamilyOut(fam.id, fam.name, fam.createdAt), deps.families.members(fam.id).map { MemberOut(it.id, it.userId, it.role) }))
        }
        get("me/export") {
            val p = call.requireUser(deps)
            val familyId = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            if (!deps.limiter.allow("privacy-export:${p.userId}", 5, 300)) throw ApiError.rateLimited()
            val query = call.request.queryParameters
            val allowedQueryKeys = setOf("format", "scope")
            if (query.names().any { it !in allowedQueryKeys } || query.getAll("format").orEmpty().size > 1 || query.getAll("scope").orEmpty().size > 1) {
                throw ApiError.validation("only one format and one scope may be specified")
            }
            val format = query["format"] ?: "json"
            val scope = query["scope"] ?: "family"
            val result = deps.privacyService.exportFamily(familyId, p.userId, format, scope)
            alog.info("family export completed", "operation" to "export", "scope" to scope, "format" to format, "count" to result.records.toString())
            call.response.headers.append("Content-Disposition", "attachment; filename=guardianlink-family-export.json")
            call.respond(result.document)
        }
        delete("me") {
            val p = call.requireUser(deps)
            if (!deps.limiter.allow("family-delete:${p.userId}", 3, 3600)) throw ApiError.rateLimited()
            confirmDestructiveAction(deps, p.userId, call.receive<DestructiveActionConfirmationReq>().password)
            val familyId = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            val removedDevices = deps.privacyService.deleteFamily(familyId, p.userId)
            alog.info("family deletion completed", "operation" to "family_delete", "count" to removedDevices.toString())
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // ---------- Parent: devices ----------
    route("families/me/devices") {
        get {
            val p = call.requireUser(deps)
            val fam = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            val list = deps.devices.listActiveByFamily(fam).map { deviceOut(deps, it) }
            call.respond(list)
        }
        get("{id}") {
            val p = call.requireUser(deps)
            val fam = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw ApiError.notFound()
            val d = deps.devices.byId(id)
            // دسترسی سطح Family: دستگاه خانواده‌ی دیگر => 404
            if (d == null || d.familyId != fam || d.status != "active") throw ApiError.notFound()
            call.respond(deviceOut(deps, d))
        }
        delete("{id}") {
            val p = call.requireUser(deps)
            val fam = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw ApiError.notFound()
            val d = deps.devices.byId(id)
            if (d == null || d.familyId != fam) throw ApiError.notFound()
            if (d.status != "active") throw ApiError.conflict(ErrCode.DEVICE_REMOVED, "already removed")
            deps.db.tx { c ->
                deps.devices.markRemoved(c, id)
                deps.deviceSessions.revokeAllForDevice(c, id) // revoke فوری همه sessionها
            }
            alog.info("device removed by parent", "operation" to "device_revoke", "count" to "1")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun maskDeviceId(id: UUID): String {
    val s = id.toString()
    return s.take(8) + "…" + s.takeLast(4)
}

fun deviceOut(deps: AppDeps, row: DeviceRow): DeviceOut {
    val st = deps.monitoring.refreshHealth(row.id)
    val health = st?.healthState ?: Health.UNKNOWN
    val online = health == Health.HEALTHY || health == Health.DELAYED
    val withinPresenceLease = st?.lastHeartbeat?.isAfter(OffsetDateTime.now().minusSeconds(deps.cfg.presenceLeaseSec)) == true
    val idStr = row.id.toString()
    return DeviceOut(
        id = row.id,
        maskedId = idStr.take(8) + "…" + idStr.takeLast(4),
        deviceName = row.deviceName,
        manufacturer = st?.manufacturer?.ifBlank { row.manufacturer } ?: row.manufacturer,
        model = st?.model?.ifBlank { row.model } ?: row.model,
        androidVersion = st?.androidVersion?.ifBlank { row.androidVersion } ?: row.androidVersion,
        appVersion = st?.appVersion?.ifBlank { row.appVersion } ?: row.appVersion,
        batteryLevel = st?.batteryLevel ?: row.batteryLevel,
        batteryBand = st?.batteryBand ?: BatteryBand.UNKNOWN,
        lastSeen = st?.lastHeartbeat ?: row.lastSeen,
        lastSync = st?.lastSync,
        status = row.status,
        presence = if (online && withinPresenceLease && row.status == "active") "online" else "offline",
        health = health,
        connectionState = st?.connectionState ?: "unknown",
        permissions = Json.readMap(st?.permissions),
        createdAt = row.createdAt,
    )
}
