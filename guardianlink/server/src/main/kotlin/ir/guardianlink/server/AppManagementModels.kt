package ir.guardianlink.server

import java.time.OffsetDateTime
import java.util.UUID

// ---------- Child → Server: real launcher/package inventory snapshot + capability report ----------
data class AppInventoryItemIn(
    val packageName: String,
    val appName: String,
    val versionName: String? = null,
    val versionCode: Long = 0,
    val installedAtMs: Long? = null,
    val isSystemApp: Boolean = false,
    val category: String? = null,
)

data class AppRestrictionEventIn(
    val clientEventId: String,
    val packageName: String,
    val eventType: String,
    val ruleVersion: Long,
    val clientTs: Long,
    val detail: String? = null,
)

data class AppManagementSyncIn(
    /** true means the accompanying list is a complete snapshot, not a delta. */
    val inventoryComplete: Boolean = false,
    val inventory: List<AppInventoryItemIn>? = null,
    val managementMode: String? = null, // normal | managed | unsupported | unknown; informational only
    val usageAccessGranted: Boolean? = null,
    val timeZoneId: String? = null,
    val events: List<AppRestrictionEventIn>? = null,
)

// ---------- Parent → Server: rule CRUD ----------
data class AppRuleScheduleReq(
    val daysOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val enabled: Boolean = true,
)

data class AppRuleReq(
    val packageName: String,
    val ruleType: String,
    val dailyLimitSeconds: Int? = null,
    val nearPct: Int? = null,
    val criticalPct: Int? = null,
    val enabled: Boolean? = null,
    val schedules: List<AppRuleScheduleReq>? = null,
    val expectedVersion: Long? = null,
)

data class AppRuleScheduleOut(
    val id: UUID,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val enabled: Boolean,
)

data class AppRuleOut(
    val id: UUID,
    val deviceId: UUID,
    val packageName: String,
    val appName: String,
    val ruleType: String,
    val dailyLimitSeconds: Int?,
    val nearPct: Int,
    val criticalPct: Int,
    val enabled: Boolean,
    val version: Long,
    val lastEnforcementStatus: String,
    val lastEnforcementAt: String?,
    val schedules: List<AppRuleScheduleOut>,
    val createdAt: String,
    val updatedAt: String,
)

data class AppRulesOut(
    val ruleVersion: Long,
    val managementMode: String,
    val usageAccessGranted: Boolean,
    val rules: List<AppRuleOut>,
)

data class AppRulesSyncOut(
    val ruleVersion: Long,
    val rules: List<AppRuleOut>,
)

data class AppCatalogOut(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val installedAt: String?,
    val isSystemApp: Boolean,
    val category: String,
    val installed: Boolean,
    val usageTodaySeconds: Int?,
    val usageWeekSeconds: Int?,
    val rule: AppRuleOut?,
)

data class AppCatalogListOut(
    val apps: List<AppCatalogOut>,
    val managementMode: String,
    val enforcementCapability: String,
    val usageAccessGranted: Boolean,
    val ruleVersion: Long,
    val inventoryUpdatedAt: String?,
    val policyLastSyncAt: String?,
)

data class AppUsageHistoryDayOut(val date: String, val usageSeconds: Int)
data class AppUsageHistoryOut(val packageName: String, val days: List<AppUsageHistoryDayOut>, val timezone: String)

data class AppRestrictionEventOut(
    val id: UUID,
    val packageName: String,
    val eventType: String,
    val timestamp: OffsetDateTime,
    val metadata: Map<String, String>,
)
data class AppRestrictionEventsOut(val events: List<AppRestrictionEventOut>, val count: Int)

// ---------- DB records ----------
data class AppInventoryRow(
    val deviceId: UUID,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val installedAt: OffsetDateTime?,
    val isSystemApp: Boolean,
    val category: String,
    val installed: Boolean,
    val updatedAt: OffsetDateTime,
)

data class AppRuleRow(
    val id: UUID,
    val deviceId: UUID,
    val packageName: String,
    val ruleType: String,
    val dailyLimitSeconds: Int?,
    val nearPct: Int,
    val criticalPct: Int,
    val enabled: Boolean,
    val createdBy: UUID,
    val version: Long,
    val lastEnforcementStatus: String,
    val lastEnforcementAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class AppRuleScheduleRow(
    val id: UUID,
    val ruleId: UUID,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val enabled: Boolean,
)

data class AppManagementStateRow(
    val deviceId: UUID,
    val ruleVersion: Long,
    val managementMode: String,
    val usageAccessGranted: Boolean,
    val timeZoneId: String,
    val inventoryUpdatedAt: OffsetDateTime?,
    val lastSyncAt: OffsetDateTime?,
)

data class AppRestrictionEventRow(
    val id: UUID,
    val deviceId: UUID,
    val packageName: String,
    val eventType: String,
    val ts: OffsetDateTime,
    val metadata: String,
)
