package ir.guardianlink.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.NotificationPreferencesOut
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun NotificationPreferencesPanel(p: NotificationPreferencesOut, onChanged: (NotificationPreferencesOut) -> Unit) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var error by remember { mutableStateOf(false) }
    val categories=listOf("BATTERY" to "باتری","CONNECTIVITY" to "اتصال","LOCATION" to "مکان","GEOFENCE" to "محدوده امن","APP_USAGE" to "زمان اپ","WEB_SAFETY" to "ایمنی وب","PERMISSION" to "مجوزها","SECURITY" to "امنیت","EMERGENCY" to "اضطراری SOS")
    val values=remember(p.enabledCategories) { runCatching { JSONObject(p.enabledCategories) }.getOrDefault(JSONObject()) }
    GlCard {
        Text("تنظیمات اعلان‌ها")
        categories.forEach { (key,label) ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(label); Switch(checked=values.optBoolean(key,true),onCheckedChange={ enabled -> values.put(key,enabled); onChanged(p.copy(enabledCategories=values.toString())) })
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("ساعات سکوت"); Switch(checked=p.quietHoursEnabled,onCheckedChange={ onChanged(p.copy(quietHoursEnabled=it)) })
        }
        Row {
            OutlinedTextField(value=p.quietStart,onValueChange={ onChanged(p.copy(quietStart=it)) },label={ Text("شروع HH:mm") },modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(value=p.quietEnd,onValueChange={ onChanged(p.copy(quietEnd=it)) },label={ Text("پایان HH:mm") },modifier=Modifier.weight(1f),singleLine=true)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("اجازهٔ هشدار بحرانی در ساعات سکوت"); Switch(checked=p.criticalDuringQuietHours,onCheckedChange={ onChanged(p.copy(criticalDuringQuietHours=it)) })
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("خلاصهٔ روزانه روی این دستگاه")
            Switch(checked=p.dailySummaryEnabled,onCheckedChange={ onChanged(p.copy(dailySummaryEnabled=it)) })
        }
        Text("خلاصه فقط روی همین برنامه/دستگاه و در صورت امکان اتصال شبکه اجرا می‌شود.")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("فاصلهٔ ضدتکرار: ${p.cooldownMinutes} دقیقه")
            TextButton(onClick={ onChanged(p.copy(cooldownMinutes=if(p.cooldownMinutes>=60) 0 else p.cooldownMinutes+5)) }) { Text("تغییر") }
        }
        Text("منطقهٔ زمانی: ${p.timezone}")
        if(error) Text("ذخیره انجام نشد؛ ساعت‌ها را با قالب HH:mm بررسی کنید.")
        TextButton(onClick={ scope.launch { error=false; runCatching { GlServices.api.saveNotificationPreferences(p.copy(timezone=java.time.ZoneId.systemDefault().id)) }.onSuccess { saved -> onChanged(saved); NotificationSummaryScheduler.update(context,saved.dailySummaryEnabled) }.onFailure { error=true } } }) { Text("ذخیره تنظیمات") }
        Text("اعلان‌های محلی به بازبودن برنامه و اجازهٔ Android وابسته‌اند؛ Push پس‌زمینه در این نسخه وجود ندارد.")
    }
}
