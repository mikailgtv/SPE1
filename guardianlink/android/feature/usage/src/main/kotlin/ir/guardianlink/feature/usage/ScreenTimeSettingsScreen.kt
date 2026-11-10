package ir.guardianlink.feature.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import ir.guardianlink.core.designsystem.GlTextField
import ir.guardianlink.core.designsystem.faNum
import ir.guardianlink.core.network.ScheduleOut
import ir.guardianlink.core.network.ScheduleReq

private val HHMM_RX = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")

/** روزهای هفته با bitmask سرور (bit0=Monday) — ترتیب نمایش ایرانی از شنبه. */
private val DAY_BITS = listOf(
    "ش" to 32, "ی" to 64, "د" to 1, "س" to 2, "چ" to 4, "پ" to 8, "ج" to 16,
)

/**
 * تنظیمات Screen Time: سقف روزانه + برنامه‌های زمانی.
 * توجه: این فاز فقط monitoring/advvisory است؛ هیچ مسدودسازی واقعی انجام نمی‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScreenTimeSettingsScreen(
    deviceId: String,
    onBack: () -> Unit,
    vm: ScreenTimeSettingsViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ScheduleOut?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId) { vm.load(deviceId) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.set_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        GlErrorBanner(ui.error)

        // ---- سقف روزانه ----
        GlCard {
            Text(stringResource(R.string.set_limit_block), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            var minutes by remember(ui.settings?.dailyLimitMinutes) { mutableStateOf((ui.settings?.dailyLimitMinutes ?: 240).toString()) }
            var near by remember(ui.settings?.nearPct) { mutableStateOf((ui.settings?.nearPct ?: 80).toString()) }
            var enabled by remember(ui.settings?.enabled) { mutableStateOf(ui.settings?.enabled ?: false) }
            GlTextField(minutes, { minutes = it.filter(Char::isDigit) }, stringResource(R.string.set_limit_minutes))
            GlTextField(near, { near = it.filter(Char::isDigit) }, stringResource(R.string.set_near_pct))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.set_limit_enabled), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            GlPrimaryButton(
                stringResource(R.string.set_save_limit),
                onClick = {
                    vm.saveSettings(
                        deviceId,
                        minutes.toIntOrNull() ?: 240,
                        near.toIntOrNull() ?: 80,
                        enabled,
                    )
                },
            )
            if (ui.saved) Text(stringResource(R.string.set_saved), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.set_monitoring_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- برنامه‌های زمانی ----
        GlCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.set_schedules), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { adding = true }) { Text(stringResource(R.string.set_add_schedule)) }
            }
            if (ui.schedules.isEmpty()) {
                Text(stringResource(R.string.st_no_schedules), style = MaterialTheme.typography.bodySmall)
            }
            ui.schedules.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            faNum(s.startTime) + " → " + faNum(s.endTime) + "  ·  " + daysLabelFa(s.daysOfWeek),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = s.enabled,
                        onCheckedChange = { en -> vm.updateSchedule(deviceId, s.id, ScheduleReq(null, null, null, null, en)) },
                    )
                    TextButton(onClick = { editing = s }) { Text(stringResource(R.string.set_edit)) }
                }
            }
            Text(
                stringResource(R.string.st_advisory_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (adding || editing != null) {
        ScheduleDialog(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { name, start, end, days, enabled ->
                val cur = editing
                if (cur == null) {
                    vm.addSchedule(deviceId, name, start, end, days, enabled)
                } else {
                    vm.updateSchedule(deviceId, cur.id, ScheduleReq(name, start, end, days, enabled))
                }
                adding = false
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(
    initial: ScheduleOut?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var start by remember { mutableStateOf(initial?.startTime ?: "22:30") }
    var end by remember { mutableStateOf(initial?.endTime ?: "07:00") }
    var days by remember { mutableStateOf(initial?.daysOfWeek ?: 127) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.set_add_schedule) else stringResource(R.string.set_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlTextField(name, { name = it }, stringResource(R.string.set_sched_name))
                GlTextField(start, { start = it }, stringResource(R.string.set_sched_start), error = if (err != null && !HHMM_RX.matches(start)) stringResource(R.string.set_bad_time) else null)
                GlTextField(end, { end = it }, stringResource(R.string.set_sched_end), error = if (err != null && !HHMM_RX.matches(end)) stringResource(R.string.set_bad_time) else null)
                Text(stringResource(R.string.set_sched_days), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_BITS.forEach { (label, bit) ->
                        FilterChip(
                            selected = (days and bit) != 0,
                            onClick = { days = days xor bit },
                            label = { Text(label) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.set_limit_enabled), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text(stringResource(R.string.set_overnight_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!HHMM_RX.matches(start) || !HHMM_RX.matches(end) || start == end || days !in 1..127 || name.isBlank()) {
                        err = "invalid"
                        return@TextButton
                    }
                    onSave(name.trim(), start, end, days, enabled)
                },
            ) { Text(stringResource(R.string.set_save_limit)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
