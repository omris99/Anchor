package com.anchor.watch.utils

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val MAX_CACHED_LOCATION_AGE_MS = 5 * 60 * 1_000L
private const val LIVE_FIX_TIMEOUT_MS = 20_000L

/**
 * Tries last-known location first (if under 5 min old),
 * then falls back to a live fix from all enabled providers (up to 20 s).
 * Returns null if permission is missing or no fix is available.
 */
suspend fun requestBestLocation(context: Context): Location? {
    val hasFineLocationPermission = context.checkSelfPermission(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasFineLocationPermission) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null

    val enabledProviders = locationManager.allProviders.filter { providerName ->
        runCatching { locationManager.isProviderEnabled(providerName) }.getOrDefault(false)
    }

    val cachedLocation = runCatching {
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
    }.getOrNull()

    val cachedLocationAgeMs = cachedLocation?.let { System.currentTimeMillis() - it.time }
        ?: Long.MAX_VALUE
    if (cachedLocation != null && cachedLocationAgeMs < MAX_CACHED_LOCATION_AGE_MS) {
        return cachedLocation
    }

    return withTimeoutOrNull(LIVE_FIX_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val locationListener = object : LocationListener {
                override fun onLocationChanged(freshLocation: Location) {
                    runCatching { locationManager.removeUpdates(this) }
                    continuation.resume(freshLocation)
                }
            }
            if (enabledProviders.isEmpty()) {
                continuation.resume(null)
            } else {
                enabledProviders.forEach { providerName ->
                    runCatching {
                        locationManager.requestLocationUpdates(
                            providerName, 0L, 0f, locationListener, Looper.getMainLooper()
                        )
                    }
                }
            }
            continuation.invokeOnCancellation {
                runCatching { locationManager.removeUpdates(locationListener) }
            }
        }
    }
}
