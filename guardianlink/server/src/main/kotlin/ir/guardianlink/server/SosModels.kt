package ir.guardianlink.server

import java.time.OffsetDateTime
import java.util.UUID

object SosStatus {
    const val ACTIVATING="ACTIVATING"; const val ACTIVE="ACTIVE"; const val ACKNOWLEDGED="ACKNOWLEDGED"
    const val RESOLVED="RESOLVED"; const val CANCELLED="CANCELLED"; const val FAILED="FAILED"; const val EXPIRED="EXPIRED"
    val open=setOf(ACTIVATING,ACTIVE,ACKNOWLEDGED)
    val terminal=setOf(RESOLVED,CANCELLED,FAILED,EXPIRED)
}

/** Pure state transition policy, shared by route/service tests. */
object SosStateMachine {
    fun transition(from:String,to:String,actor:String):Boolean = when(from) {
        SosStatus.ACTIVATING -> to in setOf(SosStatus.ACTIVE,SosStatus.FAILED,SosStatus.CANCELLED) && actor in setOf("device","system")
        SosStatus.ACTIVE -> when(to) {
            SosStatus.ACKNOWLEDGED -> actor=="parent"
            SosStatus.RESOLVED -> actor=="parent"
            SosStatus.CANCELLED -> actor=="device" || actor=="parent"
            SosStatus.EXPIRED -> actor=="system"
            else -> false
        }
        SosStatus.ACKNOWLEDGED -> when(to) {
            SosStatus.RESOLVED,SosStatus.CANCELLED -> actor=="parent" || (to==SosStatus.CANCELLED && actor=="device")
            SosStatus.EXPIRED -> actor=="system"
            else -> false
        }
        else -> false
    }
}

data class SosLocationIn(val latitude:Double,val longitude:Double,val accuracyM:Float?,val clientTs:Long,val provider:String)
data class SosActivateReq(
    val clientRequestId:UUID,
    val clientTriggeredAt:Long=0L,
    val source:String="child_ui",
    val batteryLevel:Int?=null,
    val networkState:String="unknown",
    val deviceState:String="unknown",
    val permissionStatus:String="unknown",
    val providerStatus:String="unknown",
    val trackingDurationMinutes:Int=15,
    val trackingIntervalSec:Int=60,
    val initialLocation:SosLocationIn?=null,
)
data class SosActivateOut(val session:SosSessionOut,val alreadyActive:Boolean,val queued:Boolean=false)
data class SosSessionOut(
    val id:UUID,val deviceId:UUID,val deviceName:String,val status:String,val source:String,
    val activatedAt:OffsetDateTime,val acknowledgedAt:OffsetDateTime?,val acknowledgedBy:UUID?,
    val resolvedAt:OffsetDateTime?,val resolvedBy:UUID?,val cancelledAt:OffsetDateTime?,val expiresAt:OffsetDateTime?,
    val trackingEnabled:Boolean,val trackingStatus:String,val trackingReason:String?,val trackingStartedAt:OffsetDateTime?,
    val trackingEndsAt:OffsetDateTime?,val trackingIntervalSec:Int,val batteryLevel:Int?,val networkState:String,
    val deviceState:String,val permissionStatus:String,val providerStatus:String,val resolveNote:String?,
    val latestLocation:SosLocationOut?,val currentZone:String?,val updatedAt:OffsetDateTime,
)
data class SosLocationOut(val latitude:Double,val longitude:Double,val accuracyM:Float?,val timestamp:OffsetDateTime,val provider:String,val source:String)
data class SosEventOut(val id:UUID,val eventType:String,val actorType:String,val actorUserId:UUID?,val occurredAt:OffsetDateTime,val metadata:String)
data class SosNoteOut(val id:UUID,val authorUserId:UUID,val note:String,val createdAt:OffsetDateTime)
data class SosResolveReq(val note:String?=null)
data class SosCancelReq(val reason:String)
data class SosNotesReq(val note:String)
data class SosLocationBatchReq(val points:List<SosLocationIn>)
data class SosNetworkStateReq(val networkState:String,val occurredAt:Long)
data class SosTrackingReq(val durationMinutes:Int,val intervalSec:Int=60,val permissionStatus:String,val providerStatus:String,val capabilityReason:String?=null)
data class SosTrackingOut(val status:String,val reason:String?,val trackingEndsAt:OffsetDateTime?,val intervalSec:Int)
data class SosHistoryOut(val sessions:List<SosSessionOut>,val nextBefore:OffsetDateTime?)
data class SosPreferencesOut(val autoExpirationHours:Int)
data class SosPreferencesReq(val autoExpirationHours:Int)
