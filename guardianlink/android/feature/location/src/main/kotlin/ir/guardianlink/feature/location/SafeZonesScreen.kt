package ir.guardianlink.feature.location

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlErrorBanner
import ir.guardianlink.core.designsystem.GlPrimaryButton
import ir.guardianlink.core.network.GeofenceOut
import ir.guardianlink.core.network.GeofenceReq
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.LocationCurrentOut
import kotlinx.coroutines.launch

/** Parent Safe Zones: server-owned list, current state and event history. No child controls. */
@Composable
fun SafeZonesScreen(deviceId: String, onBack: () -> Unit, onOpenHistory: () -> Unit) {
    val scope = rememberCoroutineScope()
    var zones by remember { mutableStateOf<List<GeofenceOut>>(emptyList()) }
    var policy by remember { mutableStateOf<ir.guardianlink.core.network.GeofenceListOut?>(null) }
    var location by remember { mutableStateOf<LocationCurrentOut?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<GeofenceOut?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GeofenceOut?>(null) }
    var mapCamera by remember { mutableStateOf(MapCamera(DEFAULT_CENTER, 13f)) }
    suspend fun refresh() {
        loading = true
        runCatching { GlServices.api.geofences(deviceId) }.onSuccess { zones = it.zones; policy = it; error = null }
            .onFailure { error = it.message ?: "Request failed" }
        location = runCatching { GlServices.api.locationCurrent(deviceId) }.getOrNull()
        loading = false
    }
    LaunchedEffect(deviceId) { refresh() }
    LaunchedEffect(zones) {
        zones.firstOrNull { it.enabled }?.let { mapCamera = MapCamera(LatLon(it.latitude, it.longitude), 14f) }
            ?: location?.fix?.let { mapCamera = MapCamera(LatLon(it.latitude, it.longitude), 14f) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("محدوده‌های امن", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        Text("تشخیص با گزارش‌های موقعیت دریافتی انجام می‌شود؛ تحویل رویداد لحظه‌ای نیست.", style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            MapProviders.current.MapSurface(mapCamera, location?.fix?.let { LatLon(it.latitude, it.longitude) }, { mapCamera = it }, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val z = mapCamera.zoom
                val world = 256.0 * Math.pow(2.0, z.toDouble())
                fun px(lon: Double) = (lon + 180.0) / 360.0 * world
                fun py(lat: Double): Double {
                    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
                    return (1.0 - kotlin.math.ln(kotlin.math.tan(r) + 1.0 / kotlin.math.cos(r)) / Math.PI) / 2.0 * world
                }
                val cx = px(mapCamera.center.lon); val cy = py(mapCamera.center.lat)
                zones.filter { it.enabled }.forEach { zone ->
                    val x = (px(zone.longitude) - cx + size.width / 2.0).toFloat()
                    val y = (py(zone.latitude) - cy + size.height / 2.0).toFloat()
                    val metresPerPixel = 40_075_016.686 * kotlin.math.cos(Math.toRadians(zone.latitude)) / world
                    val radiusPx = (zone.radiusM / metresPerPixel).toFloat().coerceIn(6f, maxOf(size.width, size.height))
                    drawCircle(Color(0x553B82F6), radiusPx, Offset(x, y))
                    drawCircle(Color(0xFF2563EB), radiusPx, Offset(x, y), style = Stroke(width = 2.dp.toPx()))
                    drawCircle(Color(0xFFDC2626), 5.dp.toPx(), Offset(x, y))
                }
            }
        }
        error?.let { GlErrorBanner(it) }
        if (loading) Text("در حال بارگذاری…")
        zones.forEach { zone ->
            GlCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("📍 ${zone.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stateLabel(zone.state), style = MaterialTheme.typography.bodyMedium)
                        Text("${zone.radiusM} m · ${if (zone.enabled) "فعال" else "غیرفعال"}")
                        zone.lastKnownAccuracy?.let { Text("دقت: ${it.toInt()} متر") }
                        zone.lastEvent?.let { Text("آخرین رویداد: ${it.eventType.removePrefix("GEOFENCE_")} · ${it.ts}", style = MaterialTheme.typography.bodySmall) }
                    }
                    Switch(checked = zone.enabled, onCheckedChange = { enabled ->
                        scope.launch {
                            runCatching { GlServices.api.updateGeofence(deviceId, zone.id, zone.toReq(enabled)) }
                                .onFailure { error = it.message }
                            refresh()
                        }
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { edit = zone }) { Text("جزئیات / ویرایش") }
                    TextButton(onClick = { deleting = zone }) { Text("حذف") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("هشدار ورود", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = zone.notifyOnEntry, onCheckedChange = { v ->
                        scope.launch { runCatching { GlServices.api.updateGeofence(deviceId, zone.id, zone.toReq(entry = v)) }.onFailure { error = it.message }; refresh() }
                    })
                    Text("هشدار خروج", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = zone.notifyOnExit, onCheckedChange = { v ->
                        scope.launch { runCatching { GlServices.api.updateGeofence(deviceId, zone.id, zone.toReq(exit = v)) }.onFailure { error = it.message }; refresh() }
                    })
                }
            }
        }
        if (zones.isEmpty() && !loading) Text("هنوز محدوده امنی ثبت نشده است.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlPrimaryButton("+ افزودن محدوده امن", onClick = { showAdd = true })
            OutlinedButton(onClick = onOpenHistory) { Text("تاریخچه محدوده‌ها") }
        }
        Text("فعال: ${zones.count { it.enabled }} از ۵۰", style = MaterialTheme.typography.bodySmall)
    }
    if (showAdd) ZoneEditorDialog(null, location, policy?.minRadiusM ?: 50, policy?.maxRadiusM ?: 5000, onDismiss = { showAdd = false }, onSave = { req ->
        scope.launch { runCatching { GlServices.api.createGeofence(deviceId, req) }.onFailure { error = it.message }; showAdd = false; refresh() }
    })
    edit?.let { current -> ZoneEditorDialog(current, location, policy?.minRadiusM ?: 50, policy?.maxRadiusM ?: 5000, onDismiss = { edit = null }, onSave = { req ->
        scope.launch { runCatching { GlServices.api.updateGeofence(deviceId, current.id, req) }.onFailure { error = it.message }; edit = null; refresh() }
    }) }
    deleting?.let { target -> AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text("محدوده «${target.name}» حذف شود؟") },
        text = { Text("محدوده و تاریخچه رویدادهایش برای همیشه حذف می‌شود.") },
        confirmButton = { TextButton(onClick = { scope.launch { runCatching { GlServices.api.deleteGeofence(deviceId, target.id) }.onFailure { error = it.message }; deleting = null; refresh() } }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف") } },
    ) }
}

@Composable
private fun ZoneEditorDialog(zone: GeofenceOut?, current: LocationCurrentOut?, minRadius: Int, maxRadius: Int, onDismiss: () -> Unit, onSave: (GeofenceReq) -> Unit) {
    var name by remember(zone?.id) { mutableStateOf(zone?.name ?: "") }
    var lat by remember(zone?.id) { mutableStateOf(zone?.latitude?.toString() ?: current?.fix?.latitude?.toString().orEmpty()) }
    var lon by remember(zone?.id) { mutableStateOf(zone?.longitude?.toString() ?: current?.fix?.longitude?.toString().orEmpty()) }
    var radius by remember(zone?.id) { mutableStateOf(zone?.radiusM ?: 300.coerceIn(minRadius, maxRadius)) }
    var entry by remember(zone?.id) { mutableStateOf(zone?.notifyOnEntry ?: true) }
    var exit by remember(zone?.id) { mutableStateOf(zone?.notifyOnExit ?: true) }
    var enabled by remember(zone?.id) { mutableStateOf(zone?.enabled ?: true) }
    var mapOpen by remember(zone?.id) { mutableStateOf(false) }
    var camera by remember(zone?.id) { mutableStateOf(MapCamera(LatLon(lat.toDoubleOrNull() ?: DEFAULT_CENTER.lat, lon.toDoubleOrNull() ?: DEFAULT_CENTER.lon), 15f)) }
    val goodLat = lat.toDoubleOrNull()?.let { it in -90.0..90.0 } == true
    val goodLon = lon.toDoubleOrNull()?.let { it in -180.0..180.0 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (zone == null) "افزودن محدوده امن" else "جزئیات محدوده امن") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true)
                OutlinedTextField(lat, { lat = it }, label = { Text("عرض جغرافیایی") }, singleLine = true)
                OutlinedTextField(lon, { lon = it }, label = { Text("طول جغرافیایی") }, singleLine = true)
                current?.fix?.let { fix -> TextButton(onClick = { lat = fix.latitude.toString(); lon = fix.longitude.toString(); camera = MapCamera(LatLon(fix.latitude, fix.longitude), 15f) }) { Text("استفاده از آخرین موقعیت دستگاه") } }
                TextButton(onClick = { mapOpen = true }) { Text("انتخاب موقعیت روی نقشه") }
                Text("شعاع ($minRadius تا $maxRadius متر)")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(100, 200, 300, 500, 1000).filter { it in minRadius..maxRadius }.forEach { n -> TextButton(onClick = { radius = n }) { Text(if (radius == n) "● ${n}m" else "${n}m") } }
                }
                OutlinedTextField(radius.toString(), { it.toIntOrNull()?.let { v -> radius = v } }, label = { Text("شعاع دلخواه") }, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("فعال"); Switch(enabled, { enabled = it }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("اعلان هنگام ورود"); Switch(entry, { entry = it }) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("اعلان هنگام خروج"); Switch(exit, { exit = it }) }
                zone?.let { Text("ایجاد: ${it.createdAt}\nویرایش: ${it.updatedAt}\nوضعیت: ${stateLabel(it.state)}", style = MaterialTheme.typography.bodySmall) }
                zone?.let {
                    Text("آخرین ورود: ${it.lastEntryAt ?: "—"}\\nآخرین خروج: ${it.lastExitAt ?: "—"}", style = MaterialTheme.typography.bodySmall)
                }
                zone?.lastEvent?.let { Text("آخرین رویداد: ${it.eventType} در ${it.ts}") }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && goodLat && goodLon && radius in minRadius..maxRadius, onClick = {
            onSave(GeofenceReq(name.trim(), lat.toDouble(), lon.toDouble(), radius, enabled, entry, exit))
        }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
    if (mapOpen) AlertDialog(
        onDismissRequest = { mapOpen = false },
        title = { Text("انتخاب مرکز محدوده") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("نقشه را جابه‌جا یا بزرگ‌نمایی کنید؛ نشانگر مرکز، موقعیت را انتخاب می‌کند.", style = MaterialTheme.typography.bodySmall)
                Box(Modifier.fillMaxWidth().height(320.dp)) {
                    MapProviders.current.MapSurface(camera, camera.center, { camera = it }, Modifier.fillMaxSize())
                }
                Text("${camera.center.lat}, ${camera.center.lon}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { lat = camera.center.lat.toString(); lon = camera.center.lon.toString(); mapOpen = false }) { Text("انتخاب مرکز نقشه") } },
        dismissButton = { TextButton(onClick = { mapOpen = false }) { Text("انصراف") } },
    )
}

private fun GeofenceOut.toReq(enabled: Boolean = this.enabled, entry: Boolean = notifyOnEntry, exit: Boolean = notifyOnExit) =
    GeofenceReq(name, latitude, longitude, radiusM, enabled, entry, exit)

@Composable
fun GeofenceHistoryScreen(deviceId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<ir.guardianlink.core.network.GeofenceEventOut>>(emptyList()) }
    var filter by remember { mutableStateOf("today") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deviceId, filter) {
        val now = java.time.OffsetDateTime.now()
        val from = when (filter) {
            "yesterday" -> now.toLocalDate().minusDays(1).atStartOfDay(now.offset).toString()
            "7d" -> now.minusDays(7).toString()
            "30d" -> now.minusDays(30).toString()
            else -> now.toLocalDate().atStartOfDay(now.offset).toString()
        }
        val to = if (filter == "yesterday") now.toLocalDate().atStartOfDay(now.offset).minusNanos(1).toString() else null
        runCatching { GlServices.api.geofenceHistory(deviceId, from, to) }
            .onSuccess { events = it.events; error = null }.onFailure { error = it.message }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("تاریخچه محدوده‌ها", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("today" to "امروز", "yesterday" to "دیروز", "7d" to "۷ روز", "30d" to "۳۰ روز").forEach { (key, title) ->
                TextButton(onClick = { filter = key }) { Text(if (filter == key) "● $title" else title) }
            }
        }
        error?.let { GlErrorBanner(it) }
        if (events.isEmpty()) Text("در این بازه رویدادی نیست.")
        events.forEach { ev ->
            GlCard {
                Text("${ev.zoneName} · ${ev.eventType.removePrefix("GEOFENCE_")}", fontWeight = FontWeight.Bold)
                Text(ev.ts)
                Text("وضعیت: ${if (ev.eventType.endsWith("ENTER")) "داخل" else if (ev.eventType.endsWith("EXIT")) "بیرون" else "خطا"}")
                ev.accuracyM?.let { Text("دقت ${it.toInt()} متر") }
                ev.dwellMin?.let { Text("مدت حضور ${it} دقیقه") }
            }
        }
    }
}

private fun stateLabel(state: String) = when (state) {
    "inside" -> "🟢 داخل"
    "outside" -> "⚪ خارج"
    "error" -> "🔴 خطا"
    else -> "⚪ نامشخص"
}
