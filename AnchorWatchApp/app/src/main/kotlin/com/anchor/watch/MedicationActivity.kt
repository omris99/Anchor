package com.anchor.watch

import android.content.Context
import android.os.Build
import android.os.Bundle
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
                    onConfirm = {
                        MedicationAlarmService.sendConfirm(applicationContext, medicationId)
                        finish()
                    },
                    onFinished = { finish() },
                )
            }
        }
    }
}
