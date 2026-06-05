/*
 * Partner MainActivity — KEPT per transplant Rule 2, wired to SOURCE Navigation.
 *
 * What changed vs. partner's pre-transplant scaffold (4 lines of intent):
 *   - import com.anchor.anchorwatchapp.R          → import com.anchor.watch.R
 *     (R class moved to namespace = "com.anchor.watch" so SOURCE files'
 *      `import com.anchor.watch.R` resolve unchanged)
 *   - The placeholder WearApp() scaffold (Button A/B/C with TODO callbacks) is
 *     replaced by SOURCE's MainWatchScreen ↔ SosScreen navigation pair, the same
 *     model SOURCE's own MainActivity uses (see SOURCE com.anchor.watch.MainActivity).
 *   - FallDetectionService is started on launch (SOURCE behaviour — fall
 *     monitoring runs as long as the launcher activity has been opened once).
 *   - AmbientModeSupport from SOURCE is omitted here (depends on
 *     androidx.wear:wear FragmentActivity); partner's ComponentActivity host stays
 *     intact. Ambient mode can be added back later via androidx.wear.ambient API.
 */

package com.anchor.anchorwatchapp.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.anchor.watch.services.MedicationScheduler
import com.anchor.watch.services.MedicationSyncWorker
import com.anchor.watch.services.WatchFcmService
import com.anchor.watch.utils.LanguagePreference
import com.anchor.watch.utils.LocaleHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

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

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        FallDetectionService.start(applicationContext)
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
