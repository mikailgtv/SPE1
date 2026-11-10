package ir.guardianlink.feature.location

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * پیاده‌سازی provider نقشه با کاشی‌های raster اوپن‌استریت‌مپ — بدون API key.
 * - فقط کاشی‌های نمای فعلی، با cache حافظه (LruCache) و سقف زوم ۱۸.
 * - حجم درخواست بسیار کم (کاربری خانوادگی) + User-Agent شناسا طبق سیاست مصرف OSM.
 * - attribution در گوشه‌ی نقشه درج می‌شود (الزام license OSM).
 */
object OsmTileMapProvider : MapProvider {
    override val id = "osm-raster"
    override val displayName = "OpenStreetMap"
    override val attribution = "© OpenStreetMap contributors"

    private const val TILE = 256.0
    private const val MAX_ZOOM = 18f
    private const val MIN_ZOOM = 2f
    private const val CACHE_SIZE = 48
    private const val MAX_TILES_PER_LOAD = 24
    private const val FETCH_HALF_SPAN = 320.0 // پیکسل؛ کمی بیش‌برآورد برای نرمی pan

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(10)).build()
    }
    private val cache = LruCache<String, ImageBitmap>(CACHE_SIZE)

    // ---------- ریاضی slippy-map ----------
    private fun worldSize(zoom: Float): Double = TILE * Math.pow(2.0, zoom.toDouble())
    private fun lonToX(lon: Double, zoom: Float): Double = (lon + 180.0) / 360.0 * worldSize(zoom)
    private fun latToY(lat: Double, zoom: Float): Double {
        val rad = Math.toRadians(lat.coerceIn(-85.05, 85.05))
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * worldSize(zoom)
    }
    private fun xToLon(x: Double, zoom: Float): Double = x / worldSize(zoom) * 360.0 - 180.0
    private fun yToLat(y: Double, zoom: Float): Double =
        Math.toDegrees(atan(sinh(Math.PI * (1.0 - 2.0 * y / worldSize(zoom)))))

    @Composable
    override fun MapSurface(
        camera: MapCamera,
        marker: LatLon?,
        onCameraChange: (MapCamera) -> Unit,
        modifier: Modifier,
        track: List<LatLon>,
        startMarker: LatLon?,
        accuracyMeters: Float?,
    ) {
        var version by remember { mutableIntStateOf(0) }
        Box(modifier) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            val z = (camera.zoom + log2(max(zoomChange, 0.05f))).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            val scale = Math.pow(2.0, (z - camera.zoom).toDouble())
                            // pan در پیکسلِ زوم فعلی اعمال و سپس برای زوم جدید مقیاس می‌شود
                            val cx = (lonToX(camera.center.lon, camera.zoom) - pan.x) * scale
                            val cy = (latToY(camera.center.lat, camera.zoom) - pan.y) * scale
                            onCameraChange(
                                MapCamera(
                                    LatLon(
                                        yToLat(cy, z).coerceIn(-85.0, 85.0),
                                        xToLon(cx, z).coerceIn(-180.0, 180.0),
                                    ),
                                    z,
                                ),
                            )
                        }
                    },
            ) {
                // خواندن version در فاز draw ⇒ با رسیدن کاشی جدید، بازترسیم
                if (version >= 0) drawTiles(camera, marker, track, startMarker, accuracyMeters)
            }
            Text(
                attribution,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        LaunchedEffect(camera.center.lat, camera.center.lon, camera.zoom) {
            loadVisibleTiles(camera) { version++ }
        }
    }

    private fun DrawScope.drawTiles(camera: MapCamera, marker: LatLon?, track:List<LatLon>, startMarker:LatLon?, accuracyMeters:Float?) {
        val z = camera.zoom.roundToInt().coerceIn(MIN_ZOOM.toInt(), MAX_ZOOM.toInt())
        val zf = z.toFloat()
        val centerWX = lonToX(camera.center.lon, zf)
        val centerWY = latToY(camera.center.lat, zf)
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        val n = 1 shl z
        val txMin = ((centerWX - halfW) / TILE).toInt()
        val txMax = ((centerWX + halfW) / TILE).toInt()
        val tyMin = ((centerWY - halfH) / TILE).toInt()
        val tyMax = ((centerWY + halfH) / TILE).toInt()
        for (tx in txMin..txMax) {
            for (ty in tyMin..tyMax) {
                if (tx < 0 || ty < 0 || tx >= n || ty >= n) continue
                val bmp = cache.get("$z/$tx/$ty") ?: continue
                val left = (tx * TILE - centerWX + halfW).roundToInt()
                val top = (ty * TILE - centerWY + halfH).roundToInt()
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(left, top),
                    dstSize = IntSize(TILE.toInt(), TILE.toInt()),
                )
            }
        }
        fun screen(point:LatLon)=Offset((lonToX(point.lon,zf)-centerWX+halfW).toFloat(),(latToY(point.lat,zf)-centerWY+halfH).toFloat())
        if(track.size>=2) {
            val path=Path(); track.forEachIndexed { index,p -> val xy=screen(p); if(index==0) path.moveTo(xy.x,xy.y) else path.lineTo(xy.x,xy.y) }
            drawPath(path,Color(0xFF7B1FA2),style=Stroke(width=5f))
        }
        if(marker!=null && accuracyMeters!=null && accuracyMeters>0f) {
            val mpp=40_075_016.686 * cos(Math.toRadians(marker.lat)) / worldSize(zf)
            val radius=(accuracyMeters/mpp).toFloat().coerceIn(2f,250f)
            drawCircle(Color(0x332196F3),radius,screen(marker))
            drawCircle(Color(0xAA2196F3),radius,screen(marker),style=Stroke(width=2f))
        }
        startMarker?.let { start -> drawCircle(Color.White,12f,screen(start));drawCircle(Color(0xFF2E7D32),8f,screen(start)) }
        // نشانگر 📍 مکان فعلی/آخرین نقطه
        if (marker != null) {
            val mx = (lonToX(marker.lon, zf) - centerWX + halfW).toFloat()
            val my = (latToY(marker.lat, zf) - centerWY + halfH).toFloat()
            if (mx >= -40 && mx <= size.width + 40 && my >= -40 && my <= size.height + 40) {
                drawCircle(color = Color.White, radius = 16f, center = Offset(mx, my))
                drawCircle(color = Color(0xFFD32F2F), radius = 12f, center = Offset(mx, my))
                drawCircle(color = Color.White, radius = 4f, center = Offset(mx, my))
            }
        }
    }

    private suspend fun loadVisibleTiles(camera: MapCamera, onLoaded: () -> Unit) {
        val z = camera.zoom.roundToInt().coerceIn(MIN_ZOOM.toInt(), MAX_ZOOM.toInt())
        val zf = z.toFloat()
        val centerWX = lonToX(camera.center.lon, zf)
        val centerWY = latToY(camera.center.lat, zf)
        val n = 1 shl z
        val txMin = ((centerWX - FETCH_HALF_SPAN) / TILE).toInt()
        val txMax = ((centerWX + FETCH_HALF_SPAN) / TILE).toInt()
        val tyMin = ((centerWY - FETCH_HALF_SPAN) / TILE).toInt()
        val tyMax = ((centerWY + FETCH_HALF_SPAN) / TILE).toInt()
        val missing = mutableListOf<String>()
        for (tx in txMin..txMax) {
            for (ty in tyMin..tyMax) {
                if (tx < 0 || ty < 0 || tx >= n || ty >= n) continue
                val key = "$z/$tx/$ty"
                if (cache.get(key) == null) missing.add(key)
            }
        }
        if (missing.isEmpty()) return
        val loaded = AtomicInteger(0)
        coroutineScope {
            for (key in missing.take(MAX_TILES_PER_LOAD)) {
                launch(Dispatchers.IO) {
                    if (fetchTile(key) != null) loaded.incrementAndGet()
                }
            }
        }
        if (loaded.get() > 0) onLoaded()
    }

    private fun fetchTile(key: String): ImageBitmap? {
        return try {
            val req = Request.Builder()
                .url("https://tile.openstreetmap.org/$key.png")
                .header("User-Agent", "GuardianLinkFamilyApp/1.0 (low-volume family safety app)")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { cache.put(key, it) }
            }
        } catch (_: Exception) {
            null
        }
    }
}
