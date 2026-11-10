package ir.guardianlink.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import ir.guardianlink.core.network.ApiException
import ir.guardianlink.core.network.DeviceOut
import ir.guardianlink.core.network.ErrCode
import ir.guardianlink.core.network.EventOut
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.LocationCurrentOut
import ir.guardianlink.core.network.GeofenceListOut
import ir.guardianlink.core.network.AppRulesOut
import ir.guardianlink.core.network.ScreenTimeReportOut
import ir.guardianlink.core.network.SettingsOut
import ir.guardianlink.core.network.SettingsUpdateReq
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardState(
    val loading: Boolean = true,
    val devices: List<DeviceOut> = emptyList(),
    val familyName: String? = null,
    val events: List<EventOut> = emptyList(),
    val notificationUnreadCount: Int = 0,
    val settings: SettingsOut? = null,
    val errorCode: String? = null,
    val confirmRemoveId: String? = null,
    val removing: Boolean = false,
    val settingsSaved: Boolean = false,
    /** Phase 3: خلاصه Screen Time به تفکیک دستگاه. */
    val screenTime: Map<String, ScreenTimeReportOut> = emptyMap(),
    /** Phase 4: وضعیت موقعیت به تفکیک دستگاه. */
    val locations: Map<String, LocationCurrentOut> = emptyMap(),
    /** Phase 5: safe-zone summary per device. */
    val geofences: Map<String, GeofenceListOut> = emptyMap(),
    /** Phase 6: per-device app rules/capability summary. */
    val appRules: Map<String, AppRulesOut> = emptyMap(),
)

class DashboardViewModel : ViewModel() {
    private var lastGeofenceFetchMs = 0L
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val fam = runCatching { GlServices.api.familyMe() }.getOrNull()
                val devices = GlServices.api.devices()
                val events = runCatching { GlServices.api.familyEvents(10) }.getOrNull().orEmpty()
                val notificationPage = runCatching { GlServices.api.notifications(100) }.getOrNull()
                val notificationFeed = notificationPage?.items.orEmpty()
                val unreadNotifications = notificationPage?.unreadCount ?: _state.value.notificationUnreadCount
                val settings = runCatching { GlServices.api.getSettings() }.getOrNull()
                val screenTime = devices.associate { d ->
                    d.id to runCatching { GlServices.api.screenTimeReport(d.id) }.getOrNull()
                }.filterValues { it != null }.mapValues { it.value!! }
                val locations = devices.associate { d ->
                    d.id to runCatching { GlServices.api.locationCurrent(d.id) }.getOrNull()
                }.filterValues { it != null }.mapValues { it.value!! }
                val geofences = if (System.currentTimeMillis() - lastGeofenceFetchMs >= 120_000L || _state.value.geofences.isEmpty()) {
                    lastGeofenceFetchMs = System.currentTimeMillis()
                    devices.associate { d ->
                        d.id to runCatching { GlServices.api.geofences(d.id) }.getOrNull()
                    }.filterValues { it != null }.mapValues { it.value!! }
                } else {
                    _state.value.geofences.filterKeys { deviceId -> devices.any { it.id == deviceId } }
                }
                val appRules = devices.associate { d ->
                    d.id to runCatching { GlServices.api.appRules(d.id) }.getOrNull()
                }.filterValues { it != null }.mapValues { it.value!! }
                _state.value = _state.value.copy(
                    loading = false,
                    devices = devices,
                    familyName = fam?.family?.name,
                    events = events,
                    notificationUnreadCount = unreadNotifications,
                    settings = settings,
                    errorCode = null,
                    screenTime = screenTime,
                    locations = locations,
                    geofences = geofences,
                    appRules = appRules,
                )
                notifyParentFeed(notificationFeed)
            } catch (e: ApiException) {
                _state.value = _state.value.copy(loading = false, errorCode = e.code)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, errorCode = ErrCode.NETWORK)
            }
        }
    }

    /** هشدار محلی Parent فقط از projection اعلان‌های سرور؛ رویدادهای audit مستقیماً notify نمی‌شوند. */
    private fun notifyParentFeed(notifications: List<ir.guardianlink.core.network.NotificationOut>) {
        if (notifications.isEmpty()) return
        val ctx = appContext ?: return
        viewModelScope.launch {
            var last = GlServices.prefs.getString("parent_notification_cursor_ts")
            if (last == null) {
                last = GlServices.prefs.getString("parent_last_event_ts")
                if (last == null) {
                    val baseline = "${java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)}|ffffffff-ffff-ffff-ffff-ffffffffffff"
                    GlServices.prefs.putString("parent_notification_cursor_ts", baseline)
                    return@launch // Historical backfill stays in the Center without a first-run notification burst.
                }
            }
            val prefs = runCatching { GlServices.api.notificationPreferences() }.getOrNull()
                ?: ir.guardianlink.core.network.NotificationPreferencesOut(timezone = java.time.ZoneId.systemDefault().id)
            val newest = ParentNotifier.notifyFeed(ctx, notifications, last, prefs)
            if (newest != null && newest != last) GlServices.prefs.putString("parent_notification_cursor_ts", newest)
        }
    }

    var appContext: Context? = null

    fun askRemove(id: String) {
        _state.value = _state.value.copy(confirmRemoveId = id)
    }

    fun cancelRemove() {
        _state.value = _state.value.copy(confirmRemoveId = null)
    }

    fun confirmRemove() {
        val id = _state.value.confirmRemoveId ?: return
        _state.value = _state.value.copy(removing = true)
        viewModelScope.launch {
            try {
                GlServices.api.removeDevice(id)
                AppLog.info(Cat.DEVICE, "device removed", "device" to id)
                _state.value = _state.value.copy(removing = false, confirmRemoveId = null)
                refresh()
            } catch (e: ApiException) {
                _state.value = _state.value.copy(removing = false, confirmRemoveId = null, errorCode = e.code)
            } catch (e: Exception) {
                _state.value = _state.value.copy(removing = false, confirmRemoveId = null, errorCode = ErrCode.NETWORK)
            }
        }
    }

    fun saveSettings(low: Int, critical: Int, delayedSec: Int, offlineSec: Int, intervalSec: Int) {
        _state.value = _state.value.copy(loading = true, settingsSaved = false)
        viewModelScope.launch {
            try {
                val s = GlServices.api.putSettings(
                    SettingsUpdateReq(low, critical, delayedSec, offlineSec, intervalSec),
                )
                _state.value = _state.value.copy(loading = false, settings = s, settingsSaved = true)
                refresh()
            } catch (e: ApiException) {
                _state.value = _state.value.copy(loading = false, errorCode = e.code)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, errorCode = ErrCode.NETWORK)
            }
        }
    }
}
