package ir.guardianlink.core.network

// ---------- Phase 3: Screen Time & App Usage ----------
// Child → Server
data class UsageAppIn(
    val packageName: String,
    val appName: String?,
    val category: String?,
    val usageMinutes: Int,
    val launchCount: Int?,
    val firstUsedAt: Long?,
    val lastUsedAt: Long?,
    val iconBase64: String? = null,
    val iconVersion: Int? = null,
)

data class UsageDayIn(
    val date: String,
    val apps: List<UsageAppIn>,
)

data class UsageUploadReq(val days: List<UsageDayIn>)

// Server → Parent
data class AppUsageOut(
    val packageName: String,
    val appName: String,
    val category: String,
    val usageMinutes: Int,
    val launchCount: Int,
    val firstUsedAt: String?,
    val lastUsedAt: String?,
    val percent: Double,
)

data class UsageTodayOut(
    val date: String,
    val totalMinutes: Int,
    val limitMinutes: Int?,
    val limitEnabled: Boolean,
    val remainingMinutes: Int?,
    val state: String,
    val topApps: List<AppUsageOut>,
)

data class UsageDayTotal(val date: String, val totalMinutes: Int)

data class UsageWeeklyOut(
    val days: List<UsageDayTotal>,
    val totalMinutes: Int,
    val averageMinutes: Int,
    val mostUsedDay: String?,
    val leastUsedDay: String?,
)

data class AppDetailOut(
    val packageName: String,
    val appName: String,
    val category: String,
    val todayMinutes: Int,
    val todayLaunches: Int,
    val lastUsedAt: String?,
    val week: List<UsageDayTotal>,
    val weekLaunches: Int,
)

data class CategoryTotal(val category: String, val minutes: Int)

data class DailyReportOut(
    val date: String,
    val totalMinutes: Int,
    val mostUsedApp: AppUsageOut?,
    val appsUsed: Int,
    val sessions: Int,
    val categories: List<CategoryTotal>,
)

data class ScreenTimeSettingsOut(
    val deviceId: String,
    val dailyLimitMinutes: Int,
    val nearPct: Int,
    val enabled: Boolean,
)

data class ScreenTimeSettingsReq(
    val dailyLimitMinutes: Int?,
    val nearPct: Int?,
    val enabled: Boolean?,
)

data class ScheduleOut(
    val id: String,
    val name: String,
    val startTime: String,
    val endTime: String,
    val daysOfWeek: Int,
    val enabled: Boolean,
)

data class ScheduleReq(
    val name: String?,
    val startTime: String?,
    val endTime: String?,
    val daysOfWeek: Int?,
    val enabled: Boolean?,
)

data class ScreenTimeReportOut(
    val today: UsageTodayOut,
    val weekly: UsageWeeklyOut,
    val settings: ScreenTimeSettingsOut,
    val schedules: List<ScheduleOut>,
)
