package ir.guardianlink.core.network

data class SosLocationIn(val latitude:Double,val longitude:Double,val accuracyM:Float?,val clientTs:Long,val provider:String)
data class SosActivateReq(
    val clientRequestId:String,val clientTriggeredAt:Long=System.currentTimeMillis(),val source:String="child_ui",val batteryLevel:Int?,val networkState:String,
    val deviceState:String,val permissionStatus:String,val providerStatus:String,val trackingDurationMinutes:Int,
    val trackingIntervalSec:Int,val initialLocation:SosLocationIn?,
)
data class SosActivateOut(val session:SosSessionOut,val alreadyActive:Boolean,val queued:Boolean=false)
data class SosActiveDeviceOut(val session:SosSessionOut?)
data class SosSessionOut(
    val id:String,val deviceId:String,val deviceName:String,val status:String,val source:String,val activatedAt:String,
    val acknowledgedAt:String?,val acknowledgedBy:String?,val resolvedAt:String?,val resolvedBy:String?,val cancelledAt:String?,val expiresAt:String?,
    val trackingEnabled:Boolean,val trackingStatus:String,val trackingReason:String?,val trackingStartedAt:String?,val trackingEndsAt:String?,
    val trackingIntervalSec:Int,val batteryLevel:Int?,val networkState:String,val deviceState:String,val permissionStatus:String,val providerStatus:String,
    val resolveNote:String?,val latestLocation:SosLocationOut?,val currentZone:String?,val updatedAt:String,
)
data class SosLocationOut(val latitude:Double,val longitude:Double,val accuracyM:Float?,val timestamp:String,val provider:String,val source:String)
data class SosEventOut(val id:String,val eventType:String,val actorType:String,val actorUserId:String?,val occurredAt:String,val metadata:String)
data class SosNoteOut(val id:String,val authorUserId:String,val note:String,val createdAt:String)
data class SosTrackingReq(val durationMinutes:Int,val intervalSec:Int,val permissionStatus:String,val providerStatus:String,val capabilityReason:String?=null)
data class SosTrackingOut(val status:String,val reason:String?,val trackingEndsAt:String?,val intervalSec:Int)
data class SosLocationBatchReq(val points:List<SosLocationIn>)
data class SosNetworkStateReq(val networkState:String,val occurredAt:Long)
data class SosCancelReq(val reason:String)
data class SosResolveReq(val note:String?)
data class SosNotesReq(val note:String)
data class SosPreferencesOut(val autoExpirationHours:Int)
data class SosPreferencesReq(val autoExpirationHours:Int)
