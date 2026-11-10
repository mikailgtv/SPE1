package ir.guardianlink.feature.childstatus

import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import ir.guardianlink.core.network.AppRuleOut
import ir.guardianlink.core.network.AppRuleScheduleOut
import ir.guardianlink.feature.usage.UsageCollector
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local Phase 6 policy evaluator. WorkManager invokes it opportunistically (>=15 minute cadence).
 * In Normal mode this does not attempt to block apps; it queues a truthful unsupported event.
 * Actual suspension requires Android to confirm this package is the Device Owner.
 */
internal object AppPolicyEngine {
    private val runMutex = Mutex()

    suspend fun run(context: Context) {
        runMutex.withLock {
        val creds = runCatching { ir.guardianlink.core.network.GlServices.creds.get() }.getOrNull()
        if (creds?.kind != "device") return@withLock
        val snapshot = AppPolicyCache.read()
        if (snapshot.version <= 0 && snapshot.rules.isEmpty() && snapshot.retiredPackages.isEmpty()) return@withLock

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val isOwner = runCatching { dpm?.isDeviceOwnerApp(context.packageName) == true }.getOrDefault(false)
        val admin = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        val usageAccess = UsageCollector.hasUsageAccess(context)
        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)

        // Rules deleted by the Parent become tombstones in the encrypted cache, so a previously
        // suspended app is re-enabled only by the same official Device Owner API.
        if (isOwner && dpm != null) {
            for (pkg in snapshot.retiredPackages) {
                // Never attempt to suspend/unsuspend the Child agent itself, including a legacy tombstone.
                if (pkg == context.packageName) {
                    AppPolicyCache.clearRetired(pkg)
                    continue
                }
                val result = setSuspended(dpm, admin, pkg, false)
                val status = if (result == ApplyResult.SUCCESS) "applied_unsuspended" else "failed"
                val stateKey = "retired:$pkg:${snapshot.version}"
                if (AppPolicyCache.enforcementState(stateKey) != status) {
                    if (result == ApplyResult.SUCCESS) {
                        AppPolicyCache.addEvent(pkg, "APP_RESTRICTION_ENFORCED", snapshot.version, "package_not_suspended")
                        AppPolicyCache.clearRetired(pkg)
                    } else {
                        AppPolicyCache.addEvent(pkg, "APP_RESTRICTION_FAILED", snapshot.version, "android_rejected_package_suspension")
                    }
                    AppPolicyCache.setEnforcementState(stateKey, status)
                } else if (result == ApplyResult.SUCCESS) {
                    AppPolicyCache.clearRetired(pkg)
                }
            }
        }

        for (rule in snapshot.rules) {
            if (rule.packageName == context.packageName) continue // defensive guard for pre-validation cached rules
            if ((!rule.enabled || rule.ruleType.equals("ALLOW", ignoreCase = true)) && !isOwner) continue
            val targetSuspended = if (!rule.enabled) false else desiredSuspended(rule, context, now, usageAccess)
            val key = "${rule.id}:${rule.version}"
            val reportedState: String
            val eventType: String
            val detail: String

            if (!isOwner || dpm == null) {
                reportedState = "unsupported_normal_mode"
                eventType = "APP_RESTRICTION_UNSUPPORTED"
                detail = "device_owner_required"
            } else if (rule.ruleType == "LIMITED" && !usageAccess) {
                reportedState = "unsupported_usage_access"
                eventType = "APP_RESTRICTION_UNSUPPORTED"
                detail = "usage_access_required"
            } else {
                when (setSuspended(dpm, admin, rule.packageName, targetSuspended)) {
                    ApplyResult.SUCCESS -> {
                        reportedState = if (targetSuspended) "applied_suspended" else "applied_unsuspended"
                        eventType = "APP_RESTRICTION_ENFORCED"
                        detail = if (targetSuspended) "package_suspended" else "package_not_suspended"
                    }
                    ApplyResult.FAILED -> {
                        reportedState = "failed"
                        eventType = "APP_RESTRICTION_FAILED"
                        detail = "android_rejected_package_suspension"
                    }
                }
            }

            if (AppPolicyCache.enforcementState(key) != reportedState) {
                AppPolicyCache.addEvent(rule.packageName, eventType, rule.version, detail)
                AppPolicyCache.setEnforcementState(key, reportedState)
            }
        }
        }
    }

    private enum class ApplyResult { SUCCESS, FAILED }

    private fun setSuspended(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        packageName: String,
        suspend: Boolean,
    ): ApplyResult = try {
        val failed = dpm.setPackagesSuspended(admin, arrayOf(packageName), suspend)
        if (failed.isNullOrEmpty()) ApplyResult.SUCCESS else ApplyResult.FAILED
    } catch (_: SecurityException) {
        ApplyResult.FAILED
    } catch (_: IllegalArgumentException) {
        ApplyResult.FAILED
    } catch (_: RuntimeException) {
        ApplyResult.FAILED
    }

    private fun desiredSuspended(rule: AppRuleOut, context: Context, now: java.time.ZonedDateTime, usageAccess: Boolean): Boolean {
        return when (rule.ruleType.uppercase()) {
            "ALLOW" -> false
            "RESTRICTED" -> true
            "SCHEDULED" -> rule.schedules.any { it.enabled && isWithinSchedule(it, now) }
            "LIMITED" -> {
                val limit = rule.dailyLimitSeconds ?: return false
                if (!usageAccess) return false
                val start = LocalDate.now(now.zone).atStartOfDay(now.zone).toInstant().toEpochMilli()
                val end = System.currentTimeMillis()
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
                val stats = runCatching { usm.queryAndAggregateUsageStats(start, end) }.getOrDefault(emptyMap())
                val usedMs = stats[rule.packageName]?.totalTimeInForeground ?: 0L
                usedMs >= limit * 1000L
            }
            else -> false
        }
    }

    /** ISO weekday mask: Monday bit 0 .. Sunday bit 6. Supports windows crossing midnight. */
    internal fun isWithinSchedule(schedule: AppRuleScheduleOut, now: java.time.ZonedDateTime): Boolean {
        val start = runCatching { LocalTime.parse(schedule.startTime) }.getOrNull() ?: return false
        val end = runCatching { LocalTime.parse(schedule.endTime) }.getOrNull() ?: return false
        val todayBit = 1 shl (now.dayOfWeek.value - 1)
        val previousBit = 1 shl (now.minusDays(1).dayOfWeek.value - 1)
        val current = now.toLocalTime()
        return if (start < end) {
            (schedule.dayOfWeek and todayBit) != 0 && current >= start && current < end
        } else {
            ((schedule.dayOfWeek and todayBit) != 0 && current >= start) ||
                ((schedule.dayOfWeek and previousBit) != 0 && current < end)
        }
    }
}
