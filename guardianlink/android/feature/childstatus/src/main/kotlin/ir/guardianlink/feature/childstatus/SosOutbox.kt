package ir.guardianlink.feature.childstatus

import com.google.gson.Gson
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.SosActivateOut
import ir.guardianlink.core.network.SosActivateReq
import ir.guardianlink.core.network.SosLocationIn
import java.util.UUID

/** Encrypted, bounded SOS outbox. Payload remains under Android Keystore AES-GCM protection. */
object SosOutbox {
    private const val KEY="sos_outbox_v1"
    private const val EXPIRES_MS=24L*60*60*1000
    private const val MAX_POINTS=1800
    private val gson=Gson()
    data class Record(val request:SosActivateReq,val queuedAtMs:Long,val sessionId:String?=null,val points:List<SosLocationIn> = emptyList())
    data class TrackingRecord(val sessionId:String,val queuedAtMs:Long,val points:List<SosLocationIn> = emptyList(),val networkEvents:List<ir.guardianlink.core.network.SosNetworkStateReq> = emptyList())
    private const val TRACKING_KEY="sos_tracking_outbox_v1"

    suspend fun enqueue(request:SosActivateReq) {
        val old=readRecord()
        if(old!=null && old.request.clientRequestId!=request.clientRequestId) return
        val rec=old ?: Record(request,System.currentTimeMillis())
        GlServices.secure.put(KEY,gson.toJson(rec))
        GlServices.prefs.putString("sos_outbox_state","QUEUED")
    }
    suspend fun appendPoints(requestId:String,points:List<SosLocationIn>) {
        if(points.isEmpty()) return
        val rec=readRecord()?.takeIf { it.request.clientRequestId==requestId } ?: return
        val merged=(rec.points+points).distinctBy { it.clientTs }.sortedBy { it.clientTs }.takeLast(MAX_POINTS)
        GlServices.secure.put(KEY,gson.toJson(rec.copy(points=merged)))
    }
    suspend fun queuedState():String?=GlServices.prefs.getString("sos_outbox_state")
    suspend fun discardPending(state:String) { GlServices.secure.remove(KEY);GlServices.prefs.putString("sos_outbox_state",state) }

    suspend fun syncPending():SosActivateOut? {
        var activated:SosActivateOut?=null
        val rec=readRecord()
        if(rec!=null) {
            if(System.currentTimeMillis()-rec.queuedAtMs>EXPIRES_MS) {
                GlServices.secure.remove(KEY)
                GlServices.prefs.putString("sos_outbox_state","EXPIRED_NOT_DELIVERED")
            } else {
                activated=if(rec.sessionId==null) GlServices.api.activateSos(rec.request) else null
                val sessionId=rec.sessionId ?: activated?.session?.id
                if(activated!=null && activated.session.status !in setOf("ACTIVE","ACKNOWLEDGED","ACTIVATING")) {
                    GlServices.secure.remove(KEY)
                    GlServices.prefs.putString("sos_outbox_state","SOS_CLOSED_BEFORE_SYNC")
                } else if(sessionId!=null) {
                    var remaining=rec.points
                    while(remaining.isNotEmpty()) {
                        val batch=remaining.take(20)
                        try { GlServices.api.uploadSosLocations(sessionId,batch) } catch(e:ir.guardianlink.core.network.ApiException) {
                            if(e.httpStatus==409) { GlServices.secure.remove(KEY);GlServices.prefs.putString("sos_outbox_state","SOS_CLOSED_BEFORE_SYNC");remaining=emptyList();break }
                            throw e
                        }
                        val sent=batch.map { it.clientTs }.toSet()
                        remaining=remaining.filterNot { it.clientTs in sent }
                        GlServices.secure.put(KEY,gson.toJson(rec.copy(sessionId=sessionId,points=remaining)))
                    }
                    GlServices.secure.remove(KEY)
                    if(GlServices.prefs.getString("sos_outbox_state")!="SOS_CLOSED_BEFORE_SYNC") GlServices.prefs.putString("sos_outbox_state",null)
                }
            }
        }
        flushTracking()
        return activated
    }

    suspend fun queueTrackingPoints(sessionId:String,points:List<SosLocationIn>) {
        if(points.isEmpty()) return
        val old=readTracking()
        val rec=old?.takeIf { it.sessionId==sessionId } ?: TrackingRecord(sessionId,System.currentTimeMillis())
        val merged=(rec.points+points).distinctBy { it.clientTs }.sortedBy { it.clientTs }.takeLast(MAX_POINTS)
        GlServices.secure.put(TRACKING_KEY,gson.toJson(rec.copy(points=merged)))
    }
    suspend fun queueNetworkEvent(sessionId:String,event:ir.guardianlink.core.network.SosNetworkStateReq) {
        val old=readTracking()
        val rec=old?.takeIf { it.sessionId==sessionId } ?: TrackingRecord(sessionId,System.currentTimeMillis())
        GlServices.secure.put(TRACKING_KEY,gson.toJson(rec.copy(networkEvents=(rec.networkEvents+event).distinctBy { it.occurredAt }.takeLast(100))))
    }
    suspend fun flushTracking() {
        val rec=readTracking() ?: return
        if(System.currentTimeMillis()-rec.queuedAtMs>EXPIRES_MS) { GlServices.secure.remove(TRACKING_KEY); GlServices.prefs.putString("sos_outbox_state","TRACKING_EXPIRED_NOT_DELIVERED"); return }
        try {
            var remaining=rec.points
            while(remaining.isNotEmpty()) {
                val batch=remaining.take(20)
                GlServices.api.uploadSosLocations(rec.sessionId,batch)
                val ids=batch.map { it.clientTs }.toSet()
                remaining=remaining.filterNot { it.clientTs in ids }
                GlServices.secure.put(TRACKING_KEY,gson.toJson(rec.copy(points=remaining)))
            }
            for(e in rec.networkEvents) GlServices.api.reportSosNetwork(rec.sessionId,e)
            GlServices.secure.remove(TRACKING_KEY)
        } catch(e:ir.guardianlink.core.network.ApiException) {
            if(e.httpStatus==409) { GlServices.secure.remove(TRACKING_KEY); GlServices.prefs.putString("sos_outbox_state","TRACKING_STOPPED") } else throw e
        }
    }

    suspend fun hasPending():Boolean=readRecord()!=null
    suspend fun pendingRequestId():String?=readRecord()?.request?.clientRequestId

    private suspend fun readRecord():Record? {
        val raw=GlServices.secure.get(KEY) ?: return null
        return runCatching { gson.fromJson(raw,Record::class.java) }.getOrNull()
    }
    private suspend fun readTracking():TrackingRecord? {
        val raw=GlServices.secure.get(TRACKING_KEY) ?: return null
        return runCatching { gson.fromJson(raw,TrackingRecord::class.java) }.getOrNull()
    }
}
