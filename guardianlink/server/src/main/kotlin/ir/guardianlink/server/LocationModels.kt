package ir.guardianlink.server

import java.time.OffsetDateTime
import java.util.UUID

// ---------- Child → Server ----------
data class LocationPointIn(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?,
    val clientTs: Long,
    val source: String? = null,
)

/** وضعیت + بسته‌ی موقعیت؛ داخل همان درخواست sync یا endpoint اختصاصی. */
data class LocationSyncIn(
    val permissionStatus: String? = null,   // granted | background_granted | denied | unknown
    val providerStatus: String? = null,     // enabled | disabled | unknown
    val points: List<LocationPointIn>? = null,
)

// ---------- Server → Parent ----------
data class LocationFixOut(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?,
    val timestamp: String,
    val source: String,
    /** آیا این موقعیت «فعلی» محسوب می‌شود یا صرفاً «آخرین موقعیت معلوم». */
    val isCurrent: Boolean,
)

data class LocationCurrentOut(
    val fix: LocationFixOut?,
    val sharingEnabled: Boolean,
    val permissionStatus: String,
    val providerStatus: String,
    val health: String,
    val staleAfterSec: Int,
    val retentionDays: Int,
)

data class LocationHistoryOut(
    val points: List<LocationFixOut>,
    val retentionDays: Int,
    val sharingEnabled: Boolean,
)

data class LocationSettingsOut(
    val deviceId: UUID,
    val sharingEnabled: Boolean,
    val retentionDays: Int,
)

data class LocationSettingsReq(
    val sharingEnabled: Boolean?,
    val retentionDays: Int?,
)

data class LocationRow(
    val id: UUID,
    val deviceId: UUID,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val clientTs: OffsetDateTime,
    val serverTs: OffsetDateTime,
    val source: String,
)

data class LocationSettingsRow(
    val deviceId: UUID,
    val sharingEnabled: Boolean,
    val retentionDays: Int,
)

data class LocationStatusRow(
    val deviceId: UUID,
    val permissionStatus: String,
    val providerStatus: String,
    val lastFixTs: OffsetDateTime?,
    val lastReportTs: OffsetDateTime?,
    val healthState: String,
)
