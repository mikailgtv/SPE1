package ir.guardianlink.core.common

import android.util.Log
import ir.guardianlink.core.common.BuildConfig

/** دسته‌های log ساختاریافته؛ خروجی با tag مشخص برای فیلتر در logcat. */
object Cat {
    const val AUTH = "AUTH"
    const val PAIRING = "PAIRING"
    const val DEVICE = "DEVICE"
    const val NETWORK = "NETWORK"
    const val SECURITY = "SECURITY"
    const val APP = "APP"
}

/** قرمزسازی: رمز/توکن هرگز کامل log نمی‌شود. */
object Redact {
    fun token(t: String?): String = if (t.isNullOrEmpty()) "<none>" else "<redacted>"

    fun email(e: String?): String = if (e.isNullOrEmpty()) "<none>" else "<redacted>"

    fun code(c: String?): String = if (c.isNullOrEmpty()) "<none>" else "<redacted>"
}

object AppLog {
    private const val TAG = "GuardianLink"
    private val safeKeys = setOf("err", "errorClass", "health", "next_min", "count", "state", "attempt", "status", "durationMs", "work", "result", "level")

    fun info(cat: String, msg: String, vararg kv: Pair<String, String?>) {
        if (BuildConfig.DEBUG) Log.i(TAG, fmt(cat, msg, kv))
    }

    fun warn(cat: String, msg: String, vararg kv: Pair<String, String?>) {
        if (BuildConfig.DEBUG) Log.w(TAG, fmt(cat, msg, kv))
    }

    fun error(cat: String, msg: String, vararg kv: Pair<String, String?>) {
        if (BuildConfig.DEBUG) Log.e(TAG, fmt(cat, msg, kv))
    }

    private fun fmt(cat: String, msg: String, kv: Array<out Pair<String, String?>>) =
        buildString {
            append('[').append(cat).append("] ").append(msg)
            for ((k, v) in kv) if (k in safeKeys && v != null) {
                val safe = v.filterNot { it.isISOControl() }.take(128)
                append(' ').append(k).append('=').append(safe)
            }
        }
}
