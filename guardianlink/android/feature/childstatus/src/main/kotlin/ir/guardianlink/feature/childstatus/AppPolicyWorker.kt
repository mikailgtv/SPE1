package ir.guardianlink.feature.childstatus

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Runs from encrypted cached rules without a network constraint; timing is best-effort per WorkManager. */
class AppPolicyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        WebSafetyEngine.run(applicationContext)
        AppPolicyEngine.run(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
