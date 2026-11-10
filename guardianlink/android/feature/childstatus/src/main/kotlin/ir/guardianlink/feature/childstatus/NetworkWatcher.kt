package ir.guardianlink.feature.childstatus

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import ir.guardianlink.core.network.GlServices

/** به‌محض وصل شدن شبکه، یک sync فوری (بازیابی سریع حالت آنلاین). */
object NetworkWatcher {
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun register(context: Context) {
        if (callback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (GlServices.creds.get() != null) {
                    AppLog.info(Cat.NETWORK, "network available, sync once")
                    SyncScheduler.once(context)
                }
            }
        }
        cm.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            cb,
        )
        callback = cb
    }

    fun unregister(context: Context) {
        callback?.let {
            runCatching {
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            }
        }
        callback = null
    }
}
