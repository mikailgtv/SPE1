package ir.guardianlink.feature.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlErrorBanner
import ir.guardianlink.core.designsystem.GlPrimaryButton
import ir.guardianlink.core.designsystem.faNum

@Composable
fun ParentPairingScreen(vm: ParentPairingViewModel = viewModel(), onDone: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.pair_subtitle), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

        GlErrorBanner(st.errorCode)

        if (st.loading) {
            Text(stringResource(R.string.loading))
        }

        st.session?.let { s ->
            val qr = remember(s.code) { qrBitmap(s.qrPayload) }
            GlCard {
                Text(
                    s.code.chunked(4).joinToString(" "),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally),
                )
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.qr_desc),
                    modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally),
                )
                Text(
                    stringResource(R.string.pair_expires, faNum((st.remainSec / 60).toInt()), faNum((st.remainSec % 60).toInt())),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.pair_onetime),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { vm.create() }) { Text(stringResource(R.string.pair_new)) }
            }
        }

        GlPrimaryButton(stringResource(R.string.pair_done), onClick = onDone)
    }
}
