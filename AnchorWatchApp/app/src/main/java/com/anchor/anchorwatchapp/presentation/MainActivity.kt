/*
 * Launcher Activity — navigates between MainWatchScreen and SosScreen.
 * Starts FallDetectionService on launch so fall monitoring runs as long as
 * this activity has been opened once. Ambient mode is not supported here
 * (would require androidx.wear:wear FragmentActivity instead of ComponentActivity).
 */

package com.anchor.anchorwatchapp.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.lifecycleScope
import com.anchor.watch.LanguageSelectionActivity
import com.anchor.watch.network.PartnerApi
import com.anchor.watch.network.WatchKeyStore
import com.anchor.watch.screens.MainWatchScreen
import com.anchor.watch.screens.SosScreen
import com.anchor.watch.screens.WatchPairingScreen
import com.anchor.watch.services.FallDetectionService
import com.anchor.watch.services.HealthMetricsService
import com.anchor.watch.services.MedicationScheduler
import com.anchor.watch.services.MedicationSyncWorker
import com.anchor.watch.services.WatchFcmService
import com.anchor.watch.utils.LanguagePreference
import com.anchor.watch.utils.LocaleHelper
import android.util.Log
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AnchorMainActivity"
        private const val HEALTH_HR = "android.permission.health.READ_HEART_RATE"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    private fun registerIfReady() {
        lifecycleScope.launch { HealthMetricsService.register(applicationContext) }
    }

    private val requestHealthHeartRatePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "health.READ_HEART_RATE permission result: granted=$granted")
            registerIfReady()
        }

    private val requestActivityRecognitionPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "ACTIVITY_RECOGNITION permission result: granted=$granted")
            if (checkSelfPermission(HEALTH_HR) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Launching health.READ_HEART_RATE request")
                requestHealthHeartRatePermission.launch(HEALTH_HR)
            } else {
                registerIfReady()
            }
        }

    private val requestBodySensorsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "BODY_SENSORS permission result: granted=$granted")
            if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.BODY_SENSORS)) {
                Log.d(TAG, "BODY_SENSORS permanently denied — opening app settings")
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
                return@registerForActivityResult
            }
            if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Launching ACTIVITY_RECOGNITION request")
                requestActivityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else if (checkSelfPermission(HEALTH_HR) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Launching health.READ_HEART_RATE request")
                requestHealthHeartRatePermission.launch(HEALTH_HR)
            } else {
                registerIfReady()
            }
        }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "LOCATION permission result: granted=$granted")
            val bodySensorsGranted = checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "After location callback — BODY_SENSORS currently granted=$bodySensorsGranted")
            if (!bodySensorsGranted) {
                Log.d(TAG, "Launching BODY_SENSORS request")
                requestBodySensorsPermission.launch(Manifest.permission.BODY_SENSORS)
            } else if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Launching ACTIVITY_RECOGNITION request")
                requestActivityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else if (checkSelfPermission(HEALTH_HR) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Launching health.READ_HEART_RATE request")
                requestHealthHeartRatePermission.launch(HEALTH_HR)
            } else {
                registerIfReady()
            }
        }

    private sealed interface Screen {
        data object Loading : Screen
        data object Pairing : Screen
        data object Main : Screen
        data object Sos : Screen
    }

    // Start in Loading so we never flash the wrong screen before the key check completes.
    private var screen by mutableStateOf<Screen>(Screen.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!LanguagePreference.isConfigured(this)) {
            val intent = Intent(this, LanguageSelectionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish()
            return
        }

        // Check for a stored watch API key and decide which screen to show.
        lifecycleScope.launch {
            val hasKey = WatchKeyStore.get(applicationContext).apiKey() != null
            screen = if (hasKey) Screen.Main else Screen.Pairing
            if (hasKey) {
                // Paired: pull today's reminders + reschedule alarms now, and keep them
                // fresh in the background. Without this the watch never armed any alarm.
                runCatching { MedicationScheduler.syncAndReschedule(applicationContext) }
                MedicationSyncWorker.enqueuePeriodic(applicationContext)
                // Register FCM token in case onNewToken fired before pairing completed.
                runCatching { WatchFcmService.registerSavedTokenIfPaired(applicationContext) }
            }
        }

        val locationGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val activityRecognitionGranted = checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val healthHrGranted = checkSelfPermission(HEALTH_HR) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "onCreate: location=$locationGranted, bodySensors=$bodySensorsGranted, activityRecognition=$activityRecognitionGranted, healthHr=$healthHrGranted")

        when {
            !locationGranted -> {
                Log.d(TAG, "Launching LOCATION request (will chain to BODY_SENSORS → ACTIVITY_RECOGNITION → health.READ_HEART_RATE)")
                requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            !bodySensorsGranted -> {
                Log.d(TAG, "Launching BODY_SENSORS request directly")
                requestBodySensorsPermission.launch(Manifest.permission.BODY_SENSORS)
            }
            !activityRecognitionGranted -> {
                Log.d(TAG, "Launching ACTIVITY_RECOGNITION request directly")
                requestActivityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            !healthHrGranted -> {
                Log.d(TAG, "Launching health.READ_HEART_RATE request directly")
                requestHealthHeartRatePermission.launch(HEALTH_HR)
            }
            else -> Log.d(TAG, "All permissions already granted")
        }

        FallDetectionService.start(applicationContext)
        if (bodySensorsGranted && activityRecognitionGranted && healthHrGranted) {
            Log.d(TAG, "All health permissions granted — registering Health Services passive listener")
            lifecycleScope.launch { HealthMetricsService.register(applicationContext) }
        }
        val layoutDirection = LocaleHelper.layoutDirection(this)
        // Computed once before setContent to avoid repeated calls on every recomposition.
        val watchDeviceName: String = Settings.Global.getString(contentResolver, "device_name")
            ?: Build.MODEL

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                when (screen) {
                    Screen.Loading -> Unit  // blank while key check runs (fast, ~50 ms)

                    Screen.Pairing -> WatchPairingScreen(
                        pairingApi = PartnerApi.pairing(applicationContext),
                        watchKeyStore = WatchKeyStore.get(applicationContext),
                        deviceName = watchDeviceName,
                        onPaired = {
                            screen = Screen.Main
                            lifecycleScope.launch {
                                runCatching { MedicationScheduler.syncAndReschedule(applicationContext) }
                                MedicationSyncWorker.enqueuePeriodic(applicationContext)
                                // Token may have been saved before pairing — register it now.
                                runCatching { WatchFcmService.registerSavedTokenIfPaired(applicationContext) }
                            }
                        },
                    )

                    Screen.Main -> MainWatchScreen(
                        isAmbient = false,
                        onSosClick = { screen = Screen.Sos },
                    )

                    Screen.Sos -> SosScreen(
                        onDismiss = { screen = Screen.Main },
                    )
                }
            }
        }
    }
}
