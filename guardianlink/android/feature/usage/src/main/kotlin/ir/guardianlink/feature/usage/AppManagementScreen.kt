package ir.guardianlink.feature.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlPrimaryButton
import ir.guardianlink.core.network.AppCatalogOut
import ir.guardianlink.core.network.AppCatalogListOut
import ir.guardianlink.core.network.AppRuleOut
import ir.guardianlink.core.network.AppRuleReq
import ir.guardianlink.core.network.AppRuleScheduleReq
import ir.guardianlink.core.network.AppRestrictionEventsOut
import ir.guardianlink.core.network.AppUsageHistoryOut
import ir.guardianlink.core.network.GlServices
import kotlinx.coroutines.launch

/** Parent inventory/rule management, using the Phase 3 UsageStats data already collected on Child. */
@Composable
fun AppManagementScreen(deviceId: String, onBack: () -> Unit) {
    var catalog by remember { mutableStateOf<AppCatalogListOut?>(null) }
    var events by remember { mutableStateOf<AppRestrictionEventsOut?>(null) }
    var history by remember { mutableStateOf<AppUsageHistoryOut?>(null) }
    var selected by remember { mutableStateOf<AppCatalogOut?>(null) }
    var editing by remember { mutableStateOf<AppCatalogOut?>(null) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        try {
            catalog = GlServices.api.appCatalog(deviceId)
            events = GlServices.api.appRestrictionEvents(deviceId, 50)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "خطا در دریافت اطلاعات"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(deviceId) { refresh() }
    LaunchedEffect(selected?.packageName) {
        history = null
        val pkg = selected?.packageName ?: return@LaunchedEffect
        history = runCatching { GlServices.api.appUsageHistory(deviceId, pkg, 7) }.getOrNull()
    }

    editing?.let { app ->
        AppRuleEditor(
            app = app,
            onDismiss = { editing = null },
            onSave = { type, limitMinutes, near, critical, daysMask, start, end ->
                scope.launch {
                    val old = app.rule
                    try {
                        if (type == "NONE") {
                            if (old != null) GlServices.api.deleteAppRule(deviceId, old.id, old.version)
                        } else {
                            val req = AppRuleReq(
                                packageName = app.packageName,
                                ruleType = type,
                                dailyLimitSeconds = if (type == "LIMITED") limitMinutes * 60 else null,
                                nearPct = near,
                                criticalPct = critical,
                                enabled = true,
                                schedules = if (type == "SCHEDULED") listOf(
                                    AppRuleScheduleReq(daysMask, start, end),
                                ) else emptyList(),
                                expectedVersion = old?.version,
                            )
                            if (old == null) GlServices.api.createAppRule(deviceId, req)
                            else GlServices.api.updateAppRule(deviceId, old.id, req)
                        }
                        editing = null
                        selected = null
                        refresh()
                    } catch (e: Exception) {
                        error = e.message ?: "ذخیره قانون ناموفق بود"
                    }
                }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("فهرست اپ‌ها و قوانین", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { scope.launch { refresh() } }) { Text("تازه‌سازی") }
                TextButton(onClick = onBack) { Text("بازگشت") }
            }
        }
        catalog?.let { c ->
            GlCard {
                Text("حالت مدیریت: ${modeLabel(c.managementMode)}", fontWeight = FontWeight.Bold)
                Text(
                    when (c.enforcementCapability) {
                        "managed_device_owner_reported" -> "دستگاه گزارش کرده Device Owner است؛ اعمال هر rule هنوز به نتیجهٔ DPM روی دستگاه وابسته است."
                        "advisory_only" -> "حالت عادی Android: فقط آمار، گزارش و هشدار؛ هیچ اپی مسدود نمی‌شود."
                        else -> "قابلیت enforcement نامشخص یا پشتیبانی‌نشده است؛ blocking ادعا نمی‌شود."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Usage Access: ${if (c.usageAccessGranted) "فعال" else "خاموش"} · نسخهٔ قوانین: ${c.ruleVersion}")
                Text("آخرین تماس دستگاه برای قوانین: ${c.policyLastSyncAt ?: "ثبت نشده"}", style = MaterialTheme.typography.bodySmall)
                Text("در زمان آفلاین، دستگاه ممکن است تا دریافت نسخهٔ جدید از cache رمزگذاری‌شده استفاده کند.", style = MaterialTheme.typography.bodySmall)
                Text("فهرست بسته‌ها ممکن است به‌دلیل فیلترهای Android یا سیاست Google Play کامل نباشد. تاریخچهٔ مصرف به Usage Access و داده‌های batch موجود وابسته است.", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("جست‌وجوی نام یا package") }, singleLine = true)
        if (loading && catalog == null) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        catalog?.apps.orEmpty().filter {
            query.isBlank() || it.appName.contains(query, true) || it.packageName.contains(query, true)
        }.forEach { app ->
            GlCard {
                Text(app.appName, fontWeight = FontWeight.Bold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                Text("مصرف گزارش‌شده — امروز: ${duration(app.usageTodaySeconds)} · ۷ روز: ${duration(app.usageWeekSeconds)}")
                Text("${if (app.isSystemApp) "سیستمی" else "نصب‌شده"} · ${app.rule?.let { ruleLabel(it) } ?: "بدون قانون"}")
                app.rule?.let { Text("وضعیت اعمال: ${enforcementLabel(it.lastEnforcementStatus)}", style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { selected = if (selected?.packageName == app.packageName) null else app }) { Text("تاریخچهٔ مصرف") }
                    Button(onClick = { editing = app }) { Text("مدیریت قانون") }
                }
                if (selected?.packageName == app.packageName) {
                    HorizontalDivider()
                    Text("مصرف روزانهٔ اخیر", fontWeight = FontWeight.Medium)
                    history?.days?.forEach { day -> Text("${day.date}: ${duration(day.usageSeconds)}") }
                    if (history?.days.isNullOrEmpty()) Text("داده‌ای ثبت نشده است.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        GlCard {
            Text("رویدادهای قانون و محدودیت", fontWeight = FontWeight.Bold)
            events?.events.orEmpty().take(30).forEach { e ->
                Text("${eventLabel(e.eventType)} · ${e.packageName} · ${e.timestamp}", style = MaterialTheme.typography.bodySmall)
            }
            if (events?.events.isNullOrEmpty()) Text("هنوز رویدادی ثبت نشده است.")
        }
    }
}

@Composable
private fun AppRuleEditor(
    app: AppCatalogOut,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int, Int, String, String) -> Unit,
) {
    val old = app.rule
    var type by remember(app.packageName) { mutableStateOf(old?.ruleType ?: "NONE") }
    var limit by remember(app.packageName) { mutableStateOf(((old?.dailyLimitSeconds ?: 3600) / 60).toString()) }
    var near by remember(app.packageName) { mutableStateOf((old?.nearPct ?: 80).toString()) }
    var critical by remember(app.packageName) { mutableStateOf((old?.criticalPct ?: 90).toString()) }
    val firstSchedule = old?.schedules?.firstOrNull()
    var days by remember(app.packageName) { mutableStateOf(firstSchedule?.dayOfWeek ?: 127) }
    var start by remember(app.packageName) { mutableStateOf(firstSchedule?.startTime ?: "20:00") }
    var end by remember(app.packageName) { mutableStateOf(firstSchedule?.endTime ?: "07:00") }
    val daysFa = listOf("د", "س", "چ", "پ", "ج", "ش", "ی")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قانون برای ${app.appName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("NONE" to "بدون قانون", "ALLOW" to "مجاز", "LIMITED" to "محدودیت روزانه", "RESTRICTED" to "محدود همیشه", "SCHEDULED" to "برنامهٔ هفتگی").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label)
                        androidx.compose.material3.RadioButton(selected = type == value, onClick = { type = value })
                    }
                }
                if (type == "LIMITED") {
                    OutlinedTextField(limit, { limit = it.filter(Char::isDigit).take(5) }, label = { Text("حد روزانه، دقیقه") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(near, { near = it.filter(Char::isDigit).take(2) }, label = { Text("هشدار نزدیک، درصد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(critical, { critical = it.filter(Char::isDigit).take(3) }, label = { Text("هشدار بحرانی، درصد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                if (type == "SCHEDULED") {
                    Text("روزهای هفته (دوشنبه تا یکشنبه)")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        daysFa.forEachIndexed { index, day ->
                            Column {
                                Text(day)
                                Checkbox(checked = (days and (1 shl index)) != 0, onCheckedChange = { checked ->
                                    days = if (checked) days or (1 shl index) else days and (1 shl index).inv()
                                })
                            }
                        }
                    }
                    OutlinedTextField(start, { start = it.take(5) }, label = { Text("شروع HH:mm") }, singleLine = true)
                    OutlinedTextField(end, { end = it.take(5) }, label = { Text("پایان HH:mm") }, singleLine = true)
                    if ((old?.schedules?.size ?: 0) > 1) Text("ذخیره، بازه‌های قبلی را با همین یک بازه جایگزین می‌کند.", color = MaterialTheme.colorScheme.error)
                }
                Text("حالت عادی دستگاه فقط هشدار می‌دهد؛ محدودسازی واقعی نیازمند Device Owner است.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(type, limit.toIntOrNull() ?: 60, near.toIntOrNull() ?: 80, critical.toIntOrNull() ?: 90, days, start, end)
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun modeLabel(mode: String) = when (mode) { "managed" -> "Managed / Device Owner"; "normal" -> "عادی"; "unsupported" -> "پشتیبانی‌نشده"; else -> "نامشخص" }
private fun ruleLabel(rule: AppRuleOut) = when (rule.ruleType) {
    "LIMITED" -> "سقف روزانه: ${duration(rule.dailyLimitSeconds ?: 0)}"
    "SCHEDULED" -> "برنامهٔ هفتگی (${rule.schedules.size} بازه)"
    "RESTRICTED" -> "محدود همیشه"
    else -> "مجاز"
}
private fun enforcementLabel(value: String) = when (value) {
    "applied" -> "موفق (طبق گزارش دستگاه)"
    "unsupported" -> "بدون پشتیبانی در این حالت"
    "failed" -> "Android اعمال نکرد"
    else -> "در انتظار گزارش دستگاه"
}
private fun duration(seconds: Int?): String = seconds?.let { "${it / 3600}س ${it % 3600 / 60}د" } ?: "داده‌ای گزارش نشده"
private fun eventLabel(type: String) = when (type) {
    "APP_USAGE_LIMIT_NEAR" -> "نزدیک سقف"
    "APP_USAGE_LIMIT_CRITICAL" -> "هشدار بحرانی مصرف"
    "APP_USAGE_LIMIT_REACHED" -> "سقف مصرف رسید"
    "APP_RESTRICTION_ENFORCED" -> "محدودیت اعمال شد"
    "APP_RESTRICTION_FAILED" -> "Android اعمال محدودیت را رد کرد"
    "APP_RESTRICTION_UNSUPPORTED" -> "محدودیت پشتیبانی نمی‌شود"
    else -> type
}
