package ir.guardianlink.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.network.ErrCode

@Composable
fun GlCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
fun GlPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled && !loading) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        supportingText = error?.let { { Text(it) } },
    )
}

/** چیپ وضعیت: 🟢 آنلاین / 🔴 آفلاین / 🟠 هشدار. */
@Composable
fun PresenceChip(presence: String) {
    val (icon, text, color) = when (presence) {
        "online" -> Triple("🟢", R.string.presence_online, GlColor.ok)
        "offline" -> Triple("🔴", R.string.presence_offline, GlColor.err)
        else -> Triple("🟠", R.string.presence_warning, GlColor.warn)
    }
    Surface(color = color.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(icon)
            Text(androidx.compose.ui.res.stringResource(text), color = color, fontWeight = FontWeight.Bold)
        }
    }
}

/** چیپ سلامت ارتباط: 🟢 Healthy / 🟠 Delayed / 🔴 Offline / ⚪ Unknown. */
@Composable
fun HealthChip(health: String) {
    val (icon, res, color) = when (health) {
        "healthy" -> Triple("🟢", R.string.health_healthy, GlColor.ok)
        "delayed" -> Triple("🟠", R.string.health_delayed, GlColor.warn)
        "offline" -> Triple("🔴", R.string.health_offline, GlColor.err)
        else -> Triple("⚪", R.string.health_unknown, GlColor.warn)
    }
    Surface(color = color.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(icon)
            Text(androidx.compose.ui.res.stringResource(res), color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GlErrorBanner(code: String?) {
    if (code == null) return
    Surface(color = GlColor.err.copy(alpha = 0.12f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
        Text(
            androidx.compose.ui.res.stringResource(errorTextRes(code)),
            modifier = Modifier.padding(12.dp),
            color = GlColor.err,
        )
    }
}

@Composable
fun errorTextRes(code: String): Int = when (code) {
    ErrCode.NETWORK -> R.string.err_network
    ErrCode.UNAUTHORIZED, ErrCode.VALIDATION, ErrCode.EMAIL_EXISTS -> R.string.err_auth
    ErrCode.SESSION_EXPIRED, ErrCode.SESSION_REVOKED -> R.string.err_session
    ErrCode.PAIRING_INVALID -> R.string.err_pairing_invalid
    ErrCode.PAIRING_EXPIRED -> R.string.err_pairing_expired
    ErrCode.PAIRING_USED -> R.string.err_pairing_used
    ErrCode.DEVICE_ALREADY_CONNECTED -> R.string.err_device_connected
    ErrCode.DEVICE_REMOVED -> R.string.err_device_removed
    ErrCode.RATE_LIMITED -> R.string.err_rate
    ErrCode.FORBIDDEN -> R.string.err_permission
    else -> R.string.err_server
}
