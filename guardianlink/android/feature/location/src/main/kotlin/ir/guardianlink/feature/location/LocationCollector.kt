package ir.guardianlink.feature.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import ir.guardianlink.core.network.LocationPointIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Single-shot, battery-aware location capture.
 * - Try Google Play Fused Location Provider when present.
 * - Fall back to official Android LocationManager when Play Services is missing/unavailable.
 * - No persistent listener, background service, or child notification.
 * - Last-known fixes older than 48h are never transmitted.
 */
object LocationCollector {
    private const val FIX_TIMEOUT_MS = 12_000L
    private const val LAST_KNOWN_MAX_AGE_MS = 48L * 3600 * 1000

    @SuppressLint("MissingPermission") // caller checks LocationPermission first
    suspend fun captureFix(context: Context): LocationPointIn? {
        if (!LocationPermission.fineGranted(context) || !LocationPermission.servicesEnabled(context)) return null
        var loc: Location? = null
        var source = "fused"

        // Google Play fused API is preferred, but optional for capture.
        runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            loc = withTimeoutOrNull(FIX_TIMEOUT_MS) { currentFix(client) }
            if (loc == null) {
                loc = lastKnownFix(client)
                if (loc != null) source = "last_known"
            }
        }
        if (loc != null && System.currentTimeMillis() - loc!!.time > LAST_KNOWN_MAX_AGE_MS) loc = null
        if (loc == null) {
            val platform = withTimeoutOrNull(FIX_TIMEOUT_MS) { platformCurrentFix(context) }
            loc = platform?.first
            source = platform?.second ?: "gps"
        }
        if (loc == null) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
            val best = providers.asSequence().mapNotNull { name -> runCatching { lm.getLastKnownLocation(name) }.getOrNull() }
                .maxByOrNull { it.time }
            if (best != null && best.provider != null) {
                loc = best
                source = "last_known"
            }
        }
        if (loc != null && System.currentTimeMillis() - loc!!.time > LAST_KNOWN_MAX_AGE_MS) loc = null
        if (loc == null) {
            AppLog.info(Cat.DEVICE, "no location fix this cycle")
            return null
        }
        val fix = loc!!
        return LocationPointIn(
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracyM = if (fix.hasAccuracy()) fix.accuracy else null,
            clientTs = fix.time,
            source = source,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(client: com.google.android.gms.location.FusedLocationProviderClient): Location? =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            try {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc: Location? -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { runCatching { cts.cancel() } }
        }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownFix(client: com.google.android.gms.location.FusedLocationProviderClient): Location? =
        suspendCancellableCoroutine { cont ->
            try {
                client.lastLocation
                    .addOnSuccessListener { loc: Location? -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    /** Android framework fallback works without Google Play Services. */
    @SuppressLint("MissingPermission")
    private suspend fun platformCurrentFix(context: Context): Pair<Location, String>? =
        suspendCancellableCoroutine { cont ->
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = runCatching {
                lm.getBestProvider(Criteria().apply { accuracy = Criteria.ACCURACY_COARSE; powerRequirement = Criteria.POWER_LOW }, true)
                    ?: listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { lm.isProviderEnabled(it) }
            }.getOrNull()
            if (provider == null) { cont.resume(null); return@suspendCancellableCoroutine }
            val cleanup: () -> Unit
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellation = android.os.CancellationSignal()
                cleanup = { runCatching { cancellation.cancel() }; Unit }
                try {
                    lm.getCurrentLocation(provider, cancellation, context.mainExecutor) { result ->
                        if (cont.isActive) cont.resume(result?.let { it to providerSource(provider) })
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location to providerSource(provider))
                        runCatching { lm.removeUpdates(this) }
                    }
                    @Deprecated("Deprecated by Android") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                cleanup = { runCatching { lm.removeUpdates(listener) }; Unit }
                try { lm.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }
                catch (_: Exception) { if (cont.isActive) cont.resume(null) }
            }
            cont.invokeOnCancellation { cleanup() }
        }

    private fun providerSource(provider: String): String =
        if (provider == LocationManager.GPS_PROVIDER) "gps" else "network"
}
