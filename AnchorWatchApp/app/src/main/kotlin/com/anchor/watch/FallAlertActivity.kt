package com.anchor.watch

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.anchor.watch.screens.FallAlertScreen
import com.anchor.watch.services.EmergencyService
import com.anchor.watch.services.FallDetectionService
import com.anchor.watch.utils.FallAlertController
import com.anchor.watch.utils.FallDetectionConstants
import com.anchor.watch.utils.LocaleHelper

class FallAlertActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        val controller = FallAlertController(
            graceMs = FallDetectionConstants.GRACE_PERIOD_MS,
            onTrigger = {
                EmergencyService.start(applicationContext, 1)
            },
            onCancel = {
                Log.i(TAG, "false_positive_cancelled at ${System.currentTimeMillis()}")
            },
        )

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FallAlertScreen(
                    controller = controller,
                    onFinished = {
                        FallDetectionService.acknowledge(applicationContext)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val TAG = "FallDetection"
    }
}
