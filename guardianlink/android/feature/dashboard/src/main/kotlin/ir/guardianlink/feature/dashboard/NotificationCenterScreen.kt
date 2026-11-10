package ir.guardianlink.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.DeviceOut
import ir.guardianlink.core.network.NotificationOut
import ir.guardianlink.core.network.NotificationPreferencesOut
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NotificationCenterScreen(initialNotificationId: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<NotificationOut>>(emptyList()) }
    var unread by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<NotificationOut?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var severityFilter by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var unreadOnly by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<DeviceOut>>(emptyList()) }
    var deviceFilter by remember { mutableStateOf<String?>(null) }
    var deviceMenu by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var showPreferences by remember { mutableStateOf(false) }
    var prefs by remember { mutableStateOf(NotificationPreferencesOut(timezone = ZoneId.systemDefault().id)) }
    suspend fun load(append: Boolean = false) {
        loading = true
        try {
            val page = GlServices.api.notifications(limit = 30, before = if (append) cursor else null, unreadOnly = unreadOnly, category = category, severity = severityFilter, search = query.takeIf { it.isNotBlank() }, deviceId = deviceFilter, from = fromDate.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }, to = toDate.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) })
            items = if (append) items + page.items else page.items
            unread = page.unreadCount; cursor = page.nextBefore; error = false
        } catch (_: Exception) { error = true } finally { loading = false }
    }
    LaunchedEffect(Unit) { load(); prefs = runCatching { GlServices.api.notificationPreferences() }.getOrDefault(prefs); devices = runCatching { GlServices.api.devices() }.getOrDefault(emptyList()) }
    LaunchedEffect(initialNotificationId) {
        if (initialNotificationId != null) {
            val detail = runCatching { GlServices.api.notification(initialNotificationId) }.getOrNull()
            selected = detail
            if (detail != null && !detail.isRead) runCatching { GlServices.api.markNotificationRead(detail.id) }
        }
    }
    LaunchedEffect(category, severityFilter, query, unreadOnly, deviceFilter, fromDate, toDate) { delay(300); cursor = null; load() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("مرکز اعلان‌ها · خوانده‌نشده: $unread", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row { TextButton(onClick = { showPreferences = !showPreferences }) { Text("تنظیمات") }; TextButton(onClick = onBack) { Text("بازگشت") } }
        }
        if (!ParentNotifier.canPost(context)) GlCard {
            Text("اجازهٔ اعلان Android خاموش است؛ اعلان‌های داخل برنامه همچنان قابل مشاهده‌اند.")
            TextButton(onClick = {
                runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("بازکردن تنظیمات اعلان Android") }
        }
        if (showPreferences) NotificationPreferencesPanel(prefs, onChanged = { prefs = it })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = unreadOnly, onClick = { unreadOnly = !unreadOnly }, label = { Text("خوانده‌نشده") })
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("همه") })
            FilterChip(selected = category == "BATTERY", onClick = { category = "BATTERY" }, label = { Text("باتری") })
            FilterChip(selected = category == "LOCATION", onClick = { category = "LOCATION" }, label = { Text("مکان") })
            FilterChip(selected = category == "WEB_SAFETY", onClick = { category = "WEB_SAFETY" }, label = { Text("ایمنی وب") })
            FilterChip(selected = category == "EMERGENCY", onClick = { category = "EMERGENCY" }, label = { Text("اضطراری SOS") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = severityFilter == null, onClick = { severityFilter = null }, label = { Text("همهٔ شدت‌ها") })
            FilterChip(selected = severityFilter == "CRITICAL", onClick = { severityFilter = "CRITICAL" }, label = { Text("بحرانی") })
            FilterChip(selected = severityFilter == "WARNING", onClick = { severityFilter = "WARNING" }, label = { Text("هشدار") })
            FilterChip(selected = severityFilter == "INFO", onClick = { severityFilter = "INFO" }, label = { Text("اطلاع") })
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={ deviceMenu=true }) { Text(devices.firstOrNull { it.id==deviceFilter }?.deviceName ?: "همهٔ دستگاه‌ها") }
            DropdownMenu(expanded=deviceMenu,onDismissRequest={deviceMenu=false}) {
                DropdownMenuItem(text={Text("همهٔ دستگاه‌ها")},onClick={deviceFilter=null;deviceMenu=false})
                devices.forEach { d -> DropdownMenuItem(text={Text(d.deviceName)},onClick={deviceFilter=d.id;deviceMenu=false}) }
            }
        }
        Row {
            OutlinedTextField(value=fromDate,onValueChange={fromDate=it},label={Text("از تاریخ YYYY-MM-DD")},singleLine=true,modifier=Modifier.weight(1f))
            OutlinedTextField(value=toDate,onValueChange={toDate=it},label={Text("تا تاریخ YYYY-MM-DD")},singleLine=true,modifier=Modifier.weight(1f))
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("جست‌وجو") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        selected?.let { n -> GlCard {
            Text(title(n.eventType), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("دستگاه: ${n.deviceName}"); Text("زمان: ${localTime(n.occurredAt)}")
            Text("شدت: ${severity(n.severity)} · وضعیت: ${status(n.status)}")
            Text("نوع رویداد: ${n.eventType}", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { scope.launch { runCatching { GlServices.api.acknowledgeNotification(n.id) }; load() } }) { Text("تأیید") }
                TextButton(onClick = { scope.launch { runCatching { GlServices.api.resolveNotification(n.id) }; selected = null; load() } }) { Text("حل‌شده") }
                TextButton(onClick = { selected = null }) { Text("بستن") }
            }
        } }
        if (error) Text("اتصال برقرار نشد؛ اعلان‌ها ممکن است به‌روز نباشند.", color = MaterialTheme.colorScheme.error)
        val visible = items.filter { (category == null || it.category == category) && (!unreadOnly || !it.isRead) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { n -> GlCard(Modifier.clickable {
                selected = n
                if (!n.isRead) scope.launch { runCatching { GlServices.api.markNotificationRead(n.id) }; load() }
            }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title(n.eventType), fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.Bold)
                    Text(severity(n.severity), color = if (n.severity == "CRITICAL") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Text("${n.deviceName} · ${localTime(n.occurredAt)}", style = MaterialTheme.typography.bodySmall)
                if (n.status != "OPEN") Text("وضعیت: ${status(n.status)}", style = MaterialTheme.typography.labelSmall)
            } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(enabled = !loading && cursor != null, onClick = { scope.launch { load(true) } }) { Text(if (loading) "در حال بارگذاری…" else "اعلان‌های قدیمی‌تر") }
                TextButton(onClick = { scope.launch { runCatching { GlServices.api.markAllNotificationsRead() }; load() } }) { Text("همه خوانده شد") }
            } }
        }
    }
}

