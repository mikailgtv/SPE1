package ir.guardianlink.feature.childstatus

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.network.ApiException
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.SosActivateReq
import ir.guardianlink.core.network.SosLocationIn
import ir.guardianlink.core.network.SosSessionOut
import ir.guardianlink.core.network.SosTrackingReq
import ir.guardianlink.feature.location.LocationCollector
import ir.guardianlink.feature.location.LocationPermission
import ir.guardianlink.feature.location.LocationQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun EmergencyScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var session by remember { mutableStateOf<SosSessionOut?>(null) }
    var loading by remember { mutableStateOf(true) }
    var queued by remember { mutableStateOf(false) }
    var queuedState by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var trackingMinutes by remember { mutableIntStateOf(15) }
    var intervalSec by remember { mutableIntStateOf(60) }
    var showCancel by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("هشدار اشتباهی") }
    var showLocationPermission by remember { mutableStateOf(false) }
    var permissionState by remember { mutableStateOf(LocationPermission.permissionStatus(context)) }
    val lifecycleOwner=LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event -> if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) permissionState=LocationPermission.permissionStatus(context) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        showLocationPermission=false;permissionState=LocationPermission.permissionStatus(context)
    }
    val backgroundPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionState=LocationPermission.permissionStatus(context) }
    suspend fun refresh() {
        loading=true
        session=runCatching { GlServices.api.activeDeviceSos().session }.getOrNull()
        queued=SosOutbox.hasPending()
        queuedState=SosOutbox.queuedState()
        loading=false
        session?.takeIf { it.trackingEnabled && it.trackingEndsAt!=null }?.let { active ->
            val end=runCatching { java.time.OffsetDateTime.parse(active.trackingEndsAt).toInstant().toEpochMilli() }.getOrDefault(0L)
            if(end>System.currentTimeMillis()) runCatching { SosLocationService.start(context,active.id,end,active.trackingIntervalSec) }.onFailure {
                error="SOS فعال است، اما سرویس ردیابی Android آغاز نشد؛ موقعیت زنده تأیید نشده است."
                runCatching { GlServices.api.updateSosTracking(active.id,SosTrackingReq(15,active.trackingIntervalSec,LocationPermission.permissionStatus(context),LocationPermission.providerStatus(context),"fgs_start_failure")) }
            }
        }
    }
    suspend fun activateNow() {
        error=null;loading=true
        val requestId=UUID.randomUUID().toString()
        val permission=LocationPermission.permissionStatus(context)
        val provider=LocationPermission.providerStatus(context)
        val fix=if(permission!="denied" && provider=="enabled") runCatching { LocationCollector.captureFix(context) }.getOrNull() else null
        val fallback=if(fix==null) runCatching { LocationQueue.snapshot().maxByOrNull { it.clientTs } }.getOrNull() else null
        val initial=fix ?: fallback
        val req=SosActivateReq(
            clientRequestId=requestId,clientTriggeredAt=System.currentTimeMillis(),source="child_ui",batteryLevel=DeviceInfo.batteryLevel(context),
            networkState=DeviceInfo.sosNetworkState(context),deviceState=GlServices.prefs.getString("last_health") ?: "unknown",
            permissionStatus=permission,providerStatus=provider,trackingDurationMinutes=trackingMinutes,
            trackingIntervalSec=intervalSec,
            initialLocation=initial?.let { SosLocationIn(it.latitude,it.longitude,it.accuracyM,it.clientTs,it.source ?: "last_known") },
        )
        try {
            SosOutbox.enqueue(req)
            val out=SosOutbox.syncPending()
            session=out?.session ?: runCatching { GlServices.api.activeDeviceSos().session }.getOrNull()
            queued=session==null
            queuedState=if(queued) "QUEUED" else null
            if(queued) SyncScheduler.once(context)
            session?.let { s ->
                if(s.trackingEnabled && s.trackingEndsAt!=null) {
                    val end=java.time.OffsetDateTime.parse(s.trackingEndsAt).toInstant().toEpochMilli()
                    runCatching { SosLocationService.start(context,s.id,end,s.trackingIntervalSec) }.onFailure {
                        error="SOS به سرور رسید، اما سرویس ردیابی Android آغاز نشد؛ موقعیت زنده تأیید نشده است."
                        runCatching { GlServices.api.updateSosTracking(s.id,SosTrackingReq(15,s.trackingIntervalSec,permission,provider,"fgs_start_failure")) }
                    }
                }
            }
        } catch(e:Exception) {
            val temporary=(e as? ApiException)?.let { it.httpStatus==0 || it.httpStatus>=500 || it.httpStatus==429 } ?: true
            if(temporary) {
                queued=true;queuedState="QUEUED";error="درخواست SOS رمزگذاری و در صف امن نگه‌داری شد؛ هنوز تحویل Parent تأیید نشده است."
                SyncScheduler.once(context)
            } else {
                SosOutbox.discardPending("FAILED_NOT_DELIVERED")
                queued=false;queuedState="FAILED_NOT_DELIVERED"
                error="سرور درخواست SOS را نپذیرفت؛ هیچ تحویلی به Parent تأیید نشده است."
            }
        } finally { loading=false }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(countdown) {
        if(countdown>0) { delay(1000);countdown--;if(countdown==0) activateNow() }
    }

    Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Emergency · وضعیت اضطراری",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            TextButton(onClick=onBack) { Text("بازگشت") }
        }
        Text("این قابلیت فقط Parentهای مجاز GuardianLink را مطلع می‌کند؛ با اورژانس یا نیروهای امدادی تماس نمی‌گیرد.",style=MaterialTheme.typography.bodySmall)
        if(loading) Text("در حال همگام‌سازی…")
        session?.let { s ->
            GlCard {
                Text("وضعیت SOS: ${statusFa(s.status)}",fontWeight=FontWeight.Bold)
                Text("فعال‌سازی: ${localTimeFa(s.activatedAt)}")
                Text("باتری: ${s.batteryLevel?.let { "$it٪" } ?: "نامشخص"} · شبکه: ${networkFa(s.networkState)}")
                Text("موقعیت: ${if(s.latestLocation!=null) "در دسترس" else "در دسترس نیست"}")
                Text("ردیابی: ${trackingFa(s.trackingStatus,s.trackingReason)}")
                s.currentZone?.let { Text("محدودهٔ فعلی: $it") }
                if(s.status in setOf("ACTIVE","ACKNOWLEDGED")) Button(onClick={showCancel=true},modifier=Modifier.fillMaxWidth()) { Text("لغو SOS") }
            }
        }
        if(session==null && !queued) {
            Text("برای درخواست کمک از Parent، مدت ردیابی اختیاری را انتخاب کنید.")
            Text("ردیابی موقعیت تنها با مجوز رسمی و تا پایان زمان انتخاب‌شده انجام می‌شود. خاموش‌بودن Location مانع ثبت خود SOS نیست.",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                listOf(0,15,30,60).forEach { minutes -> Row(verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=trackingMinutes==minutes,onClick={trackingMinutes=minutes})
                    Text(if(minutes==0) "خاموش" else "$minutes دقیقه")
                } }
            }
            if(trackingMinutes>0) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("فاصلهٔ درخواست:")
                    TextButton(onClick={intervalSec=if(intervalSec==30)60 else 30}) { Text("هر $intervalSec ثانیه · تغییر") }
                }
                Text("مجوز مکان: ${permissionFa(permissionState)} · سرویس: ${if(LocationPermission.servicesEnabled(context)) "روشن" else "خاموش"}")
                if(!LocationPermission.fineGranted(context)) TextButton(onClick={showLocationPermission=true}) { Text("درخواست مجوز مکان Android") }
                else if(LocationPermission.needsSettingsForBackground(context)) TextButton(onClick={
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("برای ردیابی پس‌زمینه، در تنظیمات Android گزینهٔ «همیشه مجاز» را انتخاب کنید") }
                else if(android.os.Build.VERSION.SDK_INT==29 && LocationPermission.permissionStatus(context)=="granted") TextButton(onClick={backgroundPermissionLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)}) { Text("درخواست مجوز پس‌زمینه") }
                if(!LocationPermission.servicesEnabled(context)) TextButton(onClick={runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }}) { Text("روشن‌کردن سرویس مکان در تنظیمات") }
            }
            Box(Modifier.fillMaxWidth().height(174.dp).semantics { role=Role.Button;contentDescription="دکمه SOS اضطراری؛ نگه‌داشتن سه‌ثانیه‌ای، تأیید و شمارش معکوس" }
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed=false); val released=withTimeoutOrNull(3000L) { waitForUpOrCancellation() }; if(released==null) showConfirm=true } }
                .semantics { onClick(label="درخواست SOS (با تأیید و شمارش معکوس)") { showConfirm=true;true } },contentAlignment=Alignment.Center) {
                Text("SOS\nنگه‌دار ۳ ثانیه",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Black)
            }
            Text("فعال‌سازی نیازمند تأیید است و پیش از ارسال یک شمارش معکوس کوتاه دارد.",style=MaterialTheme.typography.bodySmall)
        }
        if(queued) GlCard {
            Text(if(queuedState=="EXPIRED_NOT_DELIVERED") "درخواست SOS منقضی شد؛ تحویل Parent تأیید نشده است." else "SOS در صف امن دستگاه است؛ تا اتصال و sync، تحویل Parent تأیید نشده است.",fontWeight=FontWeight.Bold)
            Text("اطلاعات صف با Android Keystore رمز شده و پس از sync موفق پاک می‌شود.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={scope.launch { runCatching { SosOutbox.syncPending() };refresh() }}) { Text("تلاش برای همگام‌سازی") }
            TextButton(onClick={showCancel=true}) { Text("لغو درخواست صف‌شده") }
        }
        queuedState?.takeIf { it.startsWith("FAILED") || it.startsWith("TRACKING") || it.startsWith("EXPIRED") || it.startsWith("CANCELLED") }?.let { Text("وضعیت محلی: $it · تحویل Parent تأیید نشده است.",color=MaterialTheme.colorScheme.error) }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
    if(showConfirm) AlertDialog(onDismissRequest={showConfirm=false},title={Text("فعال‌سازی SOS؟")},text={Text("پس از فعال‌سازی، Parentهای خانواده از طریق برنامه مطلع می‌شوند. سرویس امدادی ثالث خبر نمی‌شود. شمارش معکوس قابل لغو است.")},confirmButton={TextButton(onClick={showConfirm=false;countdown=3}) { Text("ادامه") }},dismissButton={TextButton(onClick={showConfirm=false}) { Text("انصراف") }})
    if(countdown>0) AlertDialog(onDismissRequest={countdown=0},title={Text("ارسال SOS تا $countdown ثانیهٔ دیگر")},text={Text("برای جلوگیری از فعال‌سازی اشتباهی می‌توانید لغو کنید.")},confirmButton={TextButton(onClick={countdown=0}) { Text("لغو شمارش") }},dismissButton={})
    if(showLocationPermission) AlertDialog(onDismissRequest={showLocationPermission=false},title={Text("مجوز رسمی مکان")},text={Text("برای ردیابی اختیاری SOS، اجازهٔ مکان لازم است. درخواست از پنجرهٔ رسمی Android انجام می‌شود؛ رد مجوز، ثبت SOS را متوقف نمی‌کند.")},confirmButton={TextButton(onClick={permissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION));showLocationPermission=false}) { Text("درخواست مجوز") }},dismissButton={TextButton(onClick={showLocationPermission=false}) { Text("بعداً") }})
    if(showCancel) AlertDialog(onDismissRequest={showCancel=false},title={Text("لغو SOS")},text={Column { Text(if(session==null) "با تأیید، فقط درخواست صف‌شده از این دستگاه حذف می‌شود؛ چون هنوز sync نشده، Parent از آن مطلع نخواهد شد." else "لغو فقط پس از پاسخ موفق سرور ثبت می‌شود؛ اگر شبکه قطع باشد، وضعیت فعال می‌ماند و باید دوباره تلاش کنید.");OutlinedTextField(value=cancelReason,onValueChange={cancelReason=it},label={Text("دلیل لغو")}) }},confirmButton={TextButton(onClick={
        showCancel=false
        scope.launch {
            val current=session
            if(current==null) { SosOutbox.discardPending("CANCELLED_BEFORE_DELIVERY");queued=false;queuedState="CANCELLED_BEFORE_DELIVERY" }
            else runCatching { session=GlServices.api.cancelSos(current.id,cancelReason) }.onSuccess { SosLocationService.stop(context) }.onFailure { error="لغو به سرور نرسید؛ وضعیت قبلی باقی می‌ماند." }
        }
    }) { Text("تأیید لغو") }},dismissButton={TextButton(onClick={showCancel=false}) { Text("بازگشت") }})
}

