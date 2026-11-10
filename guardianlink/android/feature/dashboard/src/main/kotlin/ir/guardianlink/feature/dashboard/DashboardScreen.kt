package ir.guardianlink.feature.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlErrorBanner
import ir.guardianlink.core.designsystem.GlPrimaryButton
import ir.guardianlink.core.designsystem.GlTextField
import ir.guardianlink.core.designsystem.HealthChip
import ir.guardianlink.core.designsystem.PresenceChip
import ir.guardianlink.core.designsystem.faNum
import ir.guardianlink.core.network.DeviceOut
import ir.guardianlink.core.network.EventOut
import kotlinx.coroutines.delay
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Composable
fun DashboardScreen(
    vm: DashboardViewModel = viewModel(),
    onAddDevice: () -> Unit,
    onOpenScreenTime: (String) -> Unit = {},
    onOpenMap: (String) -> Unit = {},
    onOpenLocationHistory: (String, String) -> Unit = { _, _ -> },
    onOpenLocationSettings: (String) -> Unit = {},
    onOpenSafeZones: (String) -> Unit = {},
    onOpenAppManagement: (String) -> Unit = {},
    onOpenWebSafety: (String) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenEmergency: () -> Unit = {},
    onOpenPrivacyCenter: () -> Unit = {},
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        vm.appContext = context.applicationContext
        ParentNotifier.ensureChannels(context)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // refresh هر ۳۰ ثانیه فقط وقتی صفحه باز است
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            vm.refresh()
        }
    }

    st.confirmRemoveId?.let {
        AlertDialog(
            onDismissRequest = { vm.cancelRemove() },
            title = { Text(stringResource(R.string.remove_title)) },
            text = { Text(stringResource(R.string.remove_text)) },
            confirmButton = {
                TextButton(onClick = { vm.confirmRemove() }, enabled = !st.removing) {
                    Text(stringResource(R.string.remove_yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { vm.cancelRemove() }) { Text(stringResource(R.string.remove_no)) } },
        )
    }

    if (showSettings) {
        SettingsDialog(
            initial = st.settings,
            onDismiss = { showSettings = false },
            onSave = { low, crit, delayed, offline, interval ->
                vm.saveSettings(low, crit, delayed, offline, interval)
                showSettings = false
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(stringResource(R.string.dash_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                st.familyName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            Row {
                TextButton(onClick = onOpenEmergency) { Text("مرکز اضطراری") }
                TextButton(onClick = onOpenNotifications) { Text(if (st.notificationUnreadCount > 0) "اعلان‌ها (${st.notificationUnreadCount})" else "اعلان‌ها") }
                TextButton(onClick = onOpenPrivacyCenter) { Text("حریم خصوصی") }
                TextButton(onClick = { showSettings = true }) { Text("⚙️") }
                TextButton(onClick = { vm.refresh() }) { Text("⟳") }
            }
        }

        GlErrorBanner(st.errorCode)

        if (st.loading) {
            Text(stringResource(R.string.loading))
        } else if (st.devices.isEmpty()) {
            GlCard {
                Text(stringResource(R.string.no_device), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.no_device_text), style = MaterialTheme.typography.bodySmall)
                GlPrimaryButton(stringResource(R.string.add_device), onClick = onAddDevice)
            }
        } else {
            st.devices.forEach { d ->
                DeviceCard(d, st.events, onRemove = { vm.askRemove(d.id) })
                // کارت Screen Time (Phase 3) — فقط وقتی دستگاه متصل است داده دارد
                ir.guardianlink.feature.usage.ScreenTimeCard(
                    report = st.screenTime[d.id],
                    usageAccessGranted = d.permissions?.get("usage_access")?.let { it == "granted" },
                    onClick = { onOpenScreenTime(d.id) },
                )
                val appPolicy = st.appRules[d.id]
                GlCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("مدیریت اپ‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${appPolicy?.rules?.count { it.enabled } ?: 0} قانون")
                    }
                    Text(
                        when (appPolicy?.managementMode) {
                            "managed" -> "Device Owner: گزارش‌شدهٔ دستگاه مدیریت‌شده"
                            "normal" -> "حالت عادی: فقط هشدار؛ بدون مسدودسازی"
                            else -> "وضعیت مدیریت یا همگام‌سازی هنوز مشخص نیست"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (appPolicy?.rules?.any { it.enabled && it.lastEnforcementStatus in setOf("failed", "unsupported") } == true) {
                        Text("برخی محدودیت‌ها پشتیبانی یا اعمال نشده‌اند؛ جزئیات را بررسی کنید.", color = MaterialTheme.colorScheme.error)
                    }
                    GlPrimaryButton("فهرست اپ‌ها و قوانین", onClick = { onOpenAppManagement(d.id) })
                    GlPrimaryButton("ایمنی وب (Chrome مدیریت‌شده)", onClick = { onOpenWebSafety(d.id) })
                }
                // کارت 📍 Location (Phase 4)
                ir.guardianlink.feature.location.LocationCard(
                    current = st.locations[d.id],
                    onOpenMap = { onOpenMap(d.id) },
                    onOpenHistory = { filter -> onOpenLocationHistory(d.id, filter) },
                    onOpenSettings = { onOpenLocationSettings(d.id) },
                )
                val geo = st.geofences[d.id]
                GlCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🛡 محدوده‌های امن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${geo?.active ?: 0} فعال")
                    }
                    geo?.zones?.filter { it.enabled }?.take(3)?.forEach { zone ->
                        Text("${zone.name}: ${when (zone.state) { "inside" -> "🟢 داخل"; "outside" -> "⚪ خارج"; "error" -> "🔴 خطا"; else -> "⚪ نامشخص" }}")
                    }
                    geo?.zones?.firstOrNull { it.lastEvent != null }?.lastEvent?.let { ev ->
                        Text("آخرین رویداد: ${ev.zoneName} · ${if (ev.eventType.endsWith("ENTER")) "ورود" else "خروج"}", style = MaterialTheme.typography.bodySmall)
                    }
                    GlPrimaryButton("مدیریت محدوده‌های امن", onClick = { onOpenSafeZones(d.id) })
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(d: DeviceOut, events: List<EventOut>, onRemove: () -> Unit) {
    GlCard {
        // سربرگ
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("👧 " + d.deviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            PresenceChip(d.presence)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthChip(d.health ?: "unknown")
        }
        // باتری
        Text(
            batteryLabel(d.batteryLevel, d.batteryBand),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
        InfoRow(stringResource(R.string.last_sync), relativeFa(d.lastSync))
        InfoRow(stringResource(R.string.conn_health), healthLabel(d.health))
        // اطلاعات دستگاه
        Text(stringResource(R.string.device_block), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InfoRow(stringResource(R.string.device_id), d.maskedId ?: "—")
        InfoRow(stringResource(R.string.device_hw), (d.manufacturer + " " + d.model).trim())
        InfoRow(stringResource(R.string.android_ver), d.androidVersion.ifBlank { "—" })
        InfoRow(stringResource(R.string.app_ver), d.appVersion.ifBlank { "—" })
        InfoRow(stringResource(R.string.first_connected), dateFa(d.createdAt))
        InfoRow(stringResource(R.string.last_seen), relativeFa(d.lastSeen))
        // مجوزها
        Text(stringResource(R.string.permissions_block), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PermissionRows(d.permissions)
        // رویدادهای اخیر
        Text(stringResource(R.string.recent_events), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val mine = events.filter { it.deviceId == d.id }.take(5)
        if (mine.isEmpty()) {
            Text(stringResource(R.string.no_events), style = MaterialTheme.typography.bodySmall)
        } else {
            mine.forEach { e ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("• " + eventLabel(e.eventType), style = MaterialTheme.typography.bodySmall)
                    Text(relativeFa(e.ts), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.remove_device), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PermissionRows(perms: Map<String, String>?) {
    val keys = listOf("location", "notifications", "battery_optimization", "usage_access")
    for (k in keys) {
        val v = perms?.get(k)
        InfoRow(permLabel(k), permStateLabel(v))
    }
}

@Composable
private fun permLabel(key: String): String = when (key) {
    "location" -> stringResource(R.string.perm_location)
    "notifications" -> stringResource(R.string.perm_notifications)
    "battery_optimization" -> stringResource(R.string.perm_battery_opt)
    else -> stringResource(R.string.perm_usage)
}

@Composable
private fun permStateLabel(v: String?): String = when (v) {
    "granted", "allowed", "unrestricted" -> stringResource(R.string.perm_state_ok)
    "not_configured" -> stringResource(R.string.perm_state_notset)
    "not_requested" -> stringResource(R.string.perm_state_notreq)
    "optimized" -> stringResource(R.string.perm_state_optimized)
    "not_granted", "denied", "restricted" -> stringResource(R.string.perm_state_denied)
    else -> "—"
}

@Composable
private fun batteryLabel(level: Int?, band: String?): String {
    val pct = level?.let { faNum(it) + "٪" } ?: "—"
    val icon = when (band) {
        "critical" -> "🪫"
        "low" -> "🔋"
        "normal" -> "🔋"
        else -> "🔋"
    }
    val bandText = when (band) {
        "critical" -> " (" + stringResource(R.string.band_critical) + ")"
        "low" -> " (" + stringResource(R.string.band_low) + ")"
        else -> ""
    }
    return "$icon $pct$bandText"
}

@Composable
private fun healthLabel(h: String?): String = when (h) {
    "healthy" -> stringResource(ir.guardianlink.core.designsystem.R.string.health_healthy)
    "delayed" -> stringResource(ir.guardianlink.core.designsystem.R.string.health_delayed)
    "offline" -> stringResource(ir.guardianlink.core.designsystem.R.string.health_offline)
    else -> stringResource(ir.guardianlink.core.designsystem.R.string.health_unknown)
}

@Composable
private fun eventLabel(type: String): String = when (type) {
    "DEVICE_CONNECTED" -> stringResource(R.string.ev_connected)
    "DEVICE_DISCONNECTED" -> stringResource(R.string.ev_disconnected)
    "BATTERY_LOW" -> stringResource(R.string.ev_battery_low)
    "BATTERY_CRITICAL" -> stringResource(R.string.ev_battery_critical)
    "SYNC_DELAYED" -> stringResource(R.string.ev_sync_delayed)
    "PERMISSION_CHANGED" -> stringResource(R.string.ev_permission)
    "APP_UPDATED" -> stringResource(R.string.ev_app_updated)
    "SCREEN_TIME_LIMIT_NEAR" -> stringResource(R.string.ev_st_near)
    "SCREEN_TIME_LIMIT_REACHED" -> stringResource(R.string.ev_st_reached)
    "USAGE_ACCESS_REVOKED" -> stringResource(R.string.ev_usage_revoked)
    "SCHEDULE_STARTED" -> stringResource(R.string.ev_schedule_started)
    "SCHEDULE_ENDED" -> stringResource(R.string.ev_schedule_ended)
    "LOCATION_ENABLED" -> stringResource(R.string.ev_loc_enabled)
    "LOCATION_DISABLED" -> stringResource(R.string.ev_loc_disabled)
    "LOCATION_PERMISSION_GRANTED" -> stringResource(R.string.ev_loc_perm_granted)
    "LOCATION_PERMISSION_REVOKED" -> stringResource(R.string.ev_loc_perm_revoked)
    "LOCATION_UPDATE_RECEIVED" -> stringResource(R.string.ev_loc_update)
    "LOCATION_SYNC_DELAYED" -> stringResource(R.string.ev_loc_delayed)
    "LOCATION_UNAVAILABLE" -> stringResource(R.string.ev_loc_unavailable)
    "APP_RULE_CHANGED" -> stringResource(R.string.ev_app_rule_changed)
    "APP_RULE_DISABLED" -> stringResource(R.string.ev_app_rule_disabled)
    "APP_USAGE_LIMIT_NEAR" -> stringResource(R.string.ev_app_limit_near)
    "APP_USAGE_LIMIT_CRITICAL" -> stringResource(R.string.ev_app_limit_critical)
    "APP_USAGE_LIMIT_REACHED" -> stringResource(R.string.ev_app_limit_reached)
    "APP_RESTRICTION_ENFORCED" -> stringResource(R.string.ev_app_restriction_applied)
    "APP_RESTRICTION_FAILED" -> stringResource(R.string.ev_app_restriction_failed)
    "APP_RESTRICTION_UNSUPPORTED" -> stringResource(R.string.ev_app_restriction_unsupported)
    "DEVICE_MANAGEMENT_CHANGED" -> stringResource(R.string.ev_device_management_changed)
    else -> type
}

@Composable
private fun SettingsDialog(
    initial: ir.guardianlink.core.network.SettingsOut?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Int, Int) -> Unit,
) {
    var low by rememberSaveable { mutableStateOf((initial?.batteryLowPct ?: 30).toString()) }
    var crit by rememberSaveable { mutableStateOf((initial?.batteryCriticalPct ?: 15).toString()) }
    var delayed by rememberSaveable { mutableStateOf(((initial?.syncDelayedSec ?: 1800) / 60).toString()) }
    var offline by rememberSaveable { mutableStateOf(((initial?.offlineSec ?: 3600) / 60).toString()) }
    var interval by rememberSaveable { mutableStateOf(((initial?.heartbeatIntervalSec ?: 900) / 60).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlTextField(low, { low = it }, stringResource(R.string.settings_low))
                GlTextField(crit, { crit = it }, stringResource(R.string.settings_critical))
                GlTextField(delayed, { delayed = it }, stringResource(R.string.settings_delayed_min))
                GlTextField(offline, { offline = it }, stringResource(R.string.settings_offline_min))
                GlTextField(interval, { interval = it }, stringResource(R.string.settings_interval_min))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        low.toIntOrNull() ?: 30,
                        crit.toIntOrNull() ?: 15,
                        (delayed.toIntOrNull() ?: 30) * 60,
                        (offline.toIntOrNull() ?: 60) * 60,
                        (interval.toIntOrNull() ?: 15) * 60,
                    )
                },
            ) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.remove_no)) } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun relativeFa(iso: String?): String {
    val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return "—"
    val min = ChronoUnit.MINUTES.between(t, OffsetDateTime.now())
    return when {
        min < 1 -> "هم‌اکنون"
        min < 60 -> faNum(min.toInt()) + " دقیقه پیش"
        else -> faNum((min / 60).toInt()) + " ساعت پیش"
    }
}

private fun dateFa(iso: String?): String {
    val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return "—"
    return t.toLocalDate().toString()
}
