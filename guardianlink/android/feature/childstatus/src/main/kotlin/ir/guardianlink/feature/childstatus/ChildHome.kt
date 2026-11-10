package ir.guardianlink.feature.childstatus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlPrimaryButton
import ir.guardianlink.core.designsystem.HealthChip
import ir.guardianlink.core.designsystem.faNum
import ir.guardianlink.core.network.GlServices
import kotlinx.coroutines.launch

data class ChildHomeState(
    val connected: Boolean,
    val familyName: String?,
    val deviceName: String?,
    val lastSyncMs: Long?,
    val battery: String?,
    val health: String?,
    val appVersion: String?,
)

@Composable
fun ChildHomeScreen(onSyncNow: () -> Unit,onOpenEmergency: () -> Unit = {}) {
    val context = LocalContext.current
    var st by remember { mutableStateOf(ChildHomeState(false, null, null, null, null, null, null)) }
    // وضعیت دسترسی مصرف — پس از بازگشت از تنظیمات رسمی دوباره خوانده می‌شود
    var usageGranted by remember { mutableStateOf(ir.guardianlink.feature.usage.UsageCollector.hasUsageAccess(context)) }
    var deviceOwnerMode by remember { mutableStateOf(false) }
    var cachedRuleCount by remember { mutableStateOf(0) }
    var cachedRuleVersion by remember { mutableStateOf(0L) }
    var policyLastSyncMs by remember { mutableStateOf<Long?>(null) }
    var webStatus by remember { mutableStateOf("UNKNOWN") }
    var webReason by remember { mutableStateOf("none") }
    // Phase 4: وضعیت مجوز/سرویس/اشتراک موقعیت
    var locPerm by remember { mutableStateOf(ir.guardianlink.feature.location.LocationPermission.permissionStatus(context)) }
    var locServices by remember { mutableStateOf(ir.guardianlink.feature.location.LocationPermission.servicesEnabled(context)) }
    var locSharing by remember { mutableStateOf<String?>(null) }
    var safeZones by remember { mutableStateOf<List<ir.guardianlink.core.network.ChildGeofenceOut>>(emptyList()) }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        locPerm = ir.guardianlink.feature.location.LocationPermission.permissionStatus(context)
    }
    val bgPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) {
        locPerm = ir.guardianlink.feature.location.LocationPermission.permissionStatus(context)
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val uiScope = androidx.compose.runtime.rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                usageGranted = ir.guardianlink.feature.usage.UsageCollector.hasUsageAccess(context)
                locPerm = ir.guardianlink.feature.location.LocationPermission.permissionStatus(context)
                locServices = ir.guardianlink.feature.location.LocationPermission.servicesEnabled(context)
                deviceOwnerMode = runCatching {
                    (context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager)
                        .let { it.isDeviceOwnerApp(context.packageName) || it.isProfileOwnerApp(context.packageName) }
                }.getOrDefault(false)
                uiScope.launch {
                    val policy = AppPolicyCache.read()
                    val web = WebSafetyCache.read()
                    webStatus = web.reportedStatus
                    webReason = web.reportedReason
                    cachedRuleCount = policy.rules.count { it.enabled }
                    cachedRuleVersion = policy.version
                    policyLastSyncMs = GlServices.prefs.getLong("app_rule_sync_ms")
                    locSharing = GlServices.prefs.getString("loc_sharing")
                    safeZones = runCatching { GlServices.api.childGeofences().zones }.getOrDefault(safeZones)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        val prefs = GlServices.prefs
        val disconnected = prefs.getString("disconnected_reason")
        deviceOwnerMode = runCatching {
            (context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager)
                .let { it.isDeviceOwnerApp(context.packageName) || it.isProfileOwnerApp(context.packageName) }
        }.getOrDefault(false)
        val policy = AppPolicyCache.read()
        val web = WebSafetyCache.read()
        webStatus = web.reportedStatus
        webReason = web.reportedReason
        cachedRuleCount = policy.rules.count { it.enabled }
        cachedRuleVersion = policy.version
        policyLastSyncMs = prefs.getLong("app_rule_sync_ms")
        locSharing = prefs.getString("loc_sharing")
        st = ChildHomeState(
            connected = GlServices.creds.get() != null && disconnected == null,
            familyName = prefs.getString("family_name"),
            deviceName = prefs.getString("device_name"),
            lastSyncMs = prefs.getLong("last_sync_ms"),
            battery = prefs.getString("last_battery"),
            health = prefs.getString("last_health"),
            appVersion = DeviceInfo.appVersion(context),
        )
        if (st.connected) {
            SyncScheduler.once(context)
            safeZones = runCatching { GlServices.api.childGeofences().zones }.getOrDefault(emptyList())
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!st.connected) {
            Text("⛔", style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.child_disconnected_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.child_disconnected_text),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                stringResource(R.string.child_home_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            GlCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    st.familyName?.let { Text(stringResource(R.string.child_family, it), fontWeight = FontWeight.Bold) }
                    st.deviceName?.let { Text(stringResource(R.string.child_device, it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HealthChip(st.health ?: "unknown")
                    }
                    InfoRow(stringResource(R.string.child_battery), st.battery?.let { "🔋 " + faNum(it) + "٪" } ?: "—")
                    InfoRow(
                        stringResource(R.string.child_last_sync),
                        st.lastSyncMs?.let { relativeTime(it) } ?: stringResource(R.string.child_never),
                    )
                    InfoRow(stringResource(R.string.child_app_version), st.appVersion ?: "—")
                    Text(stringResource(R.string.child_zero_notif), style = MaterialTheme.typography.bodySmall)
                    GlPrimaryButton(stringResource(R.string.child_sync_now), onClick = onSyncNow)
                    GlPrimaryButton(stringResource(R.string.child_sos_button), onClick = onOpenEmergency)
                }
            }
            // کارت دسترسی مصرف (Usage Access) — مسیر رسمی اندروید
            GlCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.child_usage_access), fontWeight = FontWeight.Bold)
                        Text(
                            if (usageGranted) stringResource(R.string.child_usage_on) else stringResource(R.string.child_usage_off),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(stringResource(R.string.child_usage_text), style = MaterialTheme.typography.bodySmall)
                    if (!usageGranted) {
                        GlPrimaryButton(
                            stringResource(R.string.child_usage_open),
                            onClick = {
                                runCatching {
                                    context.startActivity(ir.guardianlink.feature.usage.UsageCollector.usageAccessSettingsIntent())
                                }
                            },
                        )
                    }
                }
            }
            // وضعیت صادقانهٔ مدیریت اپ‌ها؛ Normal mode هرگز به‌عنوان blocking معرفی نمی‌شود.
            GlCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.child_app_rules_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(if (deviceOwnerMode) R.string.child_app_mode_managed else R.string.child_app_mode_normal),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    InfoRow(stringResource(R.string.child_app_cached_rules), "$cachedRuleCount · v$cachedRuleVersion")
                    policyLastSyncMs?.let { InfoRow(stringResource(R.string.child_app_last_sync), relativeTime(it)) }
                    Text(stringResource(R.string.child_app_schedule_delay), style = MaterialTheme.typography.bodySmall)
                }
            }
            // شفافیت Phase 7: managed-Chrome policy status only; no browsing history/visit telemetry.
            GlCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.child_web_title), fontWeight = FontWeight.Bold)
                    InfoRow(stringResource(R.string.child_web_managed_status), webStatusLabel(webStatus))
                    Text(webReasonLabel(webReason), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.child_web_scope),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // کارت موقعیت (Phase 4) — جریان مجوز کاملاً رسمی
            ChildLocationCard(
                permStatus = locPerm,
                servicesEnabled = locServices,
                sharingFlag = locSharing,
                lastSyncMs = st.lastSyncMs,
                onAskPermission = {
                    permLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                onAskBackground = {
                    if (android.os.Build.VERSION.SDK_INT == 29) {
                        bgPermLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        }
                    }
                },
                onOpenLocationSettings = {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                },
            )
            ChildSafeZonesCard(safeZones)
        }
    }
}

