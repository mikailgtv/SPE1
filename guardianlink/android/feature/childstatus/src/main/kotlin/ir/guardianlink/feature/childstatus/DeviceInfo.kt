package ir.guardianlink.feature.childstatus

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process

object DeviceInfo {
    fun batteryLevel(context: Context): Int? {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val status = context.registerReceiver(null, ifilter) ?: return null
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return (level * 100 / scale).coerceIn(0, 100)
    }

    fun appVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
        }.getOrDefault("0.1.0")

    fun modelName(): String = Build.MODEL
    fun manufacturer(): String = Build.MANUFACTURER
    fun androidVersion(): String = Build.VERSION.RELEASE

    /** نوع اتصال فعال — فقط برای گزارش Connection Health. */
    fun connectionState(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(net) ?: return "unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }

    /** SOS differentiates a transport from a network confirmed to reach the Internet. */
    fun sosNetworkState(context: Context): String {
        val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net=cm.activeNetwork ?: return "none"
        val caps=cm.getNetworkCapabilities(net) ?: return "limited"
        if(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return "limited"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }

    /**
     * snapshot وضعیت مجوزهایی که برای قابلیت‌های رسمی همین اپ لازم است/خواهد بود.
     * فقط وضعیت، بدون محتوا. Location هنوز فعال نشده ⇒ not_configured.
     */
    fun permissionsSnapshot(context: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        // Location (Phase 4): وضعیت واقعی مجوز runtime — جزئیات دقیق‌تر (background/foreground)
        // در فیلد اختصاصی permission_status بسته‌ی location/sync گزارش می‌شود
        out["location"] =
            if (ir.guardianlink.feature.location.LocationPermission.fineGranted(context)) "granted" else "denied"
        // این اپ هرگز notification نمی‌سازد ⇒ مجوز درخواست نمی‌شود
        out["notifications"] = if (Build.VERSION.SDK_INT >= 33) "not_requested" else "not_requested"
        // Battery optimization: برای sync قابل اتکا مهم است
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        out["battery_optimization"] =
            if (pm.isIgnoringBatteryOptimizations(context.packageName)) "unrestricted" else "optimized"
        // Usage access: برای Phase 3 (Screen Time) لازم خواهد بود
        out["usage_access"] = try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
            if (mode == AppOpsManager.MODE_ALLOWED) "granted" else "not_granted"
        } catch (_: Exception) {
            "unknown"
        }
        return out
    }
}
