package ir.guardianlink.child

import android.app.Application
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.feature.childstatus.NetworkWatcher
import ir.guardianlink.feature.childstatus.SyncScheduler
import ir.guardianlink.feature.childstatus.AppPolicyScheduler

class GlChildApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GlServices.init(this, BuildConfig.BASE_URL)
        AppLog.info(Cat.APP, "child app started", "base" to BuildConfig.BASE_URL)
        if (GlServices.creds.get() != null) {
            SyncScheduler.schedulePeriodic(this)
            AppPolicyScheduler.schedule(this)
            NetworkWatcher.register(this)
        }
    }
}
