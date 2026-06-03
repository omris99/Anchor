package com.anchor.watch

import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.lifecycleScope
import com.anchor.watch.data.CheckInContext
import com.anchor.watch.data.CheckInRepository
import com.anchor.watch.data.local.CheckInLocalStore
import com.anchor.watch.network.PartnerApi
import com.anchor.watch.screens.DailyCheckInScreen
import com.anchor.watch.services.CheckInSyncWorker
import com.anchor.watch.utils.LocaleHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class CheckInActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CheckInActivity"
    }

    // Updated in the background while the user sees the check-in screen.
    // contextProvider captures this var by reference, so submit() always reads the latest value.
    private var checkInContext = CheckInContext()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        // Fill battery immediately (instant), then fetch live location in the background.
        checkInContext = CheckInContext(batteryPercent = readBatteryPercent())
        lifecycleScope.launch {
            val location = requestLocation()
            checkInContext = checkInContext.copy(
                lat = location?.latitude,
                lng = location?.longitude,
            )
            Log.d(TAG, "context ready: lat=${checkInContext.lat} lng=${checkInContext.lng} battery=${checkInContext.batteryPercent}")
        }

        val repository = CheckInRepository(
            store = CheckInLocalStore(applicationContext),
            api = PartnerApi.checkIn(applicationContext),
            onQueueForRetry = { CheckInSyncWorker.enqueue(applicationContext) },
        )

        val layoutDirection = LocaleHelper.layoutDirection(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                DailyCheckInScreen(
                    repository = repository,
                    contextProvider = { checkInContext },
                    onFinished = { finish() },
                )
            }
        }
    }

    // Tries last-known from any provider first (instant), then requests a live fix
    // from all enabled providers simultaneously (up to 20 s).
    private suspend fun requestLocation(): Location? {
        val permissionGranted = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "location: permission granted=$permissionGranted")
        if (!permissionGranted) return null

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager

        val allProviders = lm.allProviders
        val enabledProviders = allProviders.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        Log.d(TAG, "location: allProviders=$allProviders enabledProviders=$enabledProviders")

        val lastKnown = runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }.getOrNull()
        Log.d(TAG, "location: lastKnown=$lastKnown")
        if (lastKnown != null) return lastKnown

        Log.d(TAG, "location: requesting live fix from providers=$enabledProviders")
        return withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        Log.d(TAG, "location: fix received lat=${loc.latitude} lng=${loc.longitude} provider=${loc.provider}")
                        runCatching { lm.removeUpdates(this) }
                        cont.resume(loc)
                    }
                }
                if (enabledProviders.isEmpty()) {
                    Log.e(TAG, "location: no enabled providers — cannot request fix")
                    cont.resume(null)
                } else {
                    enabledProviders.forEach { provider ->
                        runCatching {
                            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                            Log.d(TAG, "location: requestLocationUpdates registered for $provider")
                        }.onFailure { Log.e(TAG, "location: requestLocationUpdates failed for $provider", it) }
                    }
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        }.also { result ->
            if (result == null) Log.e(TAG, "location: timeout after 20s — no fix received")
        }
    }

    private fun readBatteryPercent(): Int? {
        val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager
        if (bm == null) { Log.e(TAG, "battery: BatteryManager service is null"); return null }
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        Log.d(TAG, "battery: level=$level")
        return if (level >= 0) level else null
    }
}
