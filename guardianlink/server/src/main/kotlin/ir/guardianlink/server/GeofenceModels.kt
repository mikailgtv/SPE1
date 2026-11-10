package ir.guardianlink.server

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Phase 5 — Geofencing / Safe Zones.
 *
 * تصمیم معماری (طبق دستور صریح کاربر):
 * - Google Play Services / GeofencingClient استفاده نمی‌شود («دورش بزن») ⇒ موتور تشخیص
 *   ورود/خروج کاملاً سمت سرور است و از همان fixهای Phase 4 تغذیه می‌شود.
 * - هیچ سرویس پس‌زمینه/FGS روی کودک ⇒ هیچ notification اجباری وجود ندارد که لازم باشد
 *   مخفی یا دور زده شود (مخفی کردن notification اجباری سیستم اساساً ممکن/مجاز نیست).
 * - تاخیر تشخیص = فاصله‌ی چرخه‌های sync (≥۱۵ دقیقه)؛ هیچ تضمین real-time داده نمی‌شود.
 */

data class GeofenceReq(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val enabled: Boolean? = null,
    val notifyOnEntry: Boolean? = null,
    val notifyOnExit: Boolean? = null,
)

data class GeofenceOut(
    val id: UUID,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val enabled: Boolean,
    /** unknown | inside | outside | error — بند ۹/۱۰ */
    val state: String,
    val lastTransitionAt: String?,
    val lastEntryAt: String?,
    val lastExitAt: String?,
    val lastKnownAccuracy: Float?,
    val notifyOnEntry: Boolean,
    val notifyOnExit: Boolean,
    val notifyEnabled: Boolean,
    val createdAt: String,
    val updatedAt: String,
    /** آخرین رویداد این zone (برای «Entered 10 min ago» در داشبورد) */
    val lastEvent: GeofenceEventOut?,
)

data class GeofenceListOut(
    val zones: List<GeofenceOut>,
    val active: Int,
    val maxZones: Int,
    /** برای نمایش صادقانه‌ی UI: حداقل فاصله‌ی دو گذار (debounce) و دقت قابل قبول. */
    val cooldownSec: Int,
    val minAccuracyM: Int,
    val minRadiusM: Int,
    val maxRadiusM: Int,
)

data class GeofenceEventOut(
    val id: UUID,
    val geofenceId: UUID,
    val zoneName: String,
    val eventType: String,
    val ts: String,
    val accuracyM: Float?,
    val dwellMin: Int?,
    val processedAt: String,
)

data class GeofenceEventsOut(
    val events: List<GeofenceEventOut>,
    val count: Int,
)

/** کودک: فقط خواندنی — نام/وضعیت/شعاع (بدون هیچ قابلیت کنترلی). */
data class ChildGeofenceOut(
    val id: UUID,
    val name: String,
    val state: String,
    val radiusM: Int,
    val enabled: Boolean,
)

data class ChildGeofencesOut(val zones: List<ChildGeofenceOut>)

/**
 * گزارش رویداد از سوی کودک. هرگز اعتماد نمی‌شود: اگر مختصات داشته باشد سرور هندسه را
 * خودش باز-محاسبه می‌کند؛ ادعای متناقض ⇒ GEOFENCE_ERROR ثبت می‌شود نه گذار جعلی.
 */
data class GeofenceEventPost(
    val geofenceId: UUID,
    val eventType: String,
    val clientTs: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null,
)

data class GeofenceEventAck(
    val accepted: Boolean,
    val state: String,
    val reason: String? = null,
)

// ---------- DB rows ----------
data class GeofenceRow(
    val id: UUID,
    val deviceId: UUID,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val enabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class GeofenceStateRow(
    val geofenceId: UUID,
    val state: String,
    val lastTransitionAt: OffsetDateTime?,
    val lastKnownAccuracy: Float?,
    val updatedAt: OffsetDateTime,
)

data class GeofenceEventRow(
    val id: UUID,
    val geofenceId: UUID,
    val deviceId: UUID,
    val eventType: String,
    val ts: OffsetDateTime,
    val accuracyM: Float?,
    val dwellMin: Int?,
    val processedAt: OffsetDateTime,
)

data class GeofenceNotifRow(
    val geofenceId: UUID,
    val notifyOnEntry: Boolean,
    val notifyOnExit: Boolean,
    val enabled: Boolean,
)

/** zone + state + notification settings در یک نما (برای موتور ارزیابی). */
data class ZoneFull(
    val zone: GeofenceRow,
    /** mutable: موتور ارزیابی بین fixهای یک batch وضعیت محلی را جلو می‌برد. */
    var state: GeofenceStateRow,
    val notif: GeofenceNotifRow,
)
