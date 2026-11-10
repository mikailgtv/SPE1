package ir.guardianlink.feature.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlPrimaryButton
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.WebSafetyOverrideReq
import ir.guardianlink.core.network.WebSafetySettingsOut
import ir.guardianlink.core.network.WebSafetySettingsReq
import ir.guardianlink.core.network.WebSafetyRuleOut
import ir.guardianlink.core.network.WebSafetyRuleReq
import ir.guardianlink.core.network.WebSafetyEventsOut
import ir.guardianlink.core.network.WebSafetyScheduleReq
import kotlinx.coroutines.launch

/** Parent controls for explicitly managed Chrome URL rules, not system-wide Internet filtering. */
@Composable
fun WebSafetyScreen(deviceId: String, onBack: () -> Unit) {
    var settings by remember { mutableStateOf<WebSafetySettingsOut?>(null) }
    var events by remember { mutableStateOf<WebSafetyEventsOut?>(null) }
    var enabled by remember { mutableStateOf(false) }
    var defaultPolicy by remember { mutableStateOf("BLOCK_LISTED") }
    var alertsEnabled by remember { mutableStateOf(true) }
    var domain by remember { mutableStateOf("") }
    var overrideDomain by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("BLOCK") }
    var exactHost by remember { mutableStateOf(false) }
    var scheduleEnabled by remember { mutableStateOf(false) }
    var scheduleStart by remember { mutableStateOf("22:00") }
    var scheduleEnd by remember { mutableStateOf("07:00") }
    var scheduleDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun refresh() {
        busy = true
        try {
            settings = GlServices.api.webSafety(deviceId)
            settings?.let { enabled = it.enabled; defaultPolicy = it.defaultPolicy; alertsEnabled = it.alertsEnabled }
            events = GlServices.api.webSafetyEvents(deviceId, 50)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "دریافت اطلاعات ناموفق بود"
        } finally { busy = false }
    }
    LaunchedEffect(deviceId) { refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ایمنی وب", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        Text(
            "این نسخه فقط می‌تواند سیاست URLBlocklist/URLAllowlist را برای Chrome روی دستگاه یا پروفایل Android مدیریت‌شده تنظیم کند. این ابزار فیلتر سراسری اینترنت، سایر مرورگرها یا برنامه‌های WebView نیست.",
            style = MaterialTheme.typography.bodyMedium,
        )
        settings?.let { s ->
            GlCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("فعال‌سازی قواعد Chrome", fontWeight = FontWeight.Bold)
                            Text("در حالت عادی Android اعمال مسدودسازی را پشتیبانی نمی‌کند.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Text("وضعیت گزارش‌شده: ${statusLabel(s.reportedStatus)}", fontWeight = FontWeight.SemiBold)
                    Text(reasonLabel(s.reportedReason), style = MaterialTheme.typography.bodySmall)
                    s.lastReportedAt?.let { Text("آخرین گزارش دستگاه: $it", style = MaterialTheme.typography.bodySmall) }
                    Text("قواعد دسته‌بندی: ${if (s.categoryProviderAvailable) "قابل اعمال" else "غیرفعال؛ سرویس طبقه‌بندی پیکربندی نشده"}", style = MaterialTheme.typography.bodySmall)
                    Text("حالت پیش‌فرض", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { defaultPolicy = "BLOCK_LISTED" }) {
                            Text(if (defaultPolicy == "BLOCK_LISTED" || defaultPolicy == "ALLOW_ALL") "● مسدودکردن موارد فهرست‌شده" else "مسدودکردن موارد فهرست‌شده")
                        }
                        OutlinedButton(onClick = { defaultPolicy = "ALLOW_LISTED_ONLY" }) {
                            Text(if (defaultPolicy == "ALLOW_LISTED_ONLY") "● فقط موارد مجاز" else "فقط موارد مجاز")
                        }
                    }
                    Text("در حالت نخست، وب باز است و قواعد مسدودکننده اعمال می‌شوند؛ در حالت دوم، Chrome به‌طور پیش‌فرض همه‌چیز را می‌بندد و قواعد مجاز استثنا هستند.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("اعلان به والد برای خطا یا پشتیبانی‌نشدن", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = alertsEnabled, onCheckedChange = { alertsEnabled = it })
                    }
                    GlPrimaryButton(
                        if (busy) "در حال ذخیره…" else "ذخیره تنظیمات",
                        onClick = { scope.launch {
                            val cur = settings ?: return@launch
                            busy = true
                            try {
                                settings = GlServices.api.updateWebSafety(
                                    deviceId,
                                    WebSafetySettingsReq(enabled, defaultPolicy, "MANAGED_DEVICE", alertsEnabled, cur.ruleVersion),
                                )
                                refresh()
                            } catch (e: Exception) { error = e.message ?: "ذخیره تنظیمات ناموفق بود" }
                            finally { busy = false }
                        } },
                    )
                }
            }
        }
        GlCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("قاعده دامنه", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = domain, onValueChange = { domain = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("دامنه، مانند example.com") }, singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { action = "BLOCK" }) { Text(if (action == "BLOCK") "● مسدود" else "مسدود") }
                    OutlinedButton(onClick = { action = "ALLOW" }) { Text(if (action == "ALLOW") "● مجاز" else "مجاز") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("فقط همین میزبان (نه زیردامنه‌ها)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = exactHost, onCheckedChange = { exactHost = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("برنامهٔ زمانی هفتگی", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
                }
                if (scheduleEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(scheduleStart, { scheduleStart = it }, Modifier.weight(1f), label = { Text("شروع HH:mm") }, singleLine = true)
                        OutlinedTextField(scheduleEnd, { scheduleEnd = it }, Modifier.weight(1f), label = { Text("پایان HH:mm") }, singleLine = true)
                    }
                    Text("روزها (منطقهٔ زمانی دستگاه کودک)", style = MaterialTheme.typography.bodySmall)
                    val weekdayLabels = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")
                    weekdayLabels.forEachIndexed { index, label ->
                        val day = index + 1
                        OutlinedButton(onClick = {
                            scheduleDays = if (day in scheduleDays) scheduleDays - day else scheduleDays + day
                        }) { Text(if (day in scheduleDays) "● $label" else label) }
                    }
                }
                Text("دامنهٔ پایه همراه زیردامنه‌ها اعمال می‌شود مگر میزبان دقیق انتخاب شود. مسیر صفحه، عبارت جست‌وجو یا محتوای وب نگهداری نمی‌شود.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = domain.isNotBlank() && !busy && (!scheduleEnabled || scheduleDays.isNotEmpty()), onClick = { scope.launch {
                    busy = true
                    try {
                        val schedules = if (!scheduleEnabled) emptyList() else scheduleDays.sorted().map { day ->
                            WebSafetyScheduleReq(
                                dayOfWeek = day, startTime = scheduleStart, endTime = scheduleEnd,
                                action = if (action == "BLOCK") "ALLOW" else "BLOCK", timezoneId = null,
                            )
                        }
                        GlServices.api.createWebRule(
                            deviceId,
                            WebSafetyRuleReq(
                                domain = domain.trim(), scope = if (exactHost) "EXACT_HOST" else "DOMAIN_AND_SUBDOMAINS",
                                action = action, schedules = schedules,
                            ),
                        )
                        domain = ""
                        refresh()
                    } catch (e: Exception) { error = e.message ?: "ثبت قاعده ناموفق بود" }
                    finally { busy = false }
                } }) { Text("افزودن قاعده") }
            }
        }
        settings?.let { s ->
            Text("قواعد دامنه (${s.rules.count { it.ruleType == "DOMAIN" }})", fontWeight = FontWeight.Bold)
            s.rules.forEach { rule -> WebRuleCard(rule, deviceId) { refresh() } }
            if (s.activeOverrides.isNotEmpty()) {
                Text("اجازه‌های موقت فعال", fontWeight = FontWeight.Bold)
                s.activeOverrides.forEach { override ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${override.domain} — ${override.expiresAt}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch {
                            try { GlServices.api.revokeWebOverride(deviceId, override.id); refresh() }
                            catch (e: Exception) { error = e.message ?: "لغو اجازه ناموفق بود" }
                        } }) { Text("لغو") }
                    }
                }
            }
        }
        GlCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("اجازه موقت برای دامنه", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = overrideDomain, onValueChange = { overrideDomain = it }, modifier = Modifier.fillMaxWidth(), label = { Text("دامنه") }, singleLine = true)
                Button(enabled = overrideDomain.isNotBlank() && !busy, onClick = { scope.launch {
                    busy = true
                    try {
                        GlServices.api.createWebOverride(deviceId, WebSafetyOverrideReq(overrideDomain.trim(), durationSeconds = 15 * 60L))
                        overrideDomain = ""
                        refresh()
                    } catch (e: Exception) { error = e.message ?: "اجازه موقت ثبت نشد" }
                    finally { busy = false }
                } }) { Text("اجازه به مدت ۱۵ دقیقه") }
                Text("انقضا در حالت آفلاین ممکن است تا اجرای بعدی Worker اندروید با تأخیر اعمال شود.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("رویدادهای پیکربندی و قابلیت", fontWeight = FontWeight.Bold)
        Text("هیچ رویداد تلاش برای بازدید یا سابقهٔ مرور وب در این نسخه دریافت نمی‌شود.", style = MaterialTheme.typography.bodySmall)
        events?.events?.forEach { event ->
            GlCard {
                Text(event.eventType, fontWeight = FontWeight.SemiBold)
                Text("${reasonLabel(event.reasonCode)} · ${event.source} · ${event.ts}", style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) Text("در حال همگام‌سازی…", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WebRuleCard(rule: WebSafetyRuleOut, deviceId: String, onChanged: suspend () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var error by remember(rule.id) { mutableStateOf<String?>(null) }
    GlCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rule.domain ?: rule.category.orEmpty(), fontWeight = FontWeight.Bold)
                Text("${if (rule.action == "BLOCK") "مسدود" else "مجاز"} · ${if (rule.enabled) "فعال" else "خاموش"} · v${rule.version}", style = MaterialTheme.typography.bodySmall)
                rule.schedules.forEach { Text("${it.dayOfWeek}: ${it.startTime}–${it.endTime} (${it.action})", style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = { scope.launch {
                try {
                    GlServices.api.deleteWebRule(deviceId, rule.id, rule.version)
                    onChanged()
                } catch (e: Exception) { error = e.message ?: "حذف ناموفق بود" }
            } }) { Text("حذف") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "DISABLED" -> "خاموش"
    "UNSUPPORTED" -> "قابل اعمال نیست"
    "CONFIGURATION_APPLIED_UNVERIFIED" -> "تنظیمات به Android ارسال/بازخوانی شده؛ اثر داخل Chrome تأییدنشده"
    "LIMITED" -> "محدود"
    "ERROR" -> "خطا"
    else -> "نامشخص"
}
private fun reasonLabel(reason: String): String = when (reason) {
    "disabled" -> "ویژگی خاموش است."
    "managed_device_required" -> "برای اعمال سیاست، مدیریت رسمی دستگاه یا پروفایل لازم است."
    "chrome_not_installed" -> "Chrome نصب نشده است."
    "policy_readback_mismatch" -> "سیاست از Android با مقادیر مورد انتظار بازخوانی نشد."
    "policy_stored_effect_unverified" -> "تنظیم در Android ثبت شده، اما اعمال واقعی Chrome قابل تأیید خودکار نیست."
    "category_provider_unavailable" -> "قواعد دسته‌بندی اعمال نمی‌شوند؛ طبقه‌بندی‌گر پیکربندی نشده است."
    "vpn_not_implemented" -> "فیلتر مبتنی بر VPN در این نسخه پیاده‌سازی نشده است."
    "dns_provider_not_configured" -> "سرویس فیلتر DNS پیکربندی نشده است."
    "android_policy_rejected" -> "Android سیاست را رد کرد."
    else -> "گزارش پیکربندی و وضعیت؛ بدون دادهٔ بازدید."
}
