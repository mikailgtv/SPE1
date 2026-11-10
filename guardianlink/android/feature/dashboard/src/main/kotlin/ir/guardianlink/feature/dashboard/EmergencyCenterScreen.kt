package ir.guardianlink.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.network.DeviceOut
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.SosEventOut
import ir.guardianlink.core.network.SosLocationOut
import ir.guardianlink.core.network.SosNoteOut
import ir.guardianlink.core.network.SosSessionOut
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EmergencyCenterScreen(initialSosId:String?=null,onBack:()->Unit) {
    val scope=rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SosSessionOut>>(emptyList()) }
    var devices by remember { mutableStateOf<List<DeviceOut>>(emptyList()) }
    var selected by remember { mutableStateOf<SosSessionOut?>(null) }
    var points by remember { mutableStateOf<List<SosLocationOut>>(emptyList()) }
    var events by remember { mutableStateOf<List<SosEventOut>>(emptyList()) }
    var notes by remember { mutableStateOf<List<SosNoteOut>>(emptyList()) }
    var noteText by remember { mutableStateOf("") }
    var resolveNote by remember { mutableStateOf("") }
    var showResolve by remember { mutableStateOf(false) }
    var showExpiry by remember { mutableStateOf(false) }
    var expiryDraft by remember { mutableIntStateOf(24) }
    var expirationHours by remember { mutableStateOf(24) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        try {
            devices=GlServices.api.devices()
            expirationHours=runCatching { GlServices.api.sosPreferences().autoExpirationHours }.getOrDefault(expirationHours)
            val active=GlServices.api.activeSos()
            val history=devices.flatMap { d -> runCatching { GlServices.api.sosHistory(d.id,20) }.getOrDefault(emptyList()) }
            sessions=(active+history).distinctBy { it.id }.sortedByDescending { runCatching { OffsetDateTime.parse(it.activatedAt).toInstant() }.getOrNull() }
            val wanted=initialSosId?.let { id -> sessions.firstOrNull { it.id==id } ?: runCatching { GlServices.api.sosDetail(id) }.getOrNull() }
            if(wanted!=null) selected=wanted else if(selected==null) selected=active.firstOrNull() ?: sessions.firstOrNull()
            selected?.let { s ->
                val detail=runCatching { GlServices.api.sosDetail(s.id) }.getOrNull() ?: s
                selected=detail
                points=runCatching { GlServices.api.sosLocations(s.id,200) }.getOrDefault(emptyList())
                events=runCatching { GlServices.api.sosEvents(s.id) }.getOrDefault(emptyList())
                notes=runCatching { GlServices.api.sosNotes(s.id) }.getOrDefault(emptyList())
            }
            error=false
        } catch (_:Exception) { error=true } finally { loading=false }
    }
    LaunchedEffect(initialSosId) { load() }
    LaunchedEffect(Unit) { while(true) { delay(30_000);load() } }

    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("مرکز اضطراری · SOS",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Row { TextButton(onClick={expiryDraft=expirationHours;showExpiry=true}) { Text("انقضا: ${expirationHours} ساعت") };TextButton(onClick=onBack) { Text("بازگشت") } }
        }
        Text("هشدار فقط به اعضای مجاز خانواده می‌رسد؛ این برنامه با خدمات اورژانسی تماس نمی‌گیرد.",style=MaterialTheme.typography.bodySmall)
        if(error) Text("اتصال به سرور برقرار نیست؛ آخرین وضعیت ممکن است قدیمی باشد.",color=MaterialTheme.colorScheme.error)
        if(loading && sessions.isEmpty()) Text("در حال بارگذاری…")
        if(sessions.isEmpty() && !loading) GlCard { Text("SOS فعالی ثبت نشده است.",fontWeight=FontWeight.Bold);Text("سوابق اضطراری در همین صفحه نمایش داده می‌شوند.") }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(sessions.isNotEmpty()) item { Text("جلسه‌های اضطراری",fontWeight=FontWeight.Bold) }
            items(sessions,key={it.id}) { s -> GlCard {
                Text("${s.deviceName} · ${sosStatusFa(s.status)}",fontWeight=FontWeight.Bold)
                Text("فعال‌سازی: ${localTime(s.activatedAt)}")
                TextButton(onClick={selected=s;scope.launch { load() }}) { Text(if(selected?.id==s.id) "نمایش جزئیات" else "بازکردن") }
            } }
            selected?.let { s -> item { GlCard {
                Text(if(s.status in setOf("ACTIVE","ACKNOWLEDGED")) "🔴 SOS فعال" else "وضعیت SOS: ${sosStatusFa(s.status)}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold)
                Text("دستگاه: ${s.deviceName}")
                Text("فعال‌سازی: ${localTime(s.activatedAt)}")
                Text("باتری: ${s.batteryLevel?.let { "$it٪" } ?: "نامشخص"} · شبکه: ${networkName(s.networkState)}")
                Text("وضعیت دستگاه: ${s.deviceState}")
                Text("موقعیت: ${if(points.isNotEmpty()) "در دسترس · آخرین نقطه ${localTime(points.first().timestamp)}" else "در دسترس نیست"}")
                Text("ردیابی SOS: ${trackingName(s.trackingStatus,s.trackingReason)}")
                if(s.trackingEndsAt!=null) Text("پایان بازه: ${localTime(s.trackingEndsAt)} · درخواست هر ${s.trackingIntervalSec} ثانیه")
                s.currentZone?.let { Text("محدودهٔ امن: $it") }
                if((s.batteryLevel ?: 100)<15) Text("هشدار: باتری دستگاه SOS بحرانی است.",color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)
                Row {
                    if(s.status=="ACTIVE") TextButton(onClick={scope.launch { runCatching { selected=GlServices.api.acknowledgeSos(s.id);load() }.onFailure { error=true } }}) { Text("تأیید دریافت") }
                    if(s.status in setOf("ACTIVE","ACKNOWLEDGED")) TextButton(onClick={showResolve=true}) { Text("حل SOS") }
                }
                if(points.isNotEmpty()) {
                    val chronological=points.reversed()
                    Text("مسیر نقاط معتبر SOS · ${points.size} نقطه")
                    LocalSosTrackMap(chronological,Modifier.fillMaxWidth().height(300.dp))
                    Text("● نقطهٔ شروع   🔴 آخرین نقطه   دایره: دقت تقریبی ثبت‌شده",style=MaterialTheme.typography.bodySmall)
                }
                if(events.isNotEmpty()) {
                    Text("خط زمانی",fontWeight=FontWeight.Bold)
                    events.forEach { ev ->
                        val accuracy=runCatching { JSONObject(ev.metadata).optString("accuracy_m") }.getOrNull()?.takeIf { it!="unknown" }
                        val delay=if(ev.eventType=="SOS_ACTIVATED") runCatching { JSONObject(ev.metadata).optLong("queued_delay_sec") }.getOrDefault(0L) else 0L
                        Text("${localTime(ev.occurredAt)} · ${eventName(ev.eventType)}${accuracy?.let { " · دقت ${it}m" } ?: ""}${if(delay>=120) " · تأخیر گزارش‌شده از زمان ثبت دستگاه: ${delay/60} دقیقه" else ""}",style=MaterialTheme.typography.bodySmall)
                    }
                }
                Text("یادداشت‌های پیگیری",fontWeight=FontWeight.Bold)
                notes.forEach { Text("${localTime(it.createdAt)} · ${it.note}",style=MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value=noteText,onValueChange={noteText=it},label={Text("یادداشت Parent")},modifier=Modifier.weight(1f))
                    TextButton(onClick={val text=noteText; if(text.isNotBlank()) scope.launch { runCatching { GlServices.api.addSosNote(s.id,text);noteText="";load() }.onFailure { error=true } }}) { Text("ثبت") }
                }
            } } }
        }
    }
    if(showExpiry) AlertDialog(onDismissRequest={showExpiry=false},title={Text("انقضای خودکار SOS")},text={Column { Text("جلسه‌های جدید پس از این مدت در نخستین درخواست بعدی به سرور به وضعیت منقضی‌شده می‌روند و رویداد SOS_EXPIRED ثبت می‌شود؛ این کار پاک‌سازی داده نیست.");Text("$expiryDraft ساعت");Slider(value=expiryDraft.toFloat(),onValueChange={expiryDraft=it.roundToInt().coerceIn(1,72)},valueRange=1f..72f,steps=70) }},confirmButton={TextButton(onClick={scope.launch { runCatching { GlServices.api.saveSosPreferences(expiryDraft) }.onSuccess { expirationHours=expiryDraft;showExpiry=false }.onFailure { error=true } }}) { Text("ذخیره") }},dismissButton={TextButton(onClick={showExpiry=false}) { Text("انصراف") }})
    if(showResolve) AlertDialog(onDismissRequest={showResolve=false},title={Text("حل وضعیت SOS")},text={OutlinedTextField(value=resolveNote,onValueChange={resolveNote=it},label={Text("یادداشت اختیاری")})},confirmButton={TextButton(onClick={
        val s=selected ?: return@TextButton
        showResolve=false
        scope.launch { runCatching { selected=GlServices.api.resolveSos(s.id,resolveNote.takeIf { it.isNotBlank() });resolveNote="";load() }.onFailure { error=true } }
    }) { Text("حل و ثبت") }},dismissButton={TextButton(onClick={showResolve=false}) { Text("انصراف") }})
}

