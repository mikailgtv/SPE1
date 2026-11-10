package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.OffsetDateTime
import java.util.UUID

data class ActivePairingOut(val sessionId: UUID, val expiresAt: OffsetDateTime, val status: String)

fun Route.pairingRoutes(deps: AppDeps) {
    route("pairing") {
        // Parent: ساخت pairing session (نیازمند auth)
        post("sessions") {
            val p = call.requireUser(deps)
            val fam = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            if (!deps.limiter.allow("pair-create:" + p.userId, 10, 300)) throw ApiError.rateLimited()
            val expires = OffsetDateTime.now().plusSeconds(deps.cfg.pairingTtlSec)
            var code = TokenService.pairingCode()
            val sid = deps.db.tx { c ->
                var tries = 0
                while (deps.pairings.byCodeHash(Digest.sha256(code))?.let { it.status != "used" && it.expiresAt.isAfter(OffsetDateTime.now()) } == true && tries < 5) {
                    code = TokenService.pairingCode()
                    tries++
                }
                deps.pairings.create(c, fam, p.userId, Digest.sha256(code), expires)
            }
            plog.info("pairing session created", "session" to maskDeviceId(sid), "family" to maskDeviceId(fam), "ttl" to deps.cfg.pairingTtlSec.toString())
            call.respond(HttpStatusCode.Created, PairingCreateOut(sid, code, expires, "gl1://pair?code=$code"))
        }

        // Parent: resume آخرین session فعال (بدون plaintext کد؛ کلاینت کد را در حافظه نگه می‌دارد)
        get("sessions/active") {
            val p = call.requireUser(deps)
            val fam = p.familyId ?: throw ApiError.notFound(ErrCode.NO_FAMILY)
            val s = deps.pairings.activeForFamily(fam) ?: throw ApiError.notFound()
            call.respond(ActivePairingOut(s.id, s.expiresAt, s.status))
        }

        // Child: مرحله ۱ — exchange کد (بدون auth؛ rate-limited)
        post("exchange") {
            if (!deps.limiter.allow("exchange:" + call.clientIp(), 10, 300)) throw ApiError.rateLimited()
            val req = call.receive<ExchangeReq>()
            val norm = req.code.trim().uppercase()
            if (norm.length != 8) throw ApiError(HttpStatusCode.BadRequest, ErrCode.PAIRING_INVALID, "bad code format")
            if(req.installId.isBlank() || req.installId.length>120) throw ApiError.validation("install identity invalid")
            val exchange=deps.tokens.newOpaque()
            val exExp=OffsetDateTime.now().plusSeconds(deps.cfg.exchangeTtlSec)
            val s=deps.db.tx { c ->
                val row=c.one("SELECT * FROM pairing_sessions WHERE code_hash=? FOR UPDATE",Digest.sha256(norm)) { rs ->
                    PairingRow(rs.uuid("id"),rs.uuid("family_id"),rs.uuid("parent_id"),rs.getString("code_hash"),rs.getString("status"),rs.odt("expires_at"),rs.getString("exchange_token_hash"),rs.odtN("exchange_expires_at"),rs.odtN("used_at"),rs.uuidN("device_id"))
                } ?: run { plog.warn("exchange invalid code","code" to Redact.code(norm));throw ApiError(HttpStatusCode.NotFound,ErrCode.PAIRING_INVALID,"pairing code not valid") }
                if(row.status!="active") throw ApiError(HttpStatusCode.Conflict,ErrCode.PAIRING_USED,"pairing code already exchanged or used")
                if(!row.expiresAt.isAfter(OffsetDateTime.now())) throw ApiError(HttpStatusCode.Gone,ErrCode.PAIRING_EXPIRED,"code expired")
                if(c.one("SELECT 1 AS n FROM devices WHERE install_id=? AND status='active'",req.installId) { it.getInt("n") }!=null) throw ApiError.conflict(ErrCode.DEVICE_ALREADY_CONNECTED,"device already connected to a family")
                val changed=c.exec("UPDATE pairing_sessions SET status='exchanging',exchange_token_hash=?,exchange_expires_at=? WHERE id=? AND status='active'",Digest.sha256(exchange),exExp,row.id)
                if(changed!=1) throw ApiError(HttpStatusCode.Conflict,ErrCode.PAIRING_USED,"pairing code already exchanged")
                row
            }
            val family = deps.families.byId(s.familyId)!!
            val parent = deps.users.byId(s.parentId)!!
            plog.info("pairing exchange ok", "session" to maskDeviceId(s.id), "family" to maskDeviceId(family.id))
            call.respond(
                ExchangeOut(
                    exchangeToken = exchange,
                    familyName = family.name,
                    parentName = parent.email.substringBefore('@'),
                    expiresIn = deps.cfg.exchangeTtlSec,
                )
            )
        }

        // Child: مرحله ۲ — confirm و صدور credential دستگاه (یک‌بار مصرف)
        post("confirm") {
            if (!deps.limiter.allow("confirm:" + call.clientIp(), 10, 300)) throw ApiError.rateLimited()
            val req = call.receive<ConfirmReq>()
            if (req.deviceName.isBlank() || req.deviceName.trim().length > 60 || req.installId.isBlank() || req.installId.length > 120) throw ApiError.validation("device identity invalid")
            val secret = deps.tokens.newOpaque()
            val result = deps.db.tx { c ->
                val s = c.one("SELECT * FROM pairing_sessions WHERE exchange_token_hash=? FOR UPDATE",Digest.sha256(req.exchangeToken)) { rs ->
                    PairingRow(rs.uuid("id"),rs.uuid("family_id"),rs.uuid("parent_id"),rs.getString("code_hash"),rs.getString("status"),rs.odt("expires_at"),rs.getString("exchange_token_hash"),rs.odtN("exchange_expires_at"),rs.odtN("used_at"),rs.uuidN("device_id"))
                } ?: throw ApiError(HttpStatusCode.NotFound,ErrCode.PAIRING_INVALID,"exchange not valid")
                if(s.status!="exchanging" || s.exchangeExpiresAt?.isAfter(OffsetDateTime.now())!=true) throw ApiError(HttpStatusCode.Conflict,ErrCode.PAIRING_USED,"exchange consumed or expired")
                if(c.one("SELECT 1 AS n FROM devices WHERE install_id=? AND status='active'",req.installId) { it.getInt("n") }!=null) throw ApiError.conflict(ErrCode.DEVICE_ALREADY_CONNECTED,"device already connected to a family")
                val id=deps.devices.create(c,s.familyId,req.deviceName.trim(),req.manufacturer.take(80),req.model.take(120),req.androidVersion.take(40),req.appVersion.take(40),req.installId,Digest.sha256(secret))
                val changed=c.exec("UPDATE pairing_sessions SET status='used',used_at=now(),device_id=? WHERE id=? AND status='exchanging' AND exchange_token_hash=?",id,s.id,Digest.sha256(req.exchangeToken))
                if(changed!=1) throw ApiError.conflict(ErrCode.PAIRING_USED,"exchange already consumed")
                s to id
            }
            val s=result.first
            val deviceId=result.second
            plog.info("device paired", "device" to maskDeviceId(deviceId), "session" to maskDeviceId(s.id))
            call.respond(HttpStatusCode.Created, ConfirmOut(deviceId, secret))
        }
    }
}

