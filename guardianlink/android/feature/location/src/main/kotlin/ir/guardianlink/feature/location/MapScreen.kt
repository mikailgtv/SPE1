package ir.guardianlink.feature.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
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
import ir.guardianlink.core.designsystem.faNum
import ir.guardianlink.core.network.LocationCurrentOut

/** مرکز پیش‌فرض نقشه وقتی هنوز موقعیتی نداریم (تهران) — بدون نشانگر. */
val DEFAULT_CENTER = LatLon(35.6992, 51.3315)

@Composable
fun healthLabelFa(health: String?): String = when (health) {
    "healthy" -> "🟢 " + stringResource(R.string.loc_health_healthy)
    "delayed" -> "🟠 " + stringResource(R.string.loc_health_delayed)
    else -> "🔴 " + stringResource(R.string.loc_health_unavailable)
}

/**
 * کارت 📍 Location برای داشبورد والد (مطرح‌شده در ASCII spec):
 * وضعیت اشتراک، آخرین موقعیت معتبر، دقت، به‌روزرسانی، سلامت، تاریخچه و retention.
 * هرگز موقعیت کهنه به‌عنوان «موقعیت فعلی» جا زده نمی‌شود.
 */
@Composable
fun LocationCard(
    current: LocationCurrentOut?,
    onOpenMap: () -> Unit,
    onOpenHistory: (String) -> Unit, // "all" | "today" | "yesterday"
    onOpenSettings: () -> Unit,
) {
    GlCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("📍 " + stringResource(R.string.loc_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                when {
                    current == null -> "—"
                    current.sharingEnabled -> "🟢 " + stringResource(R.string.loc_sharing_active)
                    else -> "🔴 " + stringResource(R.string.loc_sharing_off)
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (current == null) {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall)
        } else {
            val fix = current.fix
            if (fix == null) {
                Text(stringResource(R.string.loc_no_fix_yet), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.loc_status_line, permLabelFa(current.permissionStatus), providerLabelFa(current.providerStatus)),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    if (fix.isCurrent) stringResource(R.string.loc_current_location) else stringResource(R.string.loc_last_known),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.loc_lat), style = MaterialTheme.typography.bodySmall)
                    Text(coordFa(fix.latitude), style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.loc_lon), style = MaterialTheme.typography.bodySmall)
                    Text(coordFa(fix.longitude), style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.loc_accuracy), style = MaterialTheme.typography.bodySmall)
                    // دقت ضعیف صادقانه نمایش داده می‌شود
                    Text(
                        fix.accuracyM?.let { faNum(it.toInt().toString()) + " m" } ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.loc_updated), style = MaterialTheme.typography.bodySmall)
                    Text(locationAgeFa(fix.timestamp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            GlPrimaryButton(stringResource(R.string.loc_open_map), onClick = onOpenMap)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.loc_health), style = MaterialTheme.typography.bodySmall)
                Text(healthLabelFa(current.health), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text(stringResource(R.string.loc_history), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onOpenHistory("today") }) { Text(stringResource(R.string.loc_today)) }
                TextButton(onClick = { onOpenHistory("yesterday") }) { Text(stringResource(R.string.loc_yesterday)) }
                TextButton(onClick = { onOpenHistory("all") }) { Text(stringResource(R.string.loc_all)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.loc_retention_label),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (current.retentionDays <= 0) stringResource(R.string.loc_retention_none)
                    else stringResource(R.string.loc_retention_days, faNum(current.retentionDays)),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                )
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.loc_settings)) }
            }
        }
    }
}

@Composable
fun permLabelFa(perm: String): String = when (perm) {
    "background_granted" -> stringResource(R.string.loc_perm_background)
    "granted" -> stringResource(R.string.loc_perm_foreground)
    "denied" -> stringResource(R.string.loc_perm_denied)
    else -> stringResource(R.string.loc_perm_unknown)
}

@Composable
fun providerLabelFa(provider: String): String = when (provider) {
    "enabled" -> stringResource(R.string.loc_provider_on)
    "disabled" -> stringResource(R.string.loc_provider_off)
    else -> stringResource(R.string.loc_perm_unknown)
}

/** صفحه‌ی نقشه: marker کودک + پنل اطلاعات صادقانه (current در برابر last known). */
@Composable
fun MapScreen(
    deviceId: String,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    vm: LocationViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var camera by remember { mutableStateOf(MapCamera(DEFAULT_CENTER, 13f)) }
    var centeredOnFix by remember { mutableStateOf(false) }
    LaunchedEffect(deviceId) { vm.load(deviceId) }
    val fix = ui.current?.fix
    LaunchedEffect(fix) {
        if (fix != null && !centeredOnFix) {
            camera = MapCamera(LatLon(fix.latitude, fix.longitude), 15f)
            centeredOnFix = true
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.loc_map_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Row {
                TextButton(onClick = { vm.load(deviceId) }) { Text("⟳") }
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }
        GlErrorBanner(ui.error)
        if (ui.current != null && !ui.current!!.sharingEnabled) {
            Text(
                "🔴 " + stringResource(R.string.loc_sharing_off_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Box(Modifier.fillMaxWidth().height(380.dp)) {
            MapProviders.current.MapSurface(
                camera = camera,
                marker = fix?.let { LatLon(it.latitude, it.longitude) },
                onCameraChange = { camera = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        GlCard {
            if (fix == null) {
                Text(stringResource(R.string.loc_no_fix_yet), fontWeight = FontWeight.Bold)
                ui.current?.let {
                    Text(
                        stringResource(R.string.loc_status_line, permLabelFa(it.permissionStatus), providerLabelFa(it.providerStatus)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text(
                    // برچسب صادقانه: موقعیت قدیمی = «آخرین موقعیت معلوم»
                    if (fix.isCurrent) "📍 " + stringResource(R.string.loc_current_location)
                    else "📍 " + stringResource(R.string.loc_last_known),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    coordFa(fix.latitude) + "، " + coordFa(fix.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.loc_updated), style = MaterialTheme.typography.bodySmall)
                    Text(locationAgeFa(fix.timestamp), style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.loc_accuracy), style = MaterialTheme.typography.bodySmall)
                    Text(fix.accuracyM?.let { faNum(it.toInt().toString()) + " m" } ?: "—", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.loc_health), style = MaterialTheme.typography.bodySmall)
                    Text(healthLabelFa(ui.current?.health), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlPrimaryButton(stringResource(R.string.loc_history), onClick = onOpenHistory, modifier = Modifier.weight(1f))
            GlPrimaryButton(stringResource(R.string.loc_settings), onClick = onOpenSettings, modifier = Modifier.weight(1f))
        }
    }
}
