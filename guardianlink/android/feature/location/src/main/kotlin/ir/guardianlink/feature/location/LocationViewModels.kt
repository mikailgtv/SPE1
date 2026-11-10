package ir.guardianlink.feature.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.guardianlink.core.network.ApiException
import ir.guardianlink.core.network.ErrCode
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.LocationCurrentOut
import ir.guardianlink.core.network.LocationDeleteOut
import ir.guardianlink.core.network.LocationFixOut
import ir.guardianlink.core.network.LocationHistoryOut
import ir.guardianlink.core.network.LocationSettingsOut
import ir.guardianlink.core.network.LocationSettingsReq
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

private fun Throwable.code(): String = if (this is ApiException) this.code else ErrCode.NETWORK

// ---------------- Current location + settings (card/map/settings) ----------------
data class LocationUi(
    val loading: Boolean = true,
    val current: LocationCurrentOut? = null,
    val settings: LocationSettingsOut? = null,
    val saved: Boolean = false,
    val error: String? = null,
)

class LocationViewModel : ViewModel() {
    private val _ui = MutableStateFlow(LocationUi())
    val ui: StateFlow<LocationUi> = _ui.asStateFlow()

    fun load(deviceId: String) {
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            _ui.value = try {
                LocationUi(
                    loading = false,
                    current = GlServices.api.locationCurrent(deviceId),
                    settings = GlServices.api.locationSettings(deviceId),
                )
            } catch (e: Exception) {
                LocationUi(loading = false, error = e.code())
            }
        }
    }

    fun saveSharing(deviceId: String, enabled: Boolean) {
        viewModelScope.launch {
            _ui.value = try {
                val s = GlServices.api.saveLocationSettings(deviceId, LocationSettingsReq(enabled, null))
                _ui.value.copy(settings = s, saved = true, error = null)
            } catch (e: Exception) {
                _ui.value.copy(saved = false, error = e.code())
            }
            load(deviceId)
        }
    }

    fun saveRetention(deviceId: String, days: Int) {
        viewModelScope.launch {
            _ui.value = try {
                val s = GlServices.api.saveLocationSettings(deviceId, LocationSettingsReq(null, days))
                _ui.value.copy(settings = s, saved = true, error = null)
            } catch (e: Exception) {
                _ui.value.copy(saved = false, error = e.code())
            }
        }
    }

    fun deleteHistory(deviceId: String, scope: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val out: LocationDeleteOut = GlServices.api.deleteLocationHistory(deviceId, scope)
                onDone(out.deleted)
                load(deviceId)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.code())
                onDone(-1)
            }
        }
    }
}

// ---------------- History ----------------
enum class HistoryFilter { ALL, TODAY, YESTERDAY }

data class HistoryUi(
    val loading: Boolean = true,
    val points: List<LocationFixOut> = emptyList(),
    val retentionDays: Int = 30,
    val sharingEnabled: Boolean = true,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val error: String? = null,
)

class LocationHistoryViewModel : ViewModel() {
    private val _ui = MutableStateFlow(HistoryUi())
    val ui: StateFlow<HistoryUi> = _ui.asStateFlow()

    fun load(deviceId: String, filter: HistoryFilter = _ui.value.filter) {
        _ui.value = _ui.value.copy(loading = true, filter = filter)
        viewModelScope.launch {
            _ui.value = try {
                val zone = ZoneId.systemDefault()
                val (from, to) = when (filter) {
                    HistoryFilter.ALL -> null to null
                    HistoryFilter.TODAY ->
                        LocalDate.now().atStartOfDay(zone).toOffsetDateTime().toString() to null
                    HistoryFilter.YESTERDAY ->
                        LocalDate.now().minusDays(1).atStartOfDay(zone).toOffsetDateTime().toString() to
                            LocalDate.now().atStartOfDay(zone).toOffsetDateTime().toString()
                }
                val h: LocationHistoryOut = GlServices.api.locationHistory(deviceId, from, to, 500)
                HistoryUi(
                    loading = false,
                    points = h.points,
                    retentionDays = h.retentionDays,
                    sharingEnabled = h.sharingEnabled,
                    filter = filter,
                )
            } catch (e: Exception) {
                HistoryUi(loading = false, filter = filter, error = e.code())
            }
        }
    }

    fun setFilter(deviceId: String, filter: HistoryFilter) = load(deviceId, filter)
}

/** «۲ دقیقه پیش» — نمایش صادقانه‌ی کهنگی موقعیت. */
fun locationAgeFa(iso: String?): String {
    val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return "—"
    val min = java.time.Duration.between(t, OffsetDateTime.now()).toMinutes()
    return when {
        min < 1 -> "هم‌اکنون"
        min < 60 -> "${fa(min.toInt())} دقیقه پیش"
        min < 1440 -> "${fa((min / 60).toInt())} ساعت پیش"
        else -> "${fa((min / 1440).toInt())} روز پیش"
    }
}

private fun fa(v: Int): String = ir.guardianlink.core.designsystem.faNum(v)

fun coordFa(v: Double): String = ir.guardianlink.core.designsystem.faNum(String.format("%.5f", v))
