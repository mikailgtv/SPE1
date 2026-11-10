package ir.guardianlink.server

import java.time.OffsetDateTime
import java.util.UUID

// ---------- Auth ----------
data class RegisterReq(val email: String, val password: String)
data class LoginReq(val email: String, val password: String)
data class RefreshReq(val refreshToken: String)
data class LogoutReq(val refreshToken: String)
data class TokenResp(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
)
data class UserOut(val id: UUID, val email: String, val createdAt: OffsetDateTime)
data class DestructiveActionConfirmationReq(val password: String)

// ---------- Family ----------
data class CreateFamilyReq(val name: String)
data class FamilyOut(val id: UUID, val name: String, val createdAt: OffsetDateTime)
data class MemberOut(val id: UUID, val userId: UUID, val role: String)
data class FamilyDetailOut(val family: FamilyOut, val members: List<MemberOut>)

// ---------- Pairing ----------
data class PairingCreateOut(
    val sessionId: UUID,
    val code: String,
    val expiresAt: OffsetDateTime,
    val qrPayload: String,
)
data class ExchangeReq(
    val code: String,
    val installId: String,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
)
data class ExchangeOut(
    val exchangeToken: String,
    val familyName: String,
    val parentName: String,
    val expiresIn: Long,
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
data class ConfirmOut(val deviceId: UUID, val deviceSecret: String)

// ---------- Device ----------
data class DeviceAuthReq(val deviceId: UUID, val deviceSecret: String)
data class DeviceRefreshReq(val deviceId: UUID, val refreshToken: String)
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
    /** Phase 3: بسته‌ی مصرف (اختیاری) — در همان درخواست sync ذخیره می‌شود. */
    val usage: UsageUploadReq? = null,
    /** Phase 4: وضعیت + بسته‌ی موقعیت (اختیاری) — همان درخواست، بدون شبکه‌ی اضافه. */
    val location: LocationSyncIn? = null,
    /** Phase 6: inventory/capability/event batch in the same sync request. */
    val appManagement: AppManagementSyncIn? = null,
    /** Phase 7: web rule configuration + non-content capability status. */
    val webSafety: WebSafetySyncIn? = null,
)
data class HeartbeatOut(
    val serverTime: OffsetDateTime,
    val presence: String,
    val leaseSec: Long,
    val health: String,
    val nextSyncSec: Long,
)
data class SyncOut(
    val serverTime: OffsetDateTime,
    val health: String,
    val nextSyncSec: Long,
    val batteryLowPct: Int,
    val batteryCriticalPct: Int,
    /** Phase 4: آیا والد اشتراک موقعیت را روشن گذاشته است؟ (نمایش صادقانه در اپ کودک) */
    val locationSharing: Boolean? = null,
    /** Phase 6: rule snapshot + monotonic version, same sync response. */
    val appManagement: AppRulesSyncOut? = null,
    /** Phase 7: managed-Chrome policy payload; not a system-wide web filter. */
    val webSafety: WebSafetySyncOut? = null,
)
data class HealthOut(
    val deviceId: UUID,
    val health: String,
    val connectionState: String,
    val lastHeartbeat: OffsetDateTime?,
    val lastSync: OffsetDateTime?,
    val batteryLevel: Int?,
    val batteryBand: String,
    val syncDelayedSec: Int,
    val offlineSec: Int,
    val heartbeatIntervalSec: Int,
)
data class EventOut(
    val id: UUID,
    val deviceId: UUID,
    val eventType: String,
    val ts: OffsetDateTime,
    val metadata: Map<String, String>,
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
data class DeviceOut(
    val id: UUID,
    val maskedId: String,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
    val batteryLevel: Int?,
    val batteryBand: String,
    val lastSeen: OffsetDateTime?,
    val lastSync: OffsetDateTime?,
    val status: String,
    val presence: String,
    val health: String,
    val connectionState: String,
    val permissions: Map<String, String>,
    val createdAt: OffsetDateTime,
)