private fun title(type: String) = when(type) {
    "BATTERY_LOW" -> "شارژ دستگاه کم است"; "BATTERY_CRITICAL" -> "شارژ دستگاه بحرانی است"; "BATTERY_RECOVERED" -> "شارژ دستگاه بهبود یافت"
    "DEVICE_OFFLINE" -> "دستگاه آفلاین است"; "DEVICE_ONLINE","DEVICE_CONNECTED" -> "دستگاه متصل شد"; "DEVICE_DISCONNECTED" -> "اتصال دستگاه قطع شد"
    "SYNC_DELAYED" -> "همگام‌سازی با تأخیر"; "SYNC_RECOVERED" -> "همگام‌سازی برقرار شد"
    "LOCATION_PERMISSION_REVOKED" -> "مجوز مکان برداشته شده"; "LOCATION_DISABLED" -> "مکان‌یابی خاموش است"; "LOCATION_UNAVAILABLE" -> "مکان در دسترس نیست"
    "LOCATION_PERMISSION_GRANTED" -> "مجوز مکان برقرار شد"; "LOCATION_SYNC_DELAYED" -> "همگام‌سازی مکان با تأخیر"; "GEOFENCE_ENTER" -> "ورود به محدوده امن"; "GEOFENCE_EXIT" -> "خروج از محدوده امن"; "GEOFENCE_ERROR" -> "خطا در محدوده امن"
    "APP_USAGE_LIMIT_NEAR","SCREEN_TIME_LIMIT_NEAR" -> "نزدیک‌شدن به محدودیت زمان اپ"; "APP_USAGE_LIMIT_CRITICAL","APP_USAGE_LIMIT_REACHED","SCREEN_TIME_LIMIT_REACHED" -> "محدودیت زمان استفاده رسیده است"; "USAGE_ACCESS_REVOKED","USAGE_REVOKED" -> "دسترسی آمار استفاده برداشته شده"; "SCHEDULE_STARTED" -> "زمان‌بندی شروع شد"; "SCHEDULE_ENDED" -> "زمان‌بندی پایان یافت"
    "APP_RESTRICTION_FAILED","APP_RESTRICTION_UNSUPPORTED" -> "اعمال محدودیت اپ ناموفق بود"
    "WEB_PROTECTION_DISABLED" -> "حفاظت وب غیرفعال است"; "WEB_FILTERING_ERROR" -> "خطای فیلتر وب"; "WEB_FILTERING_UNAVAILABLE" -> "فیلتر وب در دسترس نیست"
    "SOS_ACTIVATED" -> "SOS اضطراری فعال شد"; "SOS_CANCELLED" -> "SOS لغو شد"; "SOS_RESOLVED" -> "SOS حل شد"; "SOS_ACKNOWLEDGED" -> "دریافت SOS تأیید شد"; "SOS_BATTERY_CRITICAL" -> "باتری دستگاه SOS بحرانی است"; "SOS_LOCATION_UNAVAILABLE" -> "موقعیت SOS در دسترس نیست"
    "PERMISSION_CHANGED" -> "وضعیت مجوز دستگاه تغییر کرد"; "APP_RESTRICTION_FAILED" -> "اعمال محدودیت اپ ناموفق بود"; "APP_RESTRICTION_UNSUPPORTED" -> "محدودیت اپ پشتیبانی نمی‌شود"; else -> "اعلان سیستم"
}
private fun severity(s: String) = when(s) { "CRITICAL" -> "بحرانی"; "WARNING" -> "هشدار"; else -> "اطلاع" }
private fun status(s: String) = when(s) { "ACKNOWLEDGED" -> "تأییدشده"; "RESOLVED" -> "حل‌شده"; else -> "باز" }
private fun localTime(raw: String): String = runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale("fa"))) }.getOrDefault(raw)
