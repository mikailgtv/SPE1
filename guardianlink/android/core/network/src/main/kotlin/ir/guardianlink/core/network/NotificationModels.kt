package ir.guardianlink.core.network

data class NotificationOut(
    val id: String = "", val deviceId: String = "", val eventId: String = "", val eventType: String = "",
    val category: String = "", val severity: String = "", val titleKey: String = "", val bodyKey: String = "",
    val metadata: String = "{}", val occurredAt: String = "", val status: String = "OPEN",
    val acknowledgedAt: String? = null, val resolvedAt: String? = null, val isRead: Boolean = false,
    val deviceName: String = "",
)
data class NotificationPageOut(val items: List<NotificationOut> = emptyList(), val nextBefore: String? = null, val unreadCount: Int = 0)
data class UnreadCountOut(val unread: Int = 0)
data class NotificationPreferencesOut(
    val enabledCategories: String = "{}", val quietHoursEnabled: Boolean = false,
    val quietStart: String = "22:00", val quietEnd: String = "07:00", val timezone: String = "UTC",
    val criticalDuringQuietHours: Boolean = true, val dailySummaryEnabled: Boolean = false,
    val cooldownMinutes: Int = 10,
)
