package ir.guardianlink.feature.location

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class LatLon(val lat: Double, val lon: Double)

data class MapCamera(val center: LatLon, val zoom: Float)

/**
 * انتزاع ارائه‌دهنده‌ی نقشه (طبق معماری خواسته‌شده):
 * Business Logic (Map/History/Settings) فقط با این interface کار می‌کند؛
 * بعداً می‌توان Google Maps / Mapbox / هر provider دیگر را بدون بازنویسی
 * صفحات جایگزین کرد:  MapProviders.install(GoogleMapsProvider(buildConfigKey))
 * کلیدهای API هرگز hard-code نمی‌شوند (از local.properties/BuildConfig تزریق می‌شوند).
 */
interface MapProvider {
    val id: String
    val displayName: String

    /** سطح نقشه با دوربین قابل حرکت/زوم و یک نشانگر مکان کودک. */
    @Composable
    fun MapSurface(
        camera: MapCamera,
        marker: LatLon?,
        onCameraChange: (MapCamera) -> Unit,
        modifier: Modifier = Modifier,
        track: List<LatLon> = emptyList(),
        startMarker: LatLon? = null,
        accuracyMeters: Float? = null,
    )

    /** نسبت attribution (الزام license ارائه‌دهنده). */
    val attribution: String
}

object MapProviders {
    /** provider پیش‌فرض: کاشی‌های OpenStreetMap — بدون API key. */
    var current: MapProvider = OsmTileMapProvider
        private set

    fun install(provider: MapProvider) {
        current = provider
    }
}
