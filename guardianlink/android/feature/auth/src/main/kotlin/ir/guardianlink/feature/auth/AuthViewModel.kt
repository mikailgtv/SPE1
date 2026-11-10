package ir.guardianlink.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import ir.guardianlink.core.common.Redact
import ir.guardianlink.core.common.Validators
import ir.guardianlink.core.network.ApiException
import ir.guardianlink.core.network.CredentialStore
import ir.guardianlink.core.network.ErrCode
import ir.guardianlink.core.network.GlServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val errorCode: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val familyNameError: String? = null,
    val done: Boolean = false,
)

class AuthViewModel : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun clearError() {
        _state.value = _state.value.copy(errorCode = null)
    }

    fun login(email: String, password: String) {
        val emailErr = if (Validators.isValidEmail(email)) null else "email"
        val passErr = if (password.isNotEmpty()) null else "password"
        if (emailErr != null || passErr != null) {
            _state.value = _state.value.copy(emailError = emailErr, passwordError = passErr)
            return
        }
        run {
            _state.value = _state.value.copy(loading = true, errorCode = null)
            viewModelScope.launch {
                try {
                    val t = GlServices.api.login(email, password)
                    GlServices.creds.put(CredentialStore.Creds(t.accessToken, t.refreshToken, "user"))
                    AppLog.info(Cat.AUTH, "login ok", "email" to Redact.email(email))
                    _state.value = _state.value.copy(loading = false, done = true)
                } catch (e: ApiException) {
                    AppLog.warn(Cat.AUTH, "login failed", "code" to e.code)
                    _state.value = _state.value.copy(loading = false, errorCode = e.code)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(loading = false, errorCode = ErrCode.NETWORK)
                }
            }
        }
    }

    fun register(email: String, password: String) {
        val emailErr = if (Validators.isValidEmail(email)) null else "email"
        val passErr = if (Validators.isValidPassword(password)) null else "password"
        if (emailErr != null || passErr != null) {
            _state.value = _state.value.copy(emailError = emailErr, passwordError = passErr)
            return
        }
        _state.value = _state.value.copy(loading = true, errorCode = null)
        viewModelScope.launch {
            try {
                val t = GlServices.api.register(email, password)
                GlServices.creds.put(CredentialStore.Creds(t.accessToken, t.refreshToken, "user"))
                AppLog.info(Cat.AUTH, "register ok", "email" to Redact.email(email))
                _state.value = _state.value.copy(loading = false, done = true)
            } catch (e: ApiException) {
                _state.value = _state.value.copy(loading = false, errorCode = e.code)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, errorCode = ErrCode.NETWORK)
            }
        }
    }

    fun createFamily(name: String) {
        if (!Validators.isValidFamilyName(name)) {
            _state.value = _state.value.copy(familyNameError = "name")
            return
        }
        _state.value = _state.value.copy(loading = true, errorCode = null)
        viewModelScope.launch {
            try {
                GlServices.api.createFamily(name)
                AppLog.info(Cat.AUTH, "family created")
                _state.value = _state.value.copy(loading = false, done = true)
            } catch (e: ApiException) {
                _state.value = _state.value.copy(loading = false, errorCode = e.code)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, errorCode = ErrCode.NETWORK)
            }
        }
    }
}
