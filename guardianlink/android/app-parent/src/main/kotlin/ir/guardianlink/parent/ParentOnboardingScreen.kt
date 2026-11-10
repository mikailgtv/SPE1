package ir.guardianlink.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlPrimaryButton
import ir.guardianlink.core.network.ApiException
import ir.guardianlink.core.network.DeviceOut
import ir.guardianlink.core.network.GlServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun ParentOnboardingScreen(onOpenPairing: () -> Unit, onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var errorCode by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf<String?>(null) }
    var familyName by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<DeviceOut>>(emptyList()) }
    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionSnapshot by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var locationPermission by remember { mutableStateOf<String?>(null) }
    var locationSharing by remember { mutableStateOf<Boolean?>(null) }
    var baseSetupReady by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        errorCode = null
        permissionSnapshot = emptyMap()
        locationPermission = null
        locationSharing = null
        baseSetupReady = false
        try {
            email = GlServices.api.me().email
            familyName = GlServices.api.familyMe().family.name
            devices = GlServices.api.devices()
            val selected = devices.firstOrNull { it.id == selectedDeviceId } ?: devices.firstOrNull()
            selectedDeviceId = selected?.id
            if (selected != null) {
                permissionSnapshot = runCatching { GlServices.api.permissions(selected.id)["permissions"] as? Map<String, String> ?: selected.permissions.orEmpty() }
                    .getOrDefault(selected.permissions.orEmpty())
                val loc = runCatching { GlServices.api.locationCurrent(selected.id) }.getOrNull()
                locationPermission = loc?.permissionStatus
                locationSharing = loc?.sharingEnabled
                baseSetupReady = selected.status.equals("active", true) && !selected.lastSync.isNullOrBlank()
            }
        } catch (e: ApiException) {
            errorCode = e.code
        } catch (_: Exception) {
            errorCode = ir.guardianlink.core.network.ErrCode.NETWORK
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(step) {
        if (step == 5) {
            while (true) {
                delay(8_000)
                refresh()
            }
        }
    }

    val labels = listOf(
        R.string.onboarding_account,
        R.string.onboarding_family,
        R.string.onboarding_child,
        R.string.onboarding_pair,
        R.string.onboarding_verify,
        R.string.onboarding_review,
        R.string.onboarding_finish,
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.onboarding_subtitle), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { index, label ->
                val stepA11y = stringResource(R.string.onboarding_step_a11y, label, index + 1, labels.size)
                val stepState = stringResource(
                    when {
                        index + 1 == step -> R.string.onboarding_step_current
                        index + 1 < step -> R.string.onboarding_step_completed
                        else -> R.string.onboarding_step_upcoming
                    },
                )
                Text(
                    text = if (index + 1 == step) "● ${index + 1}" else if (index + 1 < step) "✓ ${index + 1}" else "○ ${index + 1}",
                    color = if (index + 1 <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.semantics { contentDescription = stepA11y; stateDescription = stepState },
                )
            }
        }
        Text(stringResource(labels[step - 1]), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (errorCode != null) {
            GlCard {
                Text(stringResource(R.string.onboarding_refresh_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { scope.launch { refresh() } }) { Text(stringResource(R.string.onboarding_retry)) }
            }
        }
        when (step) {
            1 -> GlCard {
                Text(stringResource(R.string.onboarding_account_done), fontWeight = FontWeight.Bold)
                Text(email ?: if (loading) stringResource(R.string.onboarding_loading) else "—")
                Text(stringResource(R.string.onboarding_account_note), style = MaterialTheme.typography.bodySmall)
            }
            2 -> GlCard {
                Text(stringResource(R.string.onboarding_family_done), fontWeight = FontWeight.Bold)
                Text(familyName ?: if (loading) stringResource(R.string.onboarding_loading) else "—")
                Text(stringResource(R.string.onboarding_family_note), style = MaterialTheme.typography.bodySmall)
            }
            3 -> GlCard {
                Text(stringResource(R.string.onboarding_child_note), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.onboarding_child_device_model_note), style = MaterialTheme.typography.bodySmall)
            }
            4 -> GlCard {
                Text(stringResource(R.string.onboarding_pair_note), style = MaterialTheme.typography.bodyMedium)
                GlPrimaryButton(stringResource(R.string.onboarding_open_pairing), onClick = {
                    step = 5
                    onOpenPairing()
                })
            }
            5 -> GlCard {
                Text(stringResource(R.string.onboarding_verify_note), style = MaterialTheme.typography.bodyMedium)
                if (loading) Text(stringResource(R.string.onboarding_loading))
                if (devices.isEmpty()) {
                    Text(stringResource(R.string.onboarding_waiting_device), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    devices.forEach { d ->
                        Text("${d.deviceName} · ${presenceLabel(d.presence)} · ${stringResource(R.string.onboarding_last_sync)} ${formatTimestamp(d.lastSync)}")
                    }
                    Text(stringResource(R.string.onboarding_verify_tip), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { scope.launch { refresh() } }) { Text(stringResource(R.string.onboarding_check_again)) }
                if (devices.isNotEmpty()) GlPrimaryButton(stringResource(R.string.onboarding_next), onClick = { step = 6 })
                else TextButton(onClick = { step = 4 }) { Text(stringResource(R.string.onboarding_pair_again)) }
            }
            6 -> GlCard {
                Text(stringResource(R.string.onboarding_permissions_note), style = MaterialTheme.typography.bodySmall)
                val status = selectedDeviceId?.let { id -> devices.firstOrNull { it.id == id } }
                SetupRow(stringResource(R.string.onboarding_device_registered), if (status != null) stringResource(R.string.onboarding_reported) else stringResource(R.string.onboarding_unknown))
                SetupRow(stringResource(R.string.onboarding_location), permissionLabel(locationPermission))
                SetupRow(stringResource(R.string.onboarding_location_sharing), locationSharing?.let { if (it) stringResource(R.string.onboarding_enabled) else stringResource(R.string.onboarding_disabled) } ?: stringResource(R.string.onboarding_unknown))
                SetupRow(stringResource(R.string.onboarding_usage_access), permissionLabel(permissionSnapshot["usage_access"]))
                SetupRow(stringResource(R.string.onboarding_notifications), permissionLabel(permissionSnapshot["notifications"], notReported = true))
                SetupRow(stringResource(R.string.onboarding_battery), permissionLabel(permissionSnapshot["battery_optimization"]))
                Text(stringResource(R.string.onboarding_optional_note), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { scope.launch { refresh() } }) { Text(stringResource(R.string.onboarding_check_again)) }
            }
            7 -> GlCard {
                Text(
                    stringResource(if (baseSetupReady) R.string.onboarding_device_ready else R.string.onboarding_setup_incomplete),
                    fontWeight = FontWeight.Bold,
                    color = if (baseSetupReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.onboarding_finish_note), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { scope.launch { refresh() } }) { Text(stringResource(R.string.onboarding_check_again)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 1) TextButton(onClick = { step = (step - 1).coerceAtLeast(1) }) { Text(stringResource(R.string.onboarding_back)) }
            if (step in 1..3) GlPrimaryButton(stringResource(R.string.onboarding_next), onClick = { step += 1 }, loading = loading)
            if (step == 6) GlPrimaryButton(stringResource(R.string.onboarding_next), onClick = { step = 7 })
            if (step == 7) GlPrimaryButton(stringResource(R.string.onboarding_dashboard), onClick = onFinish)
        }
    }
}

@Composable
private fun permissionLabel(value: String?, notReported: Boolean = false): String {
    if (value == null) return stringResource(if (notReported) R.string.onboarding_not_reported else R.string.onboarding_unknown)
    return when (value.lowercase(Locale.ROOT)) {
        "granted", "enabled", "true" -> stringResource(R.string.onboarding_enabled)
        "background_granted" -> stringResource(R.string.onboarding_background_granted)
        "denied", "disabled", "false" -> stringResource(R.string.onboarding_disabled)
        "limited" -> stringResource(R.string.onboarding_limited)
        "restricted" -> stringResource(R.string.onboarding_restricted)
        "unavailable" -> stringResource(R.string.onboarding_unavailable)
        else -> stringResource(R.string.onboarding_unknown)
    }
}

@Composable
private fun presenceLabel(value: String): String = when (value.lowercase(Locale.ROOT)) {
    "online", "connected" -> stringResource(R.string.onboarding_online)
    "offline", "disconnected" -> stringResource(R.string.onboarding_offline)
    else -> stringResource(R.string.onboarding_unknown)
}

@Composable
private fun formatTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return stringResource(R.string.onboarding_unknown)
    return runCatching {
        val instant = runCatching { OffsetDateTime.parse(value).toInstant() }.getOrElse { Instant.parse(value) }
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault(value)
}

@Composable
private fun SetupRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
