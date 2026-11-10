package ir.guardianlink.feature.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Base64
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.UsageAppIn
import ir.guardianlink.core.network.UsageDayIn
import ir.guardianlink.core.network.UsageUploadReq
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * جمع‌آوری مصرف اپ‌ها در دستگاه کودک — فقط Monitoring.
 *
 * حداقل داده (طبق سیاست حریم خصوصی Phase 3):
 * نام پکیج، نام اپ، مجموع زمان مصرف، آخرین/اولین استفاده و تعداد باز شدن.
 * محتوای اپ‌ها، پیام‌ها، عکس‌ها، فایل‌ها، رمزها و داده‌های حسگر در مدل دادهٔ این collector نیستند.
 *
 * API رسمی: UsageStatsManager + دسترسی خاص «Usage Access» (PACKAGE_USAGE_STATS).
 * این دسترسی با Android Enterprise/Device Owner تفاوت دارد و فقط «مشاهده آمار» می‌دهد،
 * نه اعمال محدودیت؛ بنابراین Phase 3 هیچ blocking انجام نمی‌دهد.
 */
object UsageCollector {
    private const val MAX_APPS_PER_DAY = 60
    private const val ICON_SIZE = 96
    private const val ICON_QUALITY = 80
    private const val PREF_SENT_PREFIX = "icon_sent_v_"

