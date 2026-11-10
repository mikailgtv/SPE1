package ir.guardianlink.server

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import java.time.OffsetDateTime
import java.util.UUID

class AppDeps(
    val cfg: AppConfig,
    val db: Db,
    val tokens: TokenService,
    val limiter: RateLimiter,
    val users: UserRepo,
    val families: FamilyRepo,
    val userSessions: UserSessionRepo,
    val deviceSessions: DeviceSessionRepo,
    val pairings: PairingRepo,
    val devices: DeviceRepo,
    val statuses: DeviceStatusRepo,
    val events: DeviceEventRepo,
    val familySettings: FamilySettingsRepo,
    val notifications: NotificationRepo,
) {
    lateinit var monitoring: MonitoringService
    lateinit var usageService: UsageService
    lateinit var locationService: LocationService
    lateinit var geofenceService: GeofenceService
    lateinit var appManagementService: AppManagementService
    lateinit var webSafetyService: WebSafetyService
    lateinit var sosService: SosService
    lateinit var privacyService: PrivacyService

    companion object {
        fun build(cfg: AppConfig, db: Db): AppDeps {
            val tokens = TokenService(cfg)
            val d = AppDeps(
                cfg, db, tokens, RateLimiter(),
                UserRepo(db), FamilyRepo(db), UserSessionRepo(db), DeviceSessionRepo(db),
                PairingRepo(db), DeviceRepo(db),
                DeviceStatusRepo(db), DeviceEventRepo(db), FamilySettingsRepo(db), NotificationRepo(db),
            )
            d.monitoring = MonitoringService(d)
            d.usageService = UsageService(d)
            d.locationService = LocationService(d)
            d.geofenceService = GeofenceService(d)
            d.appManagementService = AppManagementService(d)
            d.webSafetyService = WebSafetyService(d)
            d.sosService = SosService(d)
            d.privacyService = PrivacyService(d)
            return d
        }
    }
}

data class UserPrincipal(val userId: UUID, val familyId: UUID?)
data class DevicePrincipal(val deviceId: UUID)

private fun ApplicationCall.bearer(): String {
    val h = request.headers[HttpHeaders.Authorization]
        ?: throw ApiError.unauthorized("missing authorization header")
    if (!h.startsWith("Bearer ", ignoreCase = true)) throw ApiError.unauthorized("bad scheme")
    return h.removePrefix("Bearer ").removePrefix("bearer ").trim()
}

/** احراز هویت Parent: JWT + بررسی زنده‌بودن session (revocation فوری). */
suspend fun ApplicationCall.requireUser(deps: AppDeps): UserPrincipal {
    val claims = deps.tokens.verifyAccess(bearer())
    if (claims.getClaim("typ") as? String != "user") throw ApiError.unauthorized("wrong token type")
    val sid = UUID.fromString(claims.getClaim("sid") as String)
    val s = deps.userSessions.byId(sid) ?: throw ApiError.sessionRevoked()
    if (s.revokedAt != null) throw ApiError.sessionRevoked()
    if (s.expiresAt.isBefore(OffsetDateTime.now())) throw ApiError.sessionExpired()
    val userId = UUID.fromString(claims.subject)
    val family = deps.families.familyOfUser(userId)
    return UserPrincipal(userId, family?.id)
}

/** احراز هویت Child Device: JWT نوع device + بررسی زنده‌بودن device_session. */
suspend fun ApplicationCall.requireDevice(deps: AppDeps): DevicePrincipal {
    val claims = deps.tokens.verifyAccess(bearer())
    if (claims.getClaim("typ") as? String != "device") throw ApiError.unauthorized("wrong token type")
    val sid = UUID.fromString(claims.getClaim("sid") as String)
    val s = deps.deviceSessions.byId(sid) ?: throw ApiError.sessionRevoked()
    if (s.revokedAt != null) throw ApiError.sessionRevoked()
    if (s.expiresAt.isBefore(OffsetDateTime.now())) throw ApiError.sessionExpired()
    val deviceId = UUID.fromString(claims.subject)
    val dev = deps.devices.byId(deviceId) ?: throw ApiError.removed()
    if (dev.status != "active") throw ApiError.removed()
    return DevicePrincipal(deviceId)
}

fun ApplicationCall.clientIp(): String =
    request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()
        ?: request.local.remoteHost
