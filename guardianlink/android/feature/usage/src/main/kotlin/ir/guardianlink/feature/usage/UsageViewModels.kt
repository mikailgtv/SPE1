package ir.guardianlink.feature.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.guardianlink.core.network.AppDetailOut
import ir.guardianlink.core.network.AppUsageOut
import ir.guardianlink.core.network.DailyReportOut
import ir.guardianlink.core.network.ErrCode
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.ScheduleOut
import ir.guardianlink.core.network.ScheduleReq
import ir.guardianlink.core.network.ScreenTimeReportOut
import ir.guardianlink.core.network.ScreenTimeSettingsOut
import ir.guardianlink.core.network.ScreenTimeSettingsReq
import ir.guardianlink.core.network.UsageWeeklyOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** حالت‌های مرتب‌سازی لیست مصرف اپ‌ها. */
enum class SortMode { MOST_USED, LEAST_USED, ALPHABETICAL }

private fun Throwable.toCode(): String =
    if (this is ir.guardianlink.core.network.ApiException) this.code else ErrCode.NETWORK

// ---------------- Screen Time home ----------------
data class ScreenTimeUi(
    val loading: Boolean = true,
    val report: ScreenTimeReportOut? = null,
    val error: String? = null,
)

class ScreenTimeViewModel : ViewModel() {
    private val _ui = MutableStateFlow(ScreenTimeUi())
    val ui: StateFlow<ScreenTimeUi> = _ui.asStateFlow()

    fun load(deviceId: String) {
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            _ui.value = try {
                ScreenTimeUi(loading = false, report = GlServices.api.screenTimeReport(deviceId))
            } catch (e: Exception) {
                ScreenTimeUi(loading = false, error = e.toCode())
            }
        }
    }
}

// ---------------- App usage list ----------------
data class AppUsageUi(
    val loading: Boolean = true,
    val apps: List<AppUsageOut> = emptyList(),
    val sort: SortMode = SortMode.MOST_USED,
    val totalMinutes: Int = 0,
    val error: String? = null,
)

class AppUsageViewModel : ViewModel() {
    private val _ui = MutableStateFlow(AppUsageUi())
    val ui: StateFlow<AppUsageUi> = _ui.asStateFlow()
    private var currentDevice: String? = null

    fun load(deviceId: String, sort: SortMode = _ui.value.sort, date: String? = null) {
        currentDevice = deviceId
        _ui.value = _ui.value.copy(loading = true, sort = sort)
        viewModelScope.launch {
            _ui.value = try {
                val apps = GlServices.api.usageApps(deviceId, date)
                val sorted = when (sort) {
                    SortMode.MOST_USED -> apps.sortedByDescending { it.usageMinutes }
                    SortMode.LEAST_USED -> apps.sortedBy { it.usageMinutes }
                    SortMode.ALPHABETICAL -> apps.sortedBy { it.appName.lowercase() }
                }
                AppUsageUi(
                    loading = false,
                    apps = sorted,
                    sort = sort,
                    totalMinutes = apps.sumOf { it.usageMinutes },
                )
            } catch (e: Exception) {
                AppUsageUi(loading = false, sort = sort, error = e.toCode())
            }
        }
    }

    fun setSort(sort: SortMode) {
        currentDevice?.let { load(it, sort) }
    }
}

// ---------------- App detail ----------------
data class AppDetailUi(
    val loading: Boolean = true,
    val detail: AppDetailOut? = null,
    val error: String? = null,
)

class AppDetailViewModel : ViewModel() {
    private val _ui = MutableStateFlow(AppDetailUi())
    val ui: StateFlow<AppDetailUi> = _ui.asStateFlow()

    fun load(deviceId: String, pkg: String) {
        _ui.value = AppDetailUi()
        viewModelScope.launch {
            _ui.value = try {
                AppDetailUi(loading = false, detail = GlServices.api.appDetail(deviceId, pkg))
            } catch (e: Exception) {
                AppDetailUi(loading = false, error = e.toCode())
            }
        }
    }
}

// ---------------- Reports ----------------
data class ReportsUi(
    val loading: Boolean = true,
    val daily: DailyReportOut? = null,
    val weekly: UsageWeeklyOut? = null,
    val error: String? = null,
)

class ReportsViewModel : ViewModel() {
    private val _ui = MutableStateFlow(ReportsUi())
    val ui: StateFlow<ReportsUi> = _ui.asStateFlow()

    fun load(deviceId: String, date: String? = null) {
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            _ui.value = try {
                ReportsUi(
                    loading = false,
                    daily = GlServices.api.dailyReport(deviceId, date),
                    weekly = GlServices.api.usageWeekly(deviceId),
                )
            } catch (e: Exception) {
                ReportsUi(loading = false, error = e.toCode())
            }
        }
    }
}

// ---------------- Settings & schedules ----------------
data class StSettingsUi(
    val loading: Boolean = true,
    val settings: ScreenTimeSettingsOut? = null,
    val schedules: List<ScheduleOut> = emptyList(),
    val saved: Boolean = false,
    val error: String? = null,
)

class ScreenTimeSettingsViewModel : ViewModel() {
    private val _ui = MutableStateFlow(StSettingsUi())
    val ui: StateFlow<StSettingsUi> = _ui.asStateFlow()

    fun load(deviceId: String) {
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            _ui.value = try {
                val r = GlServices.api.screenTimeReport(deviceId)
                StSettingsUi(loading = false, settings = r.settings, schedules = r.schedules)
            } catch (e: Exception) {
                StSettingsUi(loading = false, error = e.toCode())
            }
        }
    }

    fun saveSettings(deviceId: String, minutes: Int, nearPct: Int, enabled: Boolean) {
        viewModelScope.launch {
            _ui.value = try {
                val s = GlServices.api.saveScreenTimeSettings(deviceId, ScreenTimeSettingsReq(minutes, nearPct, enabled))
                _ui.value.copy(settings = s, saved = true, error = null)
            } catch (e: Exception) {
                _ui.value.copy(saved = false, error = e.toCode())
            }
        }
    }

    fun addSchedule(deviceId: String, name: String, start: String, end: String, days: Int, enabled: Boolean) {
        viewModelScope.launch {
            _ui.value = try {
                GlServices.api.createSchedule(deviceId, ScheduleReq(name, start, end, days, enabled))
                _ui.value.copy(saved = true, error = null)
            } catch (e: Exception) {
                _ui.value.copy(saved = false, error = e.toCode())
            }
            load(deviceId)
        }
    }

    fun updateSchedule(deviceId: String, id: String, req: ScheduleReq) {
        viewModelScope.launch {
            _ui.value = try {
                GlServices.api.updateSchedule(deviceId, id, req)
                _ui.value.copy(saved = true, error = null)
            } catch (e: Exception) {
                _ui.value.copy(saved = false, error = e.toCode())
            }
            load(deviceId)
        }
    }
}