    /** آیا دسترسی Usage Access فعال است؟ (منبع حقیقت: AppOpsManager) */
    fun hasUsageAccess(context: Context): Boolean =
        try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }

    /** Intent رسمی تنظیمات Usage Access (بدون هیچ مسیر غیررسمی). */
    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * ساخت بسته‌ی مصرف برای امروز + دیروز (تجمیع محلی، sync دوره‌ای).
     * اگر دسترسی فعال نباشد null برمی‌گرداند (sync بدون usage ادامه می‌یابد).
     */
    suspend fun collect(context: Context, withIcons: Boolean = true): UsageUploadReq? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val days = mutableListOf<UsageDayIn>()
        for (offset in listOf(0L, 1L)) {
            val date = LocalDate.now().minusDays(offset)
            val begin = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
            if (begin >= end) continue
            val day = collectDay(context, usm, date, begin, end, withIcons && offset == 0L)
            if (day.apps.isNotEmpty()) days.add(day)
        }
        return UsageUploadReq(days)
    }

    private suspend fun collectDay(
        context: Context,
        usm: UsageStatsManager,
        date: LocalDate,
        begin: Long,
        end: Long,
        withIcons: Boolean,
    ): UsageDayIn {
        val agg = runCatching { usm.queryAndAggregateUsageStats(begin, end) }.getOrDefault(emptyMap())
        val launches = launchCounts(usm, begin, end)
        val pm = context.packageManager
        val apps = agg.values
            .mapNotNull { st ->
                val pkg = st.packageName ?: return@mapNotNull null
                // totalTimeVisible در API 29+ foreground را هم شامل می‌شود؛ جمع دوباره نمی‌کنیم
                val visibleMs = if (Build.VERSION.SDK_INT >= 29) st.totalTimeVisible else 0L
                val minutes = (maxOf(st.totalTimeInForeground, visibleMs) / 60_000.0).roundToInt()
                val lastUsed = st.lastTimeUsed.takeIf { it in begin..end }
                if (minutes <= 0 && lastUsed == null) return@mapNotNull null
                val label = appLabel(pm, pkg)
                val category = appCategory(pm, pkg)
                val first = if (Build.VERSION.SDK_INT >= 29) st.firstTimeStamp.takeIf { it in begin..end } else null
                val icon = if (withIcons) freshIcon(context, pm, pkg) else null
                UsageAppIn(
                    packageName = pkg,
                    appName = label,
                    category = category,
                    usageMinutes = minutes.coerceIn(0, 1440),
                    launchCount = launches[pkg] ?: 0,
                    firstUsedAt = first,
                    lastUsedAt = lastUsed,
                    iconBase64 = icon?.first,
                    iconVersion = icon?.second,
                )
            }
            .sortedByDescending { it.usageMinutes }
            .take(MAX_APPS_PER_DAY)
        return UsageDayIn(date.toString(), apps)
    }

    private fun launchCounts(usm: UsageStatsManager, begin: Long, end: Long): Map<String, Int> {
        val out = HashMap<String, Int>()
        val events = runCatching { usm.queryEvents(begin, end) }.getOrNull() ?: return out
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val isResume = if (Build.VERSION.SDK_INT >= 29) {
                ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (isResume) {
                val pkg = ev.packageName ?: continue
                out[pkg] = (out[pkg] ?: 0) + 1
            }
        }
        return out
    }

    private fun appLabel(pm: android.content.pm.PackageManager, pkg: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().take(60) }
            .getOrDefault(pkg)

    /**
     * دسته‌بندی فقط از مقدار واقعی سیستم (ApplicationInfo.category).
     * هرگز دسته ساختگی تولید نمی‌شود؛ اگر سیستم مقدار ندهد «undefined».
     */
    private fun appCategory(pm: android.content.pm.PackageManager, pkg: String): String =
        try {
            if (Build.VERSION.SDK_INT < 26) return "undefined"
            val ai = pm.getApplicationInfo(pkg, 0)
            when (ai.category) {
                ApplicationInfo.CATEGORY_GAME -> "game"
                ApplicationInfo.CATEGORY_AUDIO -> "audio"
                ApplicationInfo.CATEGORY_VIDEO -> "video"
                ApplicationInfo.CATEGORY_IMAGE -> "image"
                ApplicationInfo.CATEGORY_SOCIAL -> "social"
                ApplicationInfo.CATEGORY_NEWS -> "news"
                ApplicationInfo.CATEGORY_MAPS -> "maps"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> "accessibility"
                else -> "undefined"
            }
        } catch (_: Exception) {
            "undefined"
        }

    /**
     * آیکون فقط برای پکیج جدید یا نسخه‌ی تغییرکرده ارسال می‌شود (payload بهینه).
     * وضعیت ارسال‌شده‌ها در prefs محلی نگه داشته می‌شود.
     */
    private suspend fun freshIcon(context: Context, pm: android.content.pm.PackageManager, pkg: String): Pair<String, Int>? {
        val version = versionCode(pm, pkg)
        val sent = GlServices.prefs.getLong(PREF_SENT_PREFIX + pkg)
        if (sent != null && sent >= version.toLong()) return null
        val png = renderIcon(pm, pkg) ?: return null
        val b64 = Base64.encodeToString(png, Base64.NO_WRAP)
        return b64 to version
    }

    /** فقط پس از sync موفق صدا ز می‌شود تا آیکون‌های ارسال‌شده علامت بخورند (retry-safe). */
    suspend fun markIconsSent(req: UsageUploadReq?) {
        if (req == null) return
        for (day in req.days) {
            for (a in day.apps) {
                if (a.iconBase64 != null) {
                    GlServices.prefs.putLong(PREF_SENT_PREFIX + a.packageName, (a.iconVersion ?: 0).toLong())
                }
            }
        }
    }

    private fun versionCode(pm: android.content.pm.PackageManager, pkg: String): Int =
        runCatching {
            val pi = pm.getPackageInfo(pkg, 0)
            if (Build.VERSION.SDK_INT >= 28) (pi.longVersionCode and 0x7FFFFFFF).toInt() else @Suppress("DEPRECATION") pi.versionCode
        }.getOrDefault(0)

    private fun renderIcon(pm: android.content.pm.PackageManager, pkg: String): ByteArray? =
        try {
            val d = pm.getApplicationIcon(pkg)
            val src = if (d is BitmapDrawable && d.bitmap != null) {
                d.bitmap
            } else {
                val b = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                d.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
                d.draw(c)
                b
            }
            val scaled = Bitmap.createScaledBitmap(src, ICON_SIZE, ICON_SIZE, true)
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, ICON_QUALITY, bos)
            val bytes = bos.toByteArray()
            // سقف محافظتی payload
            if (bytes.isEmpty() || bytes.size > 100 * 1024) null else bytes
        } catch (_: Exception) {
            null
        }

}
