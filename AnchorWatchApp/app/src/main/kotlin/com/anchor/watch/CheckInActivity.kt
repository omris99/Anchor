package com.anchor.watch

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.anchor.watch.data.CheckInRepository
import com.anchor.watch.data.local.CheckInLocalStore
import com.anchor.watch.network.PartnerApi
import com.anchor.watch.screens.DailyCheckInScreen
import com.anchor.watch.services.CheckInSyncWorker
import com.anchor.watch.utils.LocaleHelper

class CheckInActivity : ComponentActivity() {

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

        val repository = CheckInRepository(
            store = CheckInLocalStore(applicationContext),
            // PartnerApiAdapter: was UnreachableCheckInApi (SOURCE default stub).
            api = PartnerApi.checkIn(applicationContext),
            onQueueForRetry = { CheckInSyncWorker.enqueue(applicationContext) },
        )

        val layoutDirection = LocaleHelper.layoutDirection(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                DailyCheckInScreen(
                    repository = repository,
                    onFinished = { finish() },
                )
            }
        }
    }
}
