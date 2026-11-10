package ir.guardianlink.feature.childstatus

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.ApiException
import ir.guardianlink.core.network.SosLocationIn
import ir.guardianlink.core.network.SosNetworkStateReq
import ir.guardianlink.core.network.SosTrackingReq
import ir.guardianlink.feature.location.LocationPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** User-triggered, time-bounded FGS using Android's official fused location updates. */
class SosLocationService : Service() {
    companion object {
        const val EXTRA_SESSION_ID="sos_session_id"
        const val EXTRA_ENDS_AT="sos_tracking_ends_at"
        const val EXTRA_INTERVAL="sos_tracking_interval"
        const val ACTION_STOP="ir.guardianlink.child.STOP_SOS_TRACKING"
        private const val CHANNEL="child_sos_tracking"
        private const val NOTIFICATION_ID=9301
        fun start(context:Context,sessionId:String,endsAtMs:Long,intervalSec:Int) {
            val i=Intent(context,SosLocationService::class.java).apply {
                putExtra(EXTRA_SESSION_ID,sessionId);putExtra(EXTRA_ENDS_AT,endsAtMs);putExtra(EXTRA_INTERVAL,intervalSec)
            }
            ContextCompat.startForegroundService(context,i)
        }
        fun stop(context:Context) { context.startService(Intent(context,SosLocationService::class.java).setAction(ACTION_STOP)) }
    }
    private val serviceScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val handler=Handler(Looper.getMainLooper())
    private var sessionId:String?=null
    private var endsAtMs:Long=0
    private var intervalSec=60
    private var callback:LocationCallback?=null
    private var networkCallback:android.net.ConnectivityManager.NetworkCallback?=null
    private var connectivity:android.net.ConnectivityManager?=null
    private var stopping=false
    private val stopAtEnd=Runnable { stopTracking("duration_elapsed",updateServer=true) }

    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==ACTION_STOP) { stopTracking("user_stopped",updateServer=true);return START_NOT_STICKY }
        sessionId=intent?.getStringExtra(EXTRA_SESSION_ID)
        endsAtMs=intent?.getLongExtra(EXTRA_ENDS_AT,0L) ?: 0L
        intervalSec=(intent?.getIntExtra(EXTRA_INTERVAL,60) ?: 60).coerceIn(30,60)
        if(sessionId==null || endsAtMs<=System.currentTimeMillis()) { stopSelf();return START_NOT_STICKY }
        createChannel()
        val notification=buildNotification()
        try {
            if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION_ID,notification)
        } catch (_:SecurityException) {
            stopTracking("fgs_start_failure",true,"fgs_start_failure");return START_NOT_STICKY
        } catch (_:Exception) {
            stopTracking("fgs_start_failure",true,"fgs_start_failure");return START_NOT_STICKY
        }
        if(!LocationPermission.fineGranted(this) || !LocationPermission.servicesEnabled(this)) {
            stopTracking("location_unavailable",true,if(!LocationPermission.fineGranted(this)) "background_permission_unavailable" else "provider_failure")
            return START_NOT_STICKY
        }
        startUpdates()
        monitorConnectivity()
        handler.postDelayed(stopAtEnd,(endsAtMs-System.currentTimeMillis()).coerceAtLeast(1_000L))
        return START_NOT_STICKY
    }

    private fun startUpdates() {
        val id=sessionId ?: return
        val client=LocationServices.getFusedLocationProviderClient(this)
        val request=LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,intervalSec*1000L)
            .setMinUpdateIntervalMillis(intervalSec*1000L)
            .setMaxUpdateDelayMillis(intervalSec*2_000L)
            .setWaitForAccurateLocation(false).build()
        val cb=object:LocationCallback() {
            override fun onLocationResult(result:LocationResult) {
                if(System.currentTimeMillis()>=endsAtMs) { stopTracking("duration_elapsed",true);return }
                val points=result.locations.mapNotNull(::toPoint)
                if(points.isNotEmpty()) serviceScope.launch {
                    try { GlServices.api.uploadSosLocations(id,points) }
                    catch (e:Exception) {
                        if((e as? ApiException)?.httpStatus==409) stopTracking("server_closed",updateServer=false)
                        else { SosOutbox.queueTrackingPoints(id,points); SyncScheduler.once(applicationContext) }
                    }
                }
            }
        }
        callback=cb
        try {
            client.requestLocationUpdates(request,cb,Looper.getMainLooper())
                .addOnFailureListener { stopTracking("provider_failure",true,"provider_failure") }
        } catch (_:SecurityException) { stopTracking("permission_unavailable",true,"background_permission_unavailable") }
    }

    private fun monitorConnectivity() {
        val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        connectivity=cm
        val cb=object:android.net.ConnectivityManager.NetworkCallback() {
            override fun onLost(network:android.net.Network) { recordNetwork(DeviceInfo.sosNetworkState(applicationContext)) }
            override fun onAvailable(network:android.net.Network) { recordNetwork(DeviceInfo.sosNetworkState(applicationContext)) }
        }
        networkCallback=cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }
    private fun recordNetwork(state:String) {
        val id=sessionId ?: return
        serviceScope.launch {
            val event=SosNetworkStateReq(state,System.currentTimeMillis())
            SosOutbox.queueNetworkEvent(id,event)
            runCatching { SosOutbox.flushTracking() }.onFailure { SyncScheduler.once(applicationContext) }
        }
    }
    private fun toPoint(location:Location):SosLocationIn? {
        val age=System.currentTimeMillis()-location.time
        if(age !in 0..(2*60_000L) || !location.latitude.isFinite() || !location.longitude.isFinite()) return null
        val provider=when(location.provider) { "gps" -> "gps"; "network" -> "network"; else -> "fused" }
        return SosLocationIn(location.latitude,location.longitude,if(location.hasAccuracy()) location.accuracy else null,location.time,provider)
    }

    private fun stopTracking(reason:String,updateServer:Boolean,capabilityReason:String?=null) {
        if(stopping) return
        stopping=true
        handler.removeCallbacks(stopAtEnd)
        callback?.let { runCatching { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) } }
        callback=null
        networkCallback?.let { cb -> runCatching { connectivity?.unregisterNetworkCallback(cb) } }
        networkCallback=null
        val id=sessionId
        val finish={ stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        if(updateServer && id!=null) serviceScope.launch {
            try {
                val req=if(capabilityReason==null) SosTrackingReq(0,intervalSec,LocationPermission.permissionStatus(applicationContext),LocationPermission.providerStatus(applicationContext))
                    else SosTrackingReq(15,intervalSec,LocationPermission.permissionStatus(applicationContext),LocationPermission.providerStatus(applicationContext),capabilityReason)
                runCatching { GlServices.api.updateSosTracking(id,req) }
            } finally { handler.post { finish() } }
        } else finish()
    }
    private fun createChannel() {
        if(Build.VERSION.SDK_INT>=26) {
            val nm=getSystemService(NotificationManager::class.java)
            if(nm.getNotificationChannel(CHANNEL)==null) nm.createNotificationChannel(NotificationChannel(CHANNEL,"اشتراک موقعیت SOS",NotificationManager.IMPORTANCE_LOW).apply {
                description="اطلاع‌رسانی ضروری هنگام اشتراک موقت موقعیت SOS"
                lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            })
        }
    }
    private fun buildNotification():Notification {
        val intent=packageManager.getLaunchIntentForPackage(packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val pending=intent?.let { PendingIntent.getActivity(this,NOTIFICATION_ID,it,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        return NotificationCompat.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("اشتراک موقت موقعیت SOS فعال است")
            .setContentText("موقعیت فقط برای مدت انتخاب‌شده به Parent ارسال می‌شود.")
            .setContentIntent(pending).setOngoing(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
    }
    override fun onDestroy() {
        handler.removeCallbacks(stopAtEnd)
        callback?.let { runCatching { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) } }
        networkCallback?.let { cb -> runCatching { connectivity?.unregisterNetworkCallback(cb) } }
        serviceScope.cancel()
        super.onDestroy()
    }
}
