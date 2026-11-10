package ir.guardianlink.feature.dashboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import ir.guardianlink.core.network.NotificationOut
import ir.guardianlink.core.network.NotificationPreferencesOut
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZoneId

/**
 * Notification فقط سمت Parent (طبق سیاست پروژه؛ Child هرگز notification نمی‌سازد).
 * کانال‌های محلی Parent؛ تحویل هشدار عادی از polling تغذیه می‌شود و Push provider در پروژه موجود نیست.
 */
object ParentNotifier {
    const val ACTION_OPEN_NOTIFICATION = "ir.guardianlink.parent.OPEN_NOTIFICATION"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val ACTION_OPEN_EMERGENCY = "ir.guardianlink.parent.OPEN_EMERGENCY"
    const val EXTRA_SOS_ID = "sos_id"
    private const val CH_EMERGENCY = "gl_emergency"
    private const val CH_BATTERY = "gl_battery"
    private const val CH_CONNECTION = "gl_connection"
    private const val CH_SYNC = "gl_sync"
    private const val CH_SCREENTIME = "gl_screentime"
    private const val CH_LOCATION = "gl_location"
    private const val CH_WEB = "gl_web_safety"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH_BATTERY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_BATTERY, "باتری دستگاه", NotificationManager.IMPORTANCE_DEFAULT),
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_CONNECTION, "اتصال دستگاه", NotificationManager.IMPORTANCE_DEFAULT),
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_SYNC, "خلاصه و همگام‌سازی", NotificationManager.IMPORTANCE_LOW),
            )
        }
        if (nm.getNotificationChannel(CH_SCREENTIME) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_SCREENTIME, "زمان استفاده از اپ", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        if (nm.getNotificationChannel(CH_LOCATION) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_LOCATION, "مکان و محدودهٔ امن", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        if (nm.getNotificationChannel(CH_WEB) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_WEB, "وضعیت ایمنی وب", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        if (nm.getNotificationChannel(CH_EMERGENCY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_EMERGENCY, "هشدارهای اضطراری SOS", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "هشدارهای SOS فعال‌شده از دستگاه کودک"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Local Android delivery consumes the server notification projection, never the raw audit feed. */
    fun notifyFeed(context: Context, notifications: List<NotificationOut>, lastSeenTs: String?, preferences: NotificationPreferencesOut): String? {
        if (!canPost(context)) return lastSeenTs
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var newest = lastSeenTs
        val lastParts = lastSeenTs?.split('|', limit = 2)
        val lastTs = lastParts?.firstOrNull()
        val lastId = lastParts?.getOrNull(1).orEmpty()
        val legacyCursor = lastParts?.size != 2
        val categories = runCatching { JSONObject(preferences.enabledCategories) }.getOrNull()
        val zone = runCatching { ZoneId.of(preferences.timezone) }.getOrDefault(ZoneId.systemDefault())
        val start = runCatching { LocalTime.parse(preferences.quietStart) }.getOrDefault(LocalTime.of(22, 0))
        val end = runCatching { LocalTime.parse(preferences.quietEnd) }.getOrDefault(LocalTime.of(7, 0))
        for (n in notifications.asReversed()) {
            if (lastTs != null && (n.occurredAt < lastTs || (n.occurredAt == lastTs && (legacyCursor || n.id <= lastId)))) continue
            val categoryEnabled = categories?.optBoolean(n.category, true) ?: true
            val time = java.time.ZonedDateTime.now(zone).toLocalTime()
            val quiet = preferences.quietHoursEnabled && if (start <= end) !time.isBefore(start) && time.isBefore(end) else !time.isBefore(start) || time.isBefore(end)
            val skip = !categoryEnabled || (quiet && !(n.severity == "CRITICAL" && preferences.criticalDuringQuietHours))
            if (skip) { newest = "${n.occurredAt}|${n.id}"; continue }
            val title = when (n.eventType) {
                "BATTERY_LOW" -> "شارژ دستگاه کم است"
                "BATTERY_CRITICAL" -> "شارژ دستگاه بحرانی است"
                "DEVICE_OFFLINE" -> "دستگاه آفلاین است"
                "DEVICE_CONNECTED","DEVICE_ONLINE" -> "دستگاه متصل شد"
                "SYNC_DELAYED" -> "همگام‌سازی با تأخیر"
                "LOCATION_PERMISSION_REVOKED" -> "مجوز مکان برداشته شده"
                "LOCATION_DISABLED" -> "مکان‌یابی خاموش است"
                "GEOFENCE_ENTER" -> "ورود به محدوده امن"
                "GEOFENCE_EXIT" -> "خروج از محدوده امن"
                "WEB_PROTECTION_DISABLED" -> "حفاظت وب غیرفعال است"
                "SOS_ACTIVATED" -> "SOS اضطراری فعال شد"
                "SOS_ACTIVATION_FAILED" -> "فعال‌سازی SOS با مشکل روبه‌رو شد"
                "SOS_LOCATION_UNAVAILABLE" -> "موقعیت SOS در دسترس نیست"
                "SOS_BATTERY_CRITICAL" -> "باتری دستگاه SOS بحرانی است"
                "SOS_CANCELLED" -> "SOS توسط دستگاه لغو شد"
                "SOS_RESOLVED" -> "وضعیت SOS حل‌شده ثبت شد"
                else -> "هشدار GuardianLink"
            }
            val cooldownPrefs = context.getSharedPreferences("notification_cooldowns", Context.MODE_PRIVATE)
            val lastSent = cooldownPrefs.getLong(n.eventType, 0L)
            val nowMillis = System.currentTimeMillis()
            if (n.severity != "CRITICAL" && nowMillis - lastSent < preferences.cooldownMinutes * 60 * 1000L) { newest = "${n.occurredAt}|${n.id}"; continue }
            val channel = when (n.category) {
                "EMERGENCY" -> CH_EMERGENCY
                "BATTERY" -> CH_BATTERY
                "CONNECTIVITY" -> CH_CONNECTION
                "LOCATION","GEOFENCE" -> CH_LOCATION
                "WEB_SAFETY" -> CH_WEB
                else -> CH_SCREENTIME
            }
            val emergency = n.category == "EMERGENCY"
            val body = if (n.eventType == "SOS_ACTIVATED") "دستگاه ${n.deviceName} درخواست کمک SOS فرستاد." else "${n.deviceName} · ${if (n.severity == "CRITICAL") "بحرانی" else if (n.severity == "WARNING") "هشدار" else "اطلاع"}"
            val sosId = if (emergency) runCatching { JSONObject(n.metadata).optString("sos_session_id").takeIf { it.isNotBlank() } }.getOrNull() else null
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                action = if (emergency) ACTION_OPEN_EMERGENCY else ACTION_OPEN_NOTIFICATION
                if (emergency && sosId != null) putExtra(EXTRA_SOS_ID, sosId) else putExtra(EXTRA_NOTIFICATION_ID, n.id)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pending = launch?.let { android.app.PendingIntent.getActivity(context,n.id.hashCode(),it,android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE) }
            val builder = androidx.core.app.NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
                .setContentIntent(pending).setAutoCancel(true)
            if (emergency) builder.setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX).setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            val notification = builder.build()
            nm.notify(n.id.hashCode(), notification)
            cooldownPrefs.edit().putLong(n.eventType, nowMillis).apply()
            newest = "${n.occurredAt}|${n.id}"
        }
        return newest
    }

}
