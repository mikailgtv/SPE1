package ir.guardianlink.feature.usage

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import ir.guardianlink.core.network.AppInventoryItemIn
import java.security.MessageDigest

/**
 * Reads installed package metadata only. No app content is inspected. Android 11+ visibility and
 * Google Play QUERY_ALL_PACKAGES approval can still limit completeness; callers must show that caveat.
 */
object AppInventoryCollector {
    data class Snapshot(val apps: List<AppInventoryItemIn>, val hash: String, val broadQueryDeclared: Boolean)

    @Suppress("DEPRECATION")
    fun collect(context: Context): Snapshot {
        val pm = context.packageManager
        val manifest = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val broadDeclared = manifest.requestedPermissions?.contains("android.permission.QUERY_ALL_PACKAGES") == true
        val installed = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            pm.getInstalledApplications(0)
        }
        val rows = installed.mapNotNull { info ->
            runCatching {
                val pkg = info.packageName ?: return@runCatching null
                val pi = if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    pm.getPackageInfo(pkg, 0)
                }
                val category = when {
                    info.category == ApplicationInfo.CATEGORY_GAME -> "game"
                    info.category == ApplicationInfo.CATEGORY_AUDIO -> "audio"
                    info.category == ApplicationInfo.CATEGORY_VIDEO -> "video"
                    info.category == ApplicationInfo.CATEGORY_IMAGE -> "image"
                    info.category == ApplicationInfo.CATEGORY_SOCIAL -> "social"
                    info.category == ApplicationInfo.CATEGORY_NEWS -> "news"
                    info.category == ApplicationInfo.CATEGORY_MAPS -> "maps"
                    info.category == ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
                    info.category == ApplicationInfo.CATEGORY_ACCESSIBILITY -> "accessibility"
                    info.category == ApplicationInfo.CATEGORY_UNDEFINED -> "undefined"
                    else -> "other"
                }
                AppInventoryItemIn(
                    packageName = pkg,
                    appName = runCatching { pm.getApplicationLabel(info).toString().ifBlank { pkg } }.getOrDefault(pkg).take(180),
                    versionName = pi.versionName.orEmpty().take(100),
                    versionCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong(),
                    installedAtMs = pi.firstInstallTime.takeIf { it > 0L },
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    category = category,
                )
            }.getOrNull()
        }.sortedBy { it.packageName }
        val bytes = rows.joinToString("\n") {
            listOf(it.packageName, it.appName, it.versionName, it.versionCode, it.installedAtMs, it.isSystemApp, it.category).joinToString("|")
        }.toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return Snapshot(rows, hash, broadDeclared)
    }
}
