package com.anchor.watch

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.lifecycleScope
import com.anchor.watch.data.CheckInContext
import com.anchor.watch.data.CheckInRepository
import com.anchor.watch.data.local.CheckInLocalStore
import com.anchor.watch.network.PartnerApi
import com.anchor.watch.screens.DailyCheckInScreen
import com.anchor.watch.services.CheckInSyncWorker
import com.anchor.watch.utils.LocaleHelper
import com.anchor.watch.utils.requestBestLocation
import kotlinx.coroutines.launch

class CheckInActivity : ComponentActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    // Updated in the background while the user sees the check-in screen.
    // contextProvider captures this var by reference, so submit() always reads the latest value.
    private var checkInContext = CheckInContext()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        // Fill battery immediately (instant), then fetch live location in the background.
        checkInContext = CheckInContext(batteryPercent = readBatteryPercent())
        lifecycleScope.launch {
            val location = requestBestLocation(this@CheckInActivity)
            checkInContext = checkInContext.copy(
                lat = location?.latitude,
                lng = location?.longitude,
            )
        }

        val repository = CheckInRepository(
            store = CheckInLocalStore(applicationContext),
            api = PartnerApi.checkIn(applicationContext),
            onQueueForRetry = { CheckInSyncWorker.enqueue(applicationContext) },
        )

        val layoutDirection = LocaleHelper.layoutDirection(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                DailyCheckInScreen(
                    repository = repository,
                    contextProvider = { checkInContext },
                    onFinished = { finish() },
                )
            }
        }
    }

    private fun readBatteryPercent(): Int? {
        val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager ?: return null
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level >= 0) level else null
    }
}