/** فهرست فقط‌خواندنی محدوده‌های خانواده؛ بدون کنترل و بدون اعلان در اپ کودک. */
@Composable
private fun ChildSafeZonesCard(zones: List<ir.guardianlink.core.network.ChildGeofenceOut>) {
    GlCard {
        Text("محدوده‌های امن: ${zones.count { it.enabled }} فعال", fontWeight = FontWeight.Bold)
        if (zones.isEmpty()) Text("محدوده امن فعالی نیست", style = MaterialTheme.typography.bodySmall)
        zones.filter { it.enabled }.forEach { zone ->
            InfoRow(zone.name, when (zone.state) {
                "inside" -> "🟢 داخل"
                "outside" -> "⚪ خارج"
                "error" -> "🔴 خطا"
                else -> "⚪ نامشخص"
            })
        }
        Text("فقط نمایش وضعیت؛ ممکن است به‌روزرسانی با تأخیر باشد و لحظه‌ای نیست.", style = MaterialTheme.typography.bodySmall)
    }
}

/** کارت وضعیت/مجوز موقعیت در اپ کودک (بدون هیچ notification). */
@Composable
private fun ChildLocationCard(
    permStatus: String,
    servicesEnabled: Boolean,
    sharingFlag: String?,
    lastSyncMs: Long?,
    onAskPermission: () -> Unit,
    onAskBackground: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val fineGranted = permStatus == "granted" || permStatus == "background_granted"
    GlCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(ir.guardianlink.feature.location.R.string.childloc_title), fontWeight = FontWeight.Bold)
                Text(
                    when {
                        sharingFlag == "off" -> stringResource(ir.guardianlink.feature.location.R.string.childloc_sharing_off)
                        !fineGranted -> stringResource(ir.guardianlink.feature.location.R.string.childloc_perm_required)
                        else -> stringResource(ir.guardianlink.feature.location.R.string.childloc_active)
                    },
                    fontWeight = FontWeight.Medium,
                )
            }
            InfoRow(stringResource(ir.guardianlink.feature.location.R.string.childloc_perm_label), ir.guardianlink.feature.location.permLabelFa(permStatus))
            InfoRow(
                stringResource(ir.guardianlink.feature.location.R.string.childloc_services_label),
                if (servicesEnabled) stringResource(ir.guardianlink.feature.location.R.string.childloc_services_on) else stringResource(ir.guardianlink.feature.location.R.string.childloc_services_off),
            )
            InfoRow(
                stringResource(R.string.child_last_sync),
                lastSyncMs?.let { relativeTime(it) } ?: stringResource(R.string.child_never),
            )
            Text(stringResource(ir.guardianlink.feature.location.R.string.childloc_text), style = MaterialTheme.typography.bodySmall)
            if (!fineGranted) {
                GlPrimaryButton(stringResource(ir.guardianlink.feature.location.R.string.childloc_grant), onClick = onAskPermission)
            } else if (ir.guardianlink.feature.location.LocationPermission.needsSettingsForBackground(androidx.compose.ui.platform.LocalContext.current)) {
                Text(stringResource(ir.guardianlink.feature.location.R.string.childloc_background_hint), style = MaterialTheme.typography.bodySmall)
                GlPrimaryButton(stringResource(ir.guardianlink.feature.location.R.string.childloc_open_app_settings), onClick = onAskBackground)
            } else if (permStatus == "granted" && android.os.Build.VERSION.SDK_INT == 29) {
                // در اندروید ۱۰ می‌توان background را با دیالوگ رسمی درخواست داد
                GlPrimaryButton(stringResource(ir.guardianlink.feature.location.R.string.childloc_grant), onClick = onAskBackground)
            }
            if (!servicesEnabled) {
                GlPrimaryButton(stringResource(ir.guardianlink.feature.location.R.string.childloc_turn_on_services), onClick = onOpenLocationSettings)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun relativeTime(ms: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        ms,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()
