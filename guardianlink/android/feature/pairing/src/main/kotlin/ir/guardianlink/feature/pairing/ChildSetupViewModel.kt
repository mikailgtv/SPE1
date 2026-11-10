package ir.guardianlink.feature.pairing

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import ir.guardianlink.core.common.Redact
import ir.guardianlink.core.common.Validators
import ir.guardianlink.core.network.ApiException
import ir.guardianlink.core.network.ConfirmReq
import ir.guardianlink.core.network.CredentialStore
import ir.guardianlink.core.network.ErrCode
import ir.guardianlink.core.network.ExchangeReq
import ir.guardianlink.core.network.ExchangeOut
import ir.guardianlink.core.network.GlServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChildSetupState(
    val loading: Boolean = false,
    val errorCode: String? = null,
    val codeError: Boolean = false,
    val confirm: ExchangeOut? = null,
    val connected: Boolean = false,
)

class ChildSetupViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChildSetupState())
    val state: StateFlow<ChildSetupState> = _state.asStateFlow()

    private var exchangeToken: String? = null
    private var installId: String = ""

    fun submitCode(rawCode: String) {
        val code = Validators.normalizePairingCode(rawCode)
        if (!Validators.isValidPairingCode(code)) {
            _state.value = _state.value.copy(codeError = true)
            return
        }
        _state.value = _state.value.copy(loading = true, errorCode = null, codeError = false)
        viewModelScope.launch {
            try {
                installId = GlServices.prefs.installId()
                val ex = GlServices.api.exchange(
                    ExchangeReq(
                        code = code,
                        installId = installId,
                        deviceName = Build.MODEL,
                        manufacturer = Build.MANUFACTURER,
                        model = Build.MODEL,
                        androidVersion = Build.VERSION.RELEASE,
                        appVersion = appVersion(),
                    ),
                )
                AppLog.info(Cat.PAIRING, "exchange ok", "family" to ex.familyName)
                exchangeToken = ex.exchangeToken
                _state.value = _state.value.copy(loading = false, confirm = ex)
            } catch (e: ApiException) {
                AppLog.warn(Cat.PAIRING, "exchange failed", "code" to e.code, "input" to Redact.code(code))
                _state.value = _state.value.copy(loading = false, errorCode = e.code)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, errorCode = ErrCode.NETWORK)
            }
        }
    }

    fun confirm(deviceName: String) {
        val token = exchangeToken ?: return
        if (!Validators.isValidDeviceName(deviceName)) {
            _state.value = _state.value.copy(codeError = true)
            return
        }
        _state.value = _state.value.copy(loading = true, errorCode = null)
        viewModelScope.launch {
            try {
                val cf = GlServices.api.confirm(
                    ConfirmReq(
                        exchangeToken = token,
                        installId = installId,
                        deviceName = deviceName.trim(),
                        manufacturer = Build.MANUFACTURER,
                        model = Build.MODEL,
                        androidVersion = Build.VERSION.RELEASE,
                        appVersion = appVersion(),
                    ),
                )
                val t = GlServices.api.deviceAuth(cf.deviceId, cf.deviceSecret)
                GlServices.creds.put(
                    CredentialStore.Creds(t.accessToken, t.refreshToken, "device", cf.deviceId),
                )
                GlServices.prefs.putString("family_name", _state.value.confirm?.familyName)
                GlServices.prefs.putString("device_name", deviceName.trim())
                AppLog.info(Cat.PAIRING, "device paired and authenticated", "device" to cf.deviceId)
                _state.value = _state.value.copy(loading = false, connected = true)
            } catch (e: ApiException) {
                _state.value = _state.value.copy(loading = false, errorCode = e.code)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, errorCode = ErrCode.NETWORK)
            }
        }
    }

    private fun appVersion(): String =
        try {
            "0.1.0"
        } catch (_: Exception) {
            "0.1.0"
        }
}
