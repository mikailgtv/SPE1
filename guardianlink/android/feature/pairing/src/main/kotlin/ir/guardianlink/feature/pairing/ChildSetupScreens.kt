package ir.guardianlink.feature.pairing

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlErrorBanner
import ir.guardianlink.core.designsystem.GlPrimaryButton
import ir.guardianlink.core.designsystem.GlTextField

@Composable
fun ChildSetupScreen(vm: ChildSetupViewModel = viewModel(), onConnected: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(st.connected) { if (st.connected) onConnected() }
    if (st.confirm != null) {
        ChildConfirmScreen(vm, onConnected)
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔗", style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.setup_text), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        GlErrorBanner(st.errorCode)
        GlCard {
            GlTextField(
                value = code,
                onValueChange = { code = it },
                label = stringResource(R.string.setup_field),
                error = if (st.codeError) stringResource(R.string.setup_code_bad) else null,
            )
            GlPrimaryButton(stringResource(R.string.setup_connect), onClick = { vm.submitCode(code) }, loading = st.loading)
        }
    }
}

@Composable
fun ChildConfirmScreen(vm: ChildSetupViewModel = viewModel(), onConnected: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val info = st.confirm
    var deviceName by rememberSaveable { mutableStateOf(Build.MODEL) }

    LaunchedEffect(st.connected) { if (st.connected) onConnected() }
    if (info == null) return

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.confirm_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        GlErrorBanner(st.errorCode)
        GlCard {
            Text(stringResource(R.string.confirm_family, info.familyName), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.confirm_parent, info.parentName))
            GlTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = stringResource(R.string.confirm_device_name),
            )
            Text(stringResource(R.string.confirm_notice), style = MaterialTheme.typography.bodySmall)
            GlPrimaryButton(stringResource(R.string.confirm_btn), onClick = { vm.confirm(deviceName) }, loading = st.loading)
        }
    }
}
