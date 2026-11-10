package ir.guardianlink.feature.childstatus

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Offline checks are periodic and OS-scheduled, not real-time; Android's minimum is 15 minutes. */
object AppPolicyScheduler {
    private const val PERIODIC = "gl-app-policy-periodic"

    fun schedule(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<AppPolicyWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    fun once(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "gl-app-policy-once",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AppPolicyWorker>().build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork("gl-app-policy-once")
    }
}
