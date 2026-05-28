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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import com.anchor.watch.LanguageSelectionActivity
import com.anchor.watch.screens.MainWatchScreen
import com.anchor.watch.screens.SosScreen
import com.anchor.watch.services.FallDetectionService
import com.anchor.watch.utils.LanguagePreference
import com.anchor.watch.utils.LocaleHelper

class MainActivity : ComponentActivity() {

    private sealed interface Screen {
        data object Main : Screen
        data object Sos : Screen
    }

    private var screen by mutableStateOf<Screen>(Screen.Main)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!LanguagePreference.isConfigured(this)) {
            val intent = Intent(this, LanguageSelectionActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK,
                )
            }
            startActivity(intent)
            finish()
            return
        }

        FallDetectionService.start(applicationContext)
        val layoutDirection = LocaleHelper.layoutDirection(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                when (screen) {
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
