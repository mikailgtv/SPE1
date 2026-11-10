package ir.guardianlink.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlErrorBanner
import ir.guardianlink.core.designsystem.GlPrimaryButton
import ir.guardianlink.core.designsystem.GlTextField

@Composable
fun AuthScreen(vm: AuthViewModel = viewModel(), onDone: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf("login") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(st.done) { if (st.done) onDone() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("👨‍👩‍👧", style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(R.string.auth_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(stringResource(R.string.auth_subtitle), style = MaterialTheme.typography.bodyMedium)

        GlErrorBanner(st.errorCode)

        GlCard {
            GlTextField(
                value = email,
                onValueChange = { email = it; vm.clearError() },
                label = stringResource(R.string.email),
                error = if (st.emailError != null) stringResource(R.string.err_email_format) else null,
            )
            GlTextField(
                value = password,
                onValueChange = { password = it; vm.clearError() },
                label = stringResource(R.string.password),
                error = if (st.passwordError != null) stringResource(R.string.err_password_short) else null,
            )
            GlPrimaryButton(
                text = stringResource(if (mode == "login") R.string.sign_in else R.string.create_account),
                onClick = {
                    if (mode == "login") vm.login(email, password) else vm.register(email, password)
                },
                loading = st.loading,
            )
            TextButton(onClick = { mode = if (mode == "login") "register" else "login"; vm.clearError() }) {
                Text(
                    stringResource(
                        if (mode == "login") R.string.need_account else R.string.have_account,
                    ),
                )
            }
        }
    }
}

@Composable
fun FamilySetupScreen(vm: AuthViewModel = viewModel(), onDone: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(st.done) { if (st.done) onDone() }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.family_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.family_subtitle), style = MaterialTheme.typography.bodyMedium)
        GlErrorBanner(st.errorCode)
        GlCard {
            GlTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.family_name),
                error = if (st.familyNameError != null) stringResource(R.string.err_family_name) else null,
            )
            GlPrimaryButton(stringResource(R.string.create_family), onClick = { vm.createFamily(name) }, loading = st.loading)
        }
    }
}
