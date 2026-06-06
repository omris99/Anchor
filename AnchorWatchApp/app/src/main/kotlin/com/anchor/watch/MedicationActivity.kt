package com.anchor.watch

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.anchor.watch.data.local.MedicationLocalStore
import com.anchor.watch.screens.MedicationReminderScreen
import com.anchor.watch.services.MedicationAlarmService
import com.anchor.watch.utils.LocaleHelper

class MedicationActivity : ComponentActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be set before super.onCreate() so the window is created with these flags.
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // TODO: MedicationActivity still doesn't appear on screen after wakeup from sleep —
        //  the watch face stays visible instead. The wake lock (in MedicationAlarmService)
        //  successfully turns the screen on, and requestDismissKeyguard() is called here,
        //  but the Watch Face retains focus. Approaches tried and failed:
        //   - fullScreenIntent (requires USE_FULL_SCREEN_INTENT granted by user on API 34+)
        //   - FLAG_ACTIVITY_CLEAR_TASK (made things worse on Wear OS)
        //   - setSilent(true) on IMPORTANCE_HIGH notification (silently disables fullScreenIntent)
        //  Next things to try:
        //   - Post a second IMPORTANCE_HIGH + fullScreenIntent notification (separate from the
        //     foreground service notification) and guide the user to grant USE_FULL_SCREEN_INTENT
        //   - Check if KeyguardManager.isKeyguardLocked() is even true at this point
        //   - Use WearableActivityController / AmbientModeSupport to intercept the ambient→
        //     interactive transition and force focus before the Watch Face claims it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        val medicationId = intent.getStringExtra(MedicationAlarmService.EXTRA_MED_ID)
        if (medicationId == null) {
            finish()
            return
        }

        val store = MedicationLocalStore(applicationContext)

        val layoutDirection = LocaleHelper.layoutDirection(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MedicationReminderScreen(
                    store = store,
                    medicationId = medicationId,
                    // Fire the remote /confirm now; the screen shows a brief "✓ Taken"
                    // acknowledgment and then calls onFinished to close the activity.
                    onConfirm = {
                        MedicationAlarmService.sendConfirm(applicationContext, medicationId)
                    },
                    onFinished = { finish() },
                )
            }
        }
    }
}
