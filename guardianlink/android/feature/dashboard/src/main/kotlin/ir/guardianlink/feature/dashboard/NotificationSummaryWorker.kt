package ir.guardianlink.feature.dashboard

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.guardianlink.core.network.GlServices

class NotificationSummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        return try {
        val prefs=GlServices.api.notificationPreferences()
        if(!prefs.dailySummaryEnabled) return Result.success()
        val page=GlServices.api.notifications(limit=100,unreadOnly=true)
        val categories=runCatching { org.json.JSONObject(prefs.enabledCategories) }.getOrNull()
        val allowed=page.items.filter { categories?.optBoolean(it.category,true) != false }
        val zone=runCatching { java.time.ZoneId.of(prefs.timezone) }.getOrDefault(java.time.ZoneId.systemDefault())
        val now=java.time.ZonedDateTime.now(zone).toLocalTime()
        val start=runCatching { java.time.LocalTime.parse(prefs.quietStart) }.getOrDefault(java.time.LocalTime.of(22,0))
        val end=runCatching { java.time.LocalTime.parse(prefs.quietEnd) }.getOrDefault(java.time.LocalTime.of(7,0))
        val quiet=prefs.quietHoursEnabled && if(start<=end) !now.isBefore(start) && now.isBefore(end) else !now.isBefore(start) || now.isBefore(end)
        if(allowed.isNotEmpty() && (!quiet || (prefs.criticalDuringQuietHours && allowed.any { it.severity=="CRITICAL" })) && ParentNotifier.canPost(applicationContext)) {
            ParentNotifier.ensureChannels(applicationContext)
            val open=applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.apply {
                action=ParentNotifier.ACTION_OPEN_NOTIFICATION
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pending=open?.let { android.app.PendingIntent.getActivity(applicationContext,0,it,android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE) }
            val content="${allowed.size} اعلان خوانده‌نشده؛ ${allowed.count { it.severity == "CRITICAL" }} مورد بحرانی"
            val n=NotificationCompat.Builder(applicationContext,"gl_sync")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("خلاصهٔ روزانهٔ GuardianLink")
                .setContentText(content).setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pending).setAutoCancel(true).build()
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(880_008,n)
        }
        Result.success()
    } catch (_: Exception) { Result.retry() }
}
