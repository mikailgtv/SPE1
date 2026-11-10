package ir.guardianlink.core.network

data class WebSafetySyncIn(
    val reportedMode: String,
    val reportedStatus: String,
    val reportedReason: String,
    val dnsStatus: String = "UNSUPPORTED",
    val vpnStatus: String = "UNSUPPORTED",
    val timezoneId: String,
    val events: List<WebSafetyEventIn> = emptyList(),
)
data class WebSafetyEventIn(
    val clientEventId: String,
    val eventType: String,
    val reasonCode: String,
    val clientTs: Long,
)
data class WebSafetySettingsReq(
    val enabled: Boolean,
    val defaultPolicy: String,
    val requestedMode: String,
    val alertsEnabled: Boolean,
    val expectedVersion: Long,
)
data class WebSafetyRuleReq(
    val ruleType: String = "DOMAIN",
    val domain: String? = null,
    val scope: String = "DOMAIN_AND_SUBDOMAINS",
    val category: String? = null,
    val action: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val schedules: List<WebSafetyScheduleReq> = emptyList(),
    val expectedVersion: Long? = null,
)
data class WebSafetyScheduleReq(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val action: String,
    val timezoneId: String? = null,
    val enabled: Boolean = true,
)
data class WebSafetyOverrideReq(
    val domain: String,
    val scope: String = "DOMAIN_AND_SUBDOMAINS",
    val action: String = "ALLOW",
    val durationSeconds: Long,
)
data class WebSafetyRuleOut(
    val id: String, val deviceId: String, val ruleType: String, val domain: String?, val scope: String,
    val category: String?, val action: String, val enabled: Boolean, val priority: Int,
    val version: Long, val categoryEnforceable: Boolean, val schedules: List<WebSafetyScheduleOut>,
    val createdAt: String, val updatedAt: String,
)
data class WebSafetyScheduleOut(
    val id: String, val dayOfWeek: Int, val startTime: String, val endTime: String,
    val action: String, val timezoneId: String?, val enabled: Boolean,
)
data class WebSafetyOverrideOut(
    val id: String, val domain: String, val scope: String, val action: String, val expiresAt: String,
)
data class WebSafetySettingsOut(
    val enabled: Boolean, val defaultPolicy: String, val requestedMode: String, val alertsEnabled: Boolean,
    val ruleVersion: Long, val reportedMode: String, val reportedStatus: String, val reportedReason: String,
    val dnsStatus: String, val vpnStatus: String, val timezoneId: String,
    val lastReportedAt: String?, val rules: List<WebSafetyRuleOut>,
    val activeOverrides: List<WebSafetyOverrideOut>, val categoryProviderAvailable: Boolean = false,
)
data class WebSafetySyncOut(
    val enabled: Boolean, val defaultPolicy: String, val requestedMode: String, val ruleVersion: Long,
    val rules: List<WebSafetyRuleOut>, val activeOverrides: List<WebSafetyOverrideOut>,
)
data class WebSafetyEventOut(
    val id: String, val ruleId: String?, val eventType: String, val ts: String,
    val reasonCode: String, val source: String,
)
data class WebSafetyEventsOut(val events: List<WebSafetyEventOut>)
