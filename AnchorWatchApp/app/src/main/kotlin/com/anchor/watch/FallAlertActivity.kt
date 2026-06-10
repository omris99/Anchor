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
import com.anchor.watch.screens.FallAlertScreen
import com.anchor.watch.services.FallDetectionService
import com.anchor.watch.utils.LocaleHelper

class FallAlertActivity : ComponentActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // Read the controller that the service already started. If null, the service
        // either already handled the emergency or was killed — nothing to show.
        val controller = FallDetectionService.activeController
        if (controller == null) {
            Log.w(TAG, "activeController is null — alert already handled, finishing")
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        val layoutDirection = LocaleHelper.layoutDirection(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                FallAlertScreen(
                    controller = controller,
                    // Countdown and SOS dispatch are owned by the service — onFinished
                    // only needs to close the UI. Cleanup is done by controller callbacks.
                    onFinished = {
                        Log.d(TAG, "onFinished — closing fall alert UI")
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val TAG = "FallAlertActivity"
    }
}
