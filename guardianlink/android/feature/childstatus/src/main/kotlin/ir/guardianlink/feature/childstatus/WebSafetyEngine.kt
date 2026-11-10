package ir.guardianlink.feature.childstatus

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import ir.guardianlink.core.network.WebSafetyOverrideOut
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The sole supported enforcement path is managed Chrome URL policy via official DPM app restrictions.
 * It affects Chrome only; DPM readback cannot verify that Chrome has consumed/enforced the policy.
 * No VPN, DNS filter, WebView filter or blocked-visit telemetry is implemented here.
 */
internal object WebSafetyEngine {
    private const val CHROME = "com.android.chrome"
    private val runMutex = Mutex()

    suspend fun run(context: Context) = runMutex.withLock {
        val creds = runCatching { ir.guardianlink.core.network.GlServices.creds.get() }.getOrNull()
        if (creds?.kind != "device") return@withLock
        val snapshot = WebSafetyCache.read()
        val policy = snapshot.policy
        if (policy == null) {
            WebSafetyCache.report("UNKNOWN", "UNKNOWN", "none")
            return@withLock
        }
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val managed = dpm != null && runCatching {
            dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
        }.getOrDefault(false)
        val mode = if (managed) "MANAGED_DEVICE" else "NORMAL"
        val admin = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        val supportedApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

        if (!policy.enabled) {
            if (managed && dpm != null && supportedApi && !clearChromePolicy(dpm, admin)) {
                WebSafetyCache.report(mode, "ERROR", "android_policy_rejected")
            } else {
                WebSafetyCache.report(mode, "DISABLED", "disabled")
            }
            return@withLock
        }

        when (policy.requestedMode) {
            "VPN_BASED" -> {
                if (managed && dpm != null && !clearChromePolicy(dpm, admin)) {
                    WebSafetyCache.report(mode, "ERROR", "android_policy_rejected")
                } else WebSafetyCache.report(mode, "UNSUPPORTED", "vpn_not_implemented")
                return@withLock
            }
            "DNS_BASED" -> {
                if (managed && dpm != null && !clearChromePolicy(dpm, admin)) {
                    WebSafetyCache.report(mode, "ERROR", "android_policy_rejected")
                } else WebSafetyCache.report(mode, "UNSUPPORTED", "dns_provider_not_configured")
                return@withLock
            }
        }
        if (!supportedApi || !managed || dpm == null) {
            WebSafetyCache.report(mode, "UNSUPPORTED", "managed_device_required")
            return@withLock
        }
        @Suppress("DEPRECATION")
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(CHROME, PackageManager.PackageInfoFlags.of(0))
            else context.packageManager.getPackageInfo(CHROME, 0)
        }.isSuccess
        if (!installed) {
            WebSafetyCache.report(mode, "UNSUPPORTED", "chrome_not_installed")
            return@withLock
        }

        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val domainRules = policy.rules.filter { it.enabled && it.ruleType == "DOMAIN" && it.domain != null }
        val hostRules = buildList {
            domainRules.forEach { rule ->
                val activeSchedule = WebSafetyScheduleMatcher.activeAction(rule.schedules, now)
                add(Compiled(rule.domain!!, rule.scope, activeSchedule ?: rule.action))
            }
            policy.activeOverrides.filter { isActive(it, now) }.forEach { add(Compiled(it.domain, it.scope, it.action)) }
        }
        val block = linkedSetOf<String>()
        val allow = linkedSetOf<String>()
        if (policy.defaultPolicy == "ALLOW_LISTED_ONLY") block += "*"
        hostRules.forEach { rule ->
            val value = if (rule.scope == "EXACT_HOST") ".${rule.domain}" else rule.domain
            if (policy.defaultPolicy == "ALLOW_LISTED_ONLY") {
                if (rule.action == "ALLOW") allow += value
            } else if (rule.action == "BLOCK") block += value else allow += value
        }
        if (block.size + allow.size > 1000) {
            WebSafetyCache.report(mode, "ERROR", "android_policy_rejected")
            return@withLock
        }

        val bundle = Bundle().apply {
            if (block.isNotEmpty()) putStringArray("URLBlocklist", block.toTypedArray())
            if (allow.isNotEmpty()) putStringArray("URLAllowlist", allow.toTypedArray())
        }
        try {
            dpm.setApplicationRestrictions(admin, CHROME, bundle)
            val stored = dpm.getApplicationRestrictions(admin, CHROME)
            val matches = stored.getStringArray("URLBlocklist").orEmpty().toList() == block.toList() &&
                stored.getStringArray("URLAllowlist").orEmpty().toList() == allow.toList()
            if (!matches) {
                WebSafetyCache.report(mode, "ERROR", "policy_readback_mismatch")
            } else {
                val hasCategories = policy.rules.any { it.enabled && it.ruleType == "CATEGORY" }
                WebSafetyCache.report(
                    mode, "CONFIGURATION_APPLIED_UNVERIFIED",
                    if (hasCategories) "category_provider_unavailable" else "policy_stored_effect_unverified",
                )
            }
        } catch (_: Exception) {
            WebSafetyCache.report(mode, "ERROR", "android_policy_rejected")
        }
    }

    private fun clearChromePolicy(dpm: DevicePolicyManager, admin: ComponentName): Boolean = runCatching {
        dpm.setApplicationRestrictions(admin, CHROME, Bundle())
        val stored = dpm.getApplicationRestrictions(admin, CHROME)
        stored.getStringArray("URLBlocklist").isNullOrEmpty() && stored.getStringArray("URLAllowlist").isNullOrEmpty()
    }.getOrDefault(false)

    private data class Compiled(val domain: String, val scope: String, val action: String)

    private fun isActive(override: WebSafetyOverrideOut, now: ZonedDateTime): Boolean =
        runCatching { Instant.parse(override.expiresAt).isAfter(now.toInstant()) }.getOrDefault(false)

}
