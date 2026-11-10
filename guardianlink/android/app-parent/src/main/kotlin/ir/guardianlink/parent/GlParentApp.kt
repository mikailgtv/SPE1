package ir.guardianlink.parent

import android.app.Application
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import ir.guardianlink.core.network.GlServices

class GlParentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GlServices.init(this, BuildConfig.BASE_URL)
        AppLog.info(Cat.APP, "parent app started", "base" to BuildConfig.BASE_URL)
    }
}
