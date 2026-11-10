package ir.guardianlink.feature.dashboard

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object NotificationSummaryScheduler {
    private const val NAME = "parent-notification-daily-summary"
    fun update(context: Context, enabled: Boolean) {
        val wm=WorkManager.getInstance(context.applicationContext)
        if(!enabled) { wm.cancelUniqueWork(NAME); return }
        val now=ZonedDateTime.now()
        val next=now.toLocalDate().plusDays(if(now.hour<9) 0 else 1).atTime(9,0).atZone(now.zone)
        val delay=maxOf(60L,Duration.between(now,next).seconds)
        val request=PeriodicWorkRequestBuilder<NotificationSummaryWorker>(24,TimeUnit.HOURS)
            .setInitialDelay(delay,TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
}
