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
import com.anchor.watch.data.local.WaterLocalStore
import com.anchor.watch.screens.WaterReminderScreen
import com.anchor.watch.services.WaterAlarmService
import com.anchor.watch.utils.LocaleHelper

class WaterActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AnchorWaterDebug"
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

        val waterReminderId = intent.getStringExtra(WaterAlarmService.EXTRA_WATER_ID)
        Log.d(TAG, "waterReminderId=$waterReminderId  isKeyguardLocked=${(getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked}")
        if (waterReminderId == null) {
            Log.e(TAG, "waterReminderId is null — finishing")
            finish()
            return
        }

        val store = WaterLocalStore(applicationContext)

        val layoutDirection = LocaleHelper.layoutDirection(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                WaterReminderScreen(
                    store = store,
                    waterReminderId = waterReminderId,
                    // Fire the remote /confirm now; the screen shows a brief "✓ שתיתי"
                    // acknowledgment and then calls onFinished to close the activity.
                    onConfirm = {
                        WaterAlarmService.sendConfirm(applicationContext, waterReminderId)
                    },
                    onFinished = { finish() },
                )
            }
        }
    }
}