private fun statusFa(s:String)=when(s){"ACTIVE"->"فعال";"ACKNOWLEDGED"->"دریافت‌شدن تأیید شده";"RESOLVED"->"حل‌شده";"CANCELLED"->"لغوشده";"EXPIRED"->"منقضی‌شده";"FAILED"->"ناموفق";else->s}
private fun trackingFa(s:String,r:String?)=when(s){"ACTIVE"->"فعال تا پایان بازهٔ انتخاب‌شده";"LIMITED"->"محدود · ${reasonFa(r)}";else->"متوقف"}
private fun reasonFa(s:String?)=when(s){"location_sharing_disabled"->"اشتراک موقعیت خاموش است";"location_permission_unavailable","background_permission_unavailable"->"مجوز مکان در دسترس نیست";"location_provider_disabled"->"سرویس مکان خاموش است";"provider_failure"->"ارائه‌دهندهٔ مکان خطا داد";"fgs_start_failure"->"سرویس قابل مشاهدهٔ Android آغاز نشد";else->"قابلیت در دسترس نیست"}
private fun networkFa(s:String)=when(s){"wifi"->"Wi‑Fi";"cellular"->"دادهٔ همراه";"ethernet"->"اترنت";"none"->"آفلاین";"limited"->"محدود";else->"نامشخص"}
private fun permissionFa(s:String)=when(s){"granted"->"مجوز هنگام استفاده";"background_granted"->"مجوز پس‌زمینه";"denied"->"ردشده";else->"نامشخص"}
private fun localTimeFa(raw:String)=runCatching { java.time.OffsetDateTime.parse(raw).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")) }.getOrDefault(raw)
