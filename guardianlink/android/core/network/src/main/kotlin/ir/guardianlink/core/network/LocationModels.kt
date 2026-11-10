package ir.guardianlink.core.network

// ---------- Phase 4: Location (فقط monitoring) ----------
// Child → Server
data class LocationPointIn(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?,
    val clientTs: Long,
    val source: String? = null,
)

data class LocationSyncIn(
    val permissionStatus: String? = null,   // granted | background_granted | denied | unknown
    val providerStatus: String? = null,     // enabled | disabled | unknown
    val points: List<LocationPointIn>? = null,
)

// Server → Parent
data class LocationFixOut(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?,
    val timestamp: String,
    val source: String,
    val isCurrent: Boolean,
)

data class LocationCurrentOut(
    val fix: LocationFixOut?,
    val sharingEnabled: Boolean,
    val permissionStatus: String,
    val providerStatus: String,
    val health: String,
    val staleAfterSec: Int,
    val retentionDays: Int = 30,
)

data class LocationHistoryOut(
    val points: List<LocationFixOut>,
    val retentionDays: Int,
    val sharingEnabled: Boolean,
)

data class LocationSettingsOut(
    val deviceId: String,
    val sharingEnabled: Boolean,
    val retentionDays: Int,
)

data class LocationSettingsReq(
    val sharingEnabled: Boolean?,
    val retentionDays: Int?,
)

data class LocationDeleteOut(val deleted: Int, val scope: String)
