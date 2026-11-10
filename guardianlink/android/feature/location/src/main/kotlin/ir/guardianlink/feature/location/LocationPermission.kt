package ir.guardianlink.feature.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * وضعیت مجوز/سرویس مکان — فقط از APIهای رسمی اندروید.
 * واژگان وضعیت با سرور یکسان است:
 *   permissionStatus: granted | background_granted | denied | unknown
 *   providerStatus:   enabled | disabled | unknown
 */
object LocationPermission {
    fun fineGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** دسترسی پس‌زمینه (API 29+) — در API < 29 مفهوم جدا ندارد. */
    fun backgroundGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun servicesEnabled(context: Context): Boolean =
        runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        }.getOrDefault(false)

    /** وضعیت برای گزارش به سرور. */
    fun permissionStatus(context: Context): String = when {
        !fineGranted(context) -> "denied"
        backgroundGranted(context) -> "background_granted"
        else -> "granted"
    }

    fun providerStatus(context: Context): String =
        if (servicesEnabled(context)) "enabled" else "disabled"

    /**
     * آیا در API 30+ باید کاربر را برای «Allow all the time» به تنظیمات اپ هدایت کرد؟
     * (در اندروید ۱۱ به‌بعد درخواست مستقیم ACCESS_BACKGROUND_LOCATION دیالوگ نشان نمی‌دهد.)
     */
    fun needsSettingsForBackground(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 30 && fineGranted(context) && !backgroundGranted(context)
}