fun Route.deviceRoutes(deps: AppDeps) {
    route("device") {
        post("auth") {
            if (!deps.limiter.allow("devauth:" + call.clientIp(), 20, 300)) throw ApiError.rateLimited()
            val req = call.receive<DeviceAuthReq>()
            val d = deps.devices.byId(req.deviceId)
            if (d == null) throw ApiError.unauthorized("unknown device")
            if (d.status != "active") throw ApiError.removed()
            if (d.secretHash == null || d.secretHash != Digest.sha256(req.deviceSecret)) {
                slog.warn("device auth failed", "device" to maskDeviceId(req.deviceId))
                throw ApiError.unauthorized("bad device secret")
            }
            val refresh = deps.tokens.newOpaque()
            val sid = deps.db.tx { c ->
                deps.deviceSessions.create(
                    c, d.id, Digest.sha256(refresh),
                    OffsetDateTime.now().plusSeconds(deps.cfg.refreshTokenTtlSec),
                )
            }
            dlog.info("device auth ok", "device" to maskDeviceId(d.id))
            call.respond(TokenResp(deps.tokens.access(d.id.toString(), "device", sid), refresh, deps.cfg.accessTokenTtlSec))
        }

        post("refresh") {
            if (!deps.limiter.allow("devref:" + call.clientIp(), 40, 300)) throw ApiError.rateLimited()
            val req = call.receive<DeviceRefreshReq>()
            val out = deps.db.tx { c ->
                val hash=Digest.sha256(req.refreshToken)
                val s=c.one("SELECT id,device_id,refresh_hash,expires_at,revoked_at FROM device_sessions WHERE refresh_hash=? FOR UPDATE",hash) { SessionRow(it.uuid("id"),it.uuid("device_id"),it.getString("refresh_hash"),it.odt("expires_at"),it.odtN("revoked_at")) }
                if (s == null || s.ownerId != req.deviceId || s.revokedAt != null) throw ApiError.sessionRevoked()
                if (s.expiresAt.isBefore(OffsetDateTime.now())) throw ApiError.sessionExpired()
                val d = deps.devices.byId(req.deviceId) ?: throw ApiError.removed()
                if (d.status != "active") throw ApiError.removed()
                if(c.exec("UPDATE device_sessions SET revoked_at=now() WHERE id=? AND revoked_at IS NULL",s.id)!=1) throw ApiError.sessionRevoked()
                val refresh = deps.tokens.newOpaque()
                val sid = deps.deviceSessions.create(
                    c, d.id, Digest.sha256(refresh),
                    OffsetDateTime.now().plusSeconds(deps.cfg.refreshTokenTtlSec),
                )
                TokenResp(deps.tokens.access(d.id.toString(), "device", sid), refresh, deps.cfg.accessTokenTtlSec)
            }
            call.respond(out)
        }

        post("heartbeat") {
            val p = call.requireDevice(deps)
            if (!deps.limiter.allow("hb:" + p.deviceId, 60, 60)) throw ApiError.rateLimited()
            val req = call.receive<HeartbeatReq>()
            val out = ingest(deps, p.deviceId, req.batteryLevel, req.connectionState, req.appVersion, req.androidVersion, req.model, null, withSync = false)
            call.respond(out)
        }

        post("status") {
            val p = call.requireDevice(deps)
            if (!deps.limiter.allow("st:" + p.deviceId, 60, 60)) throw ApiError.rateLimited()
            val req = call.receive<StatusReq>()
            applySnapshot(deps, p.deviceId, req.appVersion, req.androidVersion, req.model, req.manufacturer, req.permissions, withSync = false)
            call.respond(mapOf("updated" to true))
        }

        /** sync دسته‌ای: heartbeat + snapshot + last_sync در یک درخواست (بهینه برای باتری). */
        post("sync") {
            val p = call.requireDevice(deps)
            if (!deps.limiter.allow("sy:" + p.deviceId, 60, 60)) throw ApiError.rateLimited()
            val req = call.receive<SyncReq>()
            if (req.queuedCount != null && (req.queuedCount < 0 || req.queuedCount > 100000)) {
                throw ApiError.validation("bad queued_count")
            }
            val out = ingest(deps, p.deviceId, req.batteryLevel, req.connectionState, req.appVersion, req.androidVersion, req.model, req.permissions, withSync = true)
            // Phase 6: inventory/capability + deduplicated child restriction events; return rules in same response.
            val appRules = deps.appManagementService.syncFromChild(p.deviceId, req.appManagement)
            val webSafety = deps.webSafetyService.syncFromChild(p.deviceId, req.webSafety)
            // Phase 3: usage is processed after timezone/capability state has been refreshed.
            req.usage?.let { u -> deps.usageService.upload(p.deviceId, u) }
            req.usage?.let { deps.appManagementService.evaluateUploadedUsage(p.deviceId) }
            // Phase 4: location remains batched into the same request.
            req.location?.let { l -> deps.locationService.sync(p.deviceId, l) }
            val dev = deps.devices.byId(p.deviceId)!!
            val set = deps.monitoring.settings(dev.familyId)
            val locSharing = runCatching { deps.locationService.settingsOut(p.deviceId).sharingEnabled }.getOrNull()
            call.respond(
                SyncOut(out.serverTime, out.health, set.heartbeatIntervalSec.toLong(), set.batteryLowPct, set.batteryCriticalPct, locSharing, appRules, webSafety),
            )
        }
    }
}

