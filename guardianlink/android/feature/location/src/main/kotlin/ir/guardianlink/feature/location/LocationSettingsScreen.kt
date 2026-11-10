package ir.guardianlink.feature.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import ir.guardianlink.core.designsystem.faNum

/**
 * تنظیمات Location سمت والد:
 * - کلید اشتراک ON/OFF (خاموش ⇒ کودک مختصات جدید نمی‌فرستد و سرور هم ذخیره نمی‌کند).
 * - Retention قابل تنظیم: ۷ / ۳۰ / ۹۰ روز یا غیرفعال (فقط آخرین موقعیت).
 * - حذف تاریخچه: امروز / ۷ روز اخیر / همه — «همه» با تأیید دومرحله‌ای.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSettingsScreen(
    deviceId: String,
    onBack: () -> Unit,
    vm: LocationViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var pendingScope by remember { mutableStateOf<String?>(null) }
    var lastDeleted by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(deviceId) { vm.load(deviceId) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.locset_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        GlErrorBanner(ui.error)

        // ---- اشتراک ----
        GlCard {
            Text(stringResource(R.string.locset_sharing), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ui.settings?.sharingEnabled == true) "🟢 " + stringResource(R.string.loc_sharing_active)
                    else "🔴 " + stringResource(R.string.loc_sharing_off),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = ui.settings?.sharingEnabled ?: true,
                    onCheckedChange = { vm.saveSharing(deviceId, it) },
                )
            }
            Text(stringResource(R.string.locset_sharing_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // صداقت: اگر کودک مجوز اندروید نداشته باشد، روشن بودن این کلید کافی نیست
            ui.current?.let {
                if (it.sharingEnabled && it.permissionStatus == "denied") {
                    Text("⚠️ " + stringResource(R.string.locset_perm_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // ---- retention ----
        GlCard {
            Text(stringResource(R.string.locset_retention), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, days) in listOf(
                    stringResource(R.string.locset_ret_7) to 7,
                    stringResource(R.string.locset_ret_30) to 30,
                    stringResource(R.string.locset_ret_90) to 90,
                    stringResource(R.string.locset_ret_off) to 0,
                )) {
                    FilterChip(
                        selected = (ui.settings?.retentionDays ?: 30) == days,
                        onClick = { vm.saveRetention(deviceId, days) },
                        label = { Text(label) },
                    )
                }
            }
            Text(stringResource(R.string.locset_retention_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---- حذف تاریخچه ----
        GlCard {
            Text(stringResource(R.string.locset_delete), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            lastDeleted?.let {
                if (it >= 0) Text(stringResource(R.string.locset_deleted, faNum(it)), style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlPrimaryButton(
                    stringResource(R.string.locset_del_today),
                    onClick = { pendingScope = "today" },
                    modifier = Modifier.weight(1f),
                )
                GlPrimaryButton(
                    stringResource(R.string.locset_del_7),
                    onClick = { pendingScope = "last7" },
                    modifier = Modifier.weight(1f),
                )
            }
            GlPrimaryButton(
                stringResource(R.string.locset_del_all),
                onClick = { confirmDeleteAll = true },
            )
            Text(stringResource(R.string.locset_delete_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // تأیید حذف امروز / ۷ روز
    pendingScope?.let { scope ->
        AlertDialog(
            onDismissRequest = { pendingScope = null },
            title = { Text(stringResource(R.string.locset_delete)) },
            text = {
                Text(
                    if (scope == "today") stringResource(R.string.locset_confirm_today)
                    else stringResource(R.string.locset_confirm_7),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteHistory(deviceId, scope) { n -> lastDeleted = n }
                        pendingScope = null
                    },
                ) { Text(stringResource(R.string.locset_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingScope = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // تأیید دومرحله‌ای برای «حذف همه» (الزام spec)
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.locset_del_all)) },
            text = { Text(stringResource(R.string.locset_confirm_all)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteHistory(deviceId, "all") { n -> lastDeleted = n }
                        confirmDeleteAll = false
                    },
                ) { Text(stringResource(R.string.locset_del_all_final), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
