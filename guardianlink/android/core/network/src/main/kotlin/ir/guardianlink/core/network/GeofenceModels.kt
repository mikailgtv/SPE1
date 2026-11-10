package ir.guardianlink.core.network

/** Phase 5 — Safe Zones. Client never supplies device identity on child calls. */
data class GeofenceReq(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val enabled: Boolean? = null,
    val notifyOnEntry: Boolean? = null,
    val notifyOnExit: Boolean? = null,
)

data class GeofenceEventOut(
    val id: String,
    val geofenceId: String,
    val zoneName: String,
    val eventType: String,
    val ts: String,
    val accuracyM: Float?,
    val dwellMin: Int?,
    val processedAt: String,
)

data class GeofenceOut(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val enabled: Boolean,
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
    val lastEvent: GeofenceEventOut?,
)

data class GeofenceListOut(
    val zones: List<GeofenceOut>,
    val active: Int,
    val maxZones: Int,
    val cooldownSec: Int,
    val minAccuracyM: Int,
    val minRadiusM: Int,
    val maxRadiusM: Int,
)

data class GeofenceEventsOut(val events: List<GeofenceEventOut>, val count: Int)
data class ChildGeofenceOut(val id: String, val name: String, val state: String, val radiusM: Int, val enabled: Boolean)
data class ChildGeofencesOut(val zones: List<ChildGeofenceOut>)
data class GeofenceEventPost(
    val geofenceId: String,
    val eventType: String,
    val clientTs: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null,
)
data class GeofenceEventAck(val accepted: Boolean, val state: String, val reason: String?)