/**
 * ingest مشترک heartbeat/sync: اعتبارسنجی، محاسبه band و health، رویدادها، به‌روزرسانی status و devices.
 * هیچ داده‌ی child بدون validation ذخیره نمی‌شود.
 */
private fun ingest(
    deps: AppDeps,
    deviceId: UUID,
    batteryRaw: Int?,
    connRaw: String?,
    appVersion: String?,
    androidVersion: String?,
    model: String?,
    permissions: Map<String, String>?,
    withSync: Boolean,
): HeartbeatOut {
    val battery = PayloadGuard.battery(batteryRaw)
    val connState = PayloadGuard.connection(connRaw)
    val appV = PayloadGuard.shortText(appVersion, 40)
    val androidV = PayloadGuard.shortText(androidVersion, 40)
    val modelV = PayloadGuard.shortText(model, 120)
    val perms = PayloadGuard.permissions(permissions)

    val dev = deps.devices.byId(deviceId) ?: throw ApiError.removed()
    val set = deps.monitoring.settings(dev.familyId)
    val prev = deps.statuses.byDevice(deviceId)
    val prevHealth = prev?.healthState ?: Health.UNKNOWN
    val prevBand = prev?.batteryBand ?: BatteryBand.UNKNOWN
    val previousAppVersion = prev?.appVersion.orEmpty()

    val band = BatteryBand.of(battery, set.batteryLowPct, set.batteryCriticalPct)
    deps.db.tx { c ->
        deps.devices.heartbeat(c, deviceId, battery, appV, androidV, modelV)
        deps.statuses.upsertHeartbeat(c, deviceId, battery, band, connState, Health.HEALTHY)
        if (withSync) {
            deps.statuses.upsertSnapshot(c, deviceId, appV, androidV, modelV, PayloadGuard.shortText(null), null, withSync = true)
        }
    }
    // رویدادها پس از commit
    deps.monitoring.onHeartbeatConnected(deviceId, prevHealth)
    if (band != prevBand) {
        deps.db.tx { c -> deps.statuses.setBand(c, deviceId, band) }
        deps.monitoring.emitBatteryEvent(deviceId, band, prevBand, battery)
    }
    if (withSync && appV != null && previousAppVersion.isNotBlank() && appV != previousAppVersion) {
        deps.monitoring.emitAppUpdated(deviceId, previousAppVersion, appV)
    }
    if (perms.isNotEmpty() || (!withSync && appV != null)) {
        applySnapshot(deps, deviceId, appV, androidV, modelV, null, perms, withSync = false)
    }

    val st = deps.monitoring.refreshHealth(deviceId)
    return HeartbeatOut(
        OffsetDateTime.now(),
        if (st?.healthState == Health.OFFLINE) "offline" else "online",
        set.offlineSec.toLong(),
        st?.healthState ?: Health.HEALTHY,
        set.heartbeatIntervalSec.toLong(),
    )
}

private fun applySnapshot(
    deps: AppDeps,
    deviceId: UUID,
    appVersion: String?,
    androidVersion: String?,
    model: String?,
    manufacturer: String?,
    permissions: Map<String, String>?,
    withSync: Boolean,
) {
    val perms = PayloadGuard.permissions(permissions)
    val appV = PayloadGuard.shortText(appVersion, 40)
    val androidV = PayloadGuard.shortText(androidVersion, 40)
    val modelV = PayloadGuard.shortText(model, 120)
    val manV = PayloadGuard.shortText(manufacturer, 120)
    val prev = deps.statuses.byDevice(deviceId)
    val prevPerms = Json.readMap(prev?.permissions)
    val prevApp = prev?.appVersion.orEmpty()
    deps.db.tx { c ->
        deps.statuses.upsertSnapshot(c, deviceId, appV, androidV, modelV, manV, if (perms.isNotEmpty()) Json.write(perms) else null, withSync)
    }
    if (perms.isNotEmpty()) deps.monitoring.emitPermissionEvents(deviceId, if (prev == null) emptyMap() else prevPerms, perms)
    if (appV != null && prevApp.isNotBlank() && appV != prevApp) deps.monitoring.emitAppUpdated(deviceId, prevApp, appV)
}
