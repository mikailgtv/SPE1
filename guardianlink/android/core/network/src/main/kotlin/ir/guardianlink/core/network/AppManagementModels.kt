package ir.guardianlink.core.network

// Phase 6 API payloads. These intentionally contain package metadata and usage totals only.
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
    val inventoryComplete: Boolean = false,
    val inventory: List<AppInventoryItemIn>? = null,
    val managementMode: String,
    val usageAccessGranted: Boolean,
    val timeZoneId: String,
    val events: List<AppRestrictionEventIn> = emptyList(),
)

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
    val id: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val enabled: Boolean,
)

data class AppRuleOut(
    val id: String,
    val deviceId: String,
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

data class AppRulesSyncOut(val ruleVersion: Long, val rules: List<AppRuleOut>)

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
    val id: String,
    val packageName: String,
    val eventType: String,
    val timestamp: String,
    val metadata: Map<String, String>,
)
data class AppRestrictionEventsOut(val events: List<AppRestrictionEventOut>, val count: Int)
