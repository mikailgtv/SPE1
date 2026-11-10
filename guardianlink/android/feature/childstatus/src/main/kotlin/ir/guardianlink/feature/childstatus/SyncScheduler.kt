package ir.guardianlink.feature.childstatus

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * زمان‌بندی sync بدون سرویس دائمی:
 *  - WorkManager دوره‌ای با interval سروری (حداقل ۱۵ دقیقه = حداقل مجاز سیستم)
 *  - sync فوری هنگام باز شدن اپ / وصل شدن شبکه
 *  - constraint: فقط وقتی شبکه هست ⇒ درخواست بیهوده ارسال نمی‌شود (باتری)
 */
object SyncScheduler {
    private const val PERIODIC = "gl-status-periodic"
    const val MIN_INTERVAL_MIN = 15L

    fun schedulePeriodic(context: Context, intervalMin: Long = MIN_INTERVAL_MIN) {
        val mins = intervalMin.coerceAtLeast(MIN_INTERVAL_MIN)
        val req = PeriodicWorkRequestBuilder<StatusSyncWorker>(mins, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }

    fun once(context: Context) {
        val req = OneTimeWorkRequestBuilder<StatusSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "gl-status-once-" + System.currentTimeMillis() / 60_000,
            ExistingWorkPolicy.KEEP,
            req,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
