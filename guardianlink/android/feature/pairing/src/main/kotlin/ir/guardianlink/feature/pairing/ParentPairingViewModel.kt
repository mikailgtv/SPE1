package ir.guardianlink.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import ir.guardianlink.core.network.ApiException
import ir.guardianlink.core.network.ErrCode
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.PairingCreateOut
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

data class ParentPairingState(
    val loading: Boolean = true,
    val session: PairingCreateOut? = null,
    val remainSec: Long = 0,
    val errorCode: String? = null,
)

class ParentPairingViewModel : ViewModel() {
    private val _state = MutableStateFlow(ParentPairingState())
    val state: StateFlow<ParentPairingState> = _state.asStateFlow()

    init {
        create()
    }

    fun create() {
        _state.value = ParentPairingState(loading = true)
        viewModelScope.launch {
            try {
                val s = GlServices.api.createPairing()
                AppLog.info(Cat.PAIRING, "pairing session created", "session" to s.sessionId)
                _state.value = ParentPairingState(session = s)
                tick()
            } catch (e: ApiException) {
                _state.value = ParentPairingState(loading = false, errorCode = e.code)
            } catch (e: Exception) {
                _state.value = ParentPairingState(loading = false, errorCode = ErrCode.NETWORK)
            }
        }
    }

    private suspend fun tick() {
        while (true) {
            val s = _state.value.session ?: return
            val exp = runCatching { OffsetDateTime.parse(s.expiresAt) }.getOrNull() ?: return
            val remain = ChronoUnit.SECONDS.between(OffsetDateTime.now(), exp).coerceAtLeast(0)
            _state.value = _state.value.copy(loading = false, remainSec = remain)
            if (remain == 0L) {
                _state.value = _state.value.copy(errorCode = ErrCode.PAIRING_EXPIRED)
                return
            }
            delay(1000)
        }
    }
}