private fun sosStatusFa(s:String)=when(s){"ACTIVE"->"فعال";"ACKNOWLEDGED"->"تأییدشده";"RESOLVED"->"حل‌شده";"CANCELLED"->"لغوشده";"EXPIRED"->"منقضی‌شده";"FAILED"->"ناموفق";else->s}
private fun trackingName(s:String,r:String?)=when(s){"ACTIVE"->"فعال";"LIMITED"->"محدود · ${when(r){"location_permission_unavailable","background_permission_unavailable"->"مجوز مکان موجود نیست";"location_sharing_disabled"->"اشتراک موقعیت خاموش است";"provider_failure"->"خطای ارائه‌دهنده";"location_provider_disabled"->"سرویس مکان خاموش است";else->"قابلیت محدود است"}}";else->"متوقف"}
private fun networkName(s:String)=when(s){"wifi"->"Wi‑Fi";"cellular"->"دادهٔ همراه";"ethernet"->"اترنت";"none"->"آفلاین";"limited"->"محدود";else->"نامشخص"}
private fun eventName(s:String)=when(s){"SOS_ACTIVATED"->"SOS فعال شد";"SOS_ACKNOWLEDGED"->"Parent دریافت را تأیید کرد";"SOS_RESOLVED"->"SOS حل شد";"SOS_CANCELLED"->"SOS لغو شد";"SOS_LOCATION_UPDATED"->"موقعیت به‌روزرسانی شد";"SOS_LOCATION_UNAVAILABLE"->"موقعیت در دسترس نبود";"SOS_TRACKING_STARTED"->"ردیابی شروع شد";"SOS_TRACKING_STOPPED"->"ردیابی متوقف شد";"SOS_NETWORK_LOST"->"اتصال شبکه قطع شد";"SOS_NETWORK_RESTORED"->"اتصال شبکه برگشت";"SOS_BATTERY_CRITICAL"->"باتری بحرانی است";"SOS_EXPIRED"->"SOS با ثبت رویداد منقضی شد";else->s}
private fun localTime(raw:String)=runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm",Locale("fa"))) }.getOrDefault(raw)
