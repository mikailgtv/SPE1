package ir.guardianlink.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.network.SosLocationOut
import kotlin.math.cos
import kotlin.math.max

/** Local-only SOS track sketch: no tile requests or coordinates sent to a map provider. */
@Composable
internal fun LocalSosTrackMap(points: List<SosLocationOut>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val startColor = Color(0xFF168A55)
    val endColor = Color(0xFFD32F2F)
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(surface)) {
        Canvas(Modifier.matchParentSize().padding(14.dp)) {
            val latitudes = points.map { it.latitude }
            val longitudes = points.map { it.longitude }
            val rawLat = (latitudes.maxOrNull() ?: 0.0) - (latitudes.minOrNull() ?: 0.0)
            val rawLon = (longitudes.maxOrNull() ?: 0.0) - (longitudes.minOrNull() ?: 0.0)
            val midLat = latitudes.average().takeIf { it.isFinite() } ?: 0.0
            val latSpan = max(rawLat, 0.001)
            val lonSpan = max(rawLon, 0.001 / max(cos(Math.toRadians(midLat)), 0.1))
            val minLat = (latitudes.minOrNull() ?: 0.0) - latSpan * 0.15
            val maxLat = (latitudes.maxOrNull() ?: 0.0) + latSpan * 0.15
            val minLon = (longitudes.minOrNull() ?: 0.0) - lonSpan * 0.15
            val maxLon = (longitudes.maxOrNull() ?: 0.0) + lonSpan * 0.15
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            for (i in 1..3) {
                val x = w * i / 4f
                val y = h * i / 4f
                drawLine(grid, Offset(x, 0f), Offset(x, h), 1f)
                drawLine(grid, Offset(0f, y), Offset(w, y), 1f)
            }
            fun project(lat: Double, lon: Double) = Offset(
                (((lon - minLon) / (maxLon - minLon)) * w).toFloat(),
                (((maxLat - lat) / (maxLat - minLat)) * h).toFloat(),
            )
            val path = Path()
            points.forEachIndexed { index, point ->
                val p = project(point.latitude, point.longitude)
                if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            if (points.size > 1) drawPath(path, lineColor, style = Stroke(width = 5f))
            points.firstOrNull()?.let { drawCircle(startColor, radius = 8f, center = project(it.latitude, it.longitude)) }
            points.lastOrNull()?.let { latest ->
                val center = project(latest.latitude, latest.longitude)
                val metersPerPixel = max((maxLat - minLat) * 111_320.0 / h, 0.1)
                val accuracyRadius = ((latest.accuracyM ?: 0f) / metersPerPixel).toFloat().coerceIn(0f, max(w, h) * 0.45f)
                if (accuracyRadius > 0f) drawCircle(endColor.copy(alpha = 0.16f), accuracyRadius, center)
                drawCircle(endColor, radius = 9f, center = center)
            }
        }
        Text("نمای شماتیک محلی · بدون بارگذاری کاشی یا ارسال مختصات به ارائه‌دهندهٔ نقشه",modifier=Modifier.align(Alignment.BottomCenter).background(surface.copy(alpha=0.9f)).padding(6.dp),style=MaterialTheme.typography.labelSmall)
    }
}
