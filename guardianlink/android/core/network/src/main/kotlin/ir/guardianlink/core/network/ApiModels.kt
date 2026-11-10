package ir.guardianlink.core.network

// ---------- DTOها (نام‌ها با policy snake_case نگاشته می‌شوند) ----------
data class TokenResp(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
)

data class UserOut(val id: String, val email: String, val createdAt: String?)
data class FamilyOut(val id: String, val name: String, val createdAt: String?)
data class MemberOut(val id: String, val userId: String, val role: String)
data class FamilyDetailOut(val family: FamilyOut, val members: List<MemberOut>)

data class PairingCreateOut(val sessionId: String, val code: String, val expiresAt: String, val qrPayload: String)
data class ExchangeOut(val exchangeToken: String, val familyName: String, val parentName: String, val expiresIn: Long)
data class ConfirmOut(val deviceId: String, val deviceSecret: String)
data class HeartbeatOut(val serverTime: String, val presence: String, val leaseSec: Long)

data class DeviceOut(
    val id: String,
    val maskedId: String?,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
    val batteryLevel: Int?,
    val batteryBand: String?,
    val lastSeen: String?,
    val lastSync: String?,
    val status: String,
    val presence: String,
    val health: String?,
    val connectionState: String?,
    val permissions: Map<String, String>?,
    val createdAt: String?,
)

// ---------- Requests ----------
data class RegisterReq(val email: String, val password: String)
data class LoginReq(val email: String, val password: String)
data class RefreshReq(val refreshToken: String)
data class CreateFamilyReq(val name: String)
data class ExchangeReq(
    val code: String,
    val installId: String,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
)
data class ConfirmReq(
    val exchangeToken: String,
    val installId: String,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
)
data class DeviceAuthReq(val deviceId: String, val deviceSecret: String)
data class DeviceRefreshReq(val deviceId: String, val refreshToken: String)
data class HeartbeatReq(
    val batteryLevel: Int?,
    val appVersion: String?,
    val androidVersion: String?,
    val model: String?,
    val connectionState: String? = null,
)

data class StatusReq(
    val appVersion: String?,
    val androidVersion: String?,
    val model: String?,
    val manufacturer: String?,
    val permissions: Map<String, String>? = null,
)

data class SyncReq(
    val batteryLevel: Int?,
    val connectionState: String? = null,
    val appVersion: String? = null,
    val androidVersion: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val permissions: Map<String, String>? = null,
    val queuedCount: Int? = null,
    /** Phase 3: بسته‌ی مصرف — همراه همان درخواست sync (batch، بهینه برای باتری). */
    val usage: UsageUploadReq? = null,
    /** Phase 4: وضعیت + بسته‌ی موقعیت — همان درخواست sync (بدون شبکه‌ی اضافه). */
    val location: LocationSyncIn? = null,
    /** Phase 6: package inventory/capability/event batch piggybacked on the existing sync. */
    val appManagement: AppManagementSyncIn? = null,
    /** Phase 7: web rules + minimal capability status; never page or visit telemetry. */
    val webSafety: WebSafetySyncIn? = null,
)

data class SyncOut(
    val serverTime: String?,
    val health: String,
    val nextSyncSec: Long,
    val batteryLowPct: Int,
    val batteryCriticalPct: Int,
    /** Phase 4: وضعیت کلید اشتراک موقعیت نزد والد. */
    val locationSharing: Boolean? = null,
    val appManagement: AppRulesSyncOut? = null,
    val webSafety: WebSafetySyncOut? = null,
)

data class HealthOut(
    val deviceId: String,
    val health: String,
    val connectionState: String,
    val lastHeartbeat: String?,
    val lastSync: String?,
    val batteryLevel: Int?,
    val batteryBand: String,
    val syncDelayedSec: Int,
    val offlineSec: Int,
    val heartbeatIntervalSec: Int,
)

data class EventOut(
    val id: String,
    val deviceId: String,
    val eventType: String,
    val ts: String,
    val metadata: Map<String, String>?,
)

data class SettingsOut(
    val batteryLowPct: Int,
    val batteryCriticalPct: Int,
    val syncDelayedSec: Int,
    val offlineSec: Int,
    val heartbeatIntervalSec: Int,
)

data class SettingsUpdateReq(
    val batteryLowPct: Int?,
    val batteryCriticalPct: Int?,
    val syncDelayedSec: Int?,
    val offlineSec: Int?,
    val heartbeatIntervalSec: Int?,
)
