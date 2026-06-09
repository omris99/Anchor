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
import android.util.Log
import com.anchor.watch.data.local.MedicationLocalStore
import com.anchor.watch.screens.MedicationReminderScreen
import com.anchor.watch.services.MedicationAlarmService
import com.anchor.watch.utils.LocaleHelper

class MedicationActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AnchorMedDebug"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate called — Activity IS being created")
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
        Log.d(TAG, "medicationId=$medicationId  isKeyguardLocked=${(getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked}")
        if (medicationId == null) {
            Log.e(TAG, "medicationId is null — finishing")
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
