package com.anchor.watch.services

import android.content.Context
import android.util.Log
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import com.anchor.watch.network.PartnerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// Health Services manages the lifecycle of this service — the OS wakes it up
// to deliver batched data, then destroys it. SensorManager would be useless here
// since listeners only live while the service is alive between HR deliveries.
// Both HR and steps are requested from Health Services so they arrive together.
class HealthMetricsService : PassiveListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var latestHeartRate: Int? = null
    private var latestSteps: Int? = null

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val hrPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
        val heartRate = hrPoints.lastOrNull()?.value?.toInt()
        if (heartRate != null) latestHeartRate = heartRate

        // STEPS_DAILY: DeltaDataType<Long, IntervalDataPoint<Long>> — confirmed in JAR
        val stepPoints = dataPoints.getData(DataType.STEPS_DAILY)
        val steps = stepPoints.lastOrNull()?.value?.toInt()
        if (steps != null) latestSteps = steps

        Log.d(TAG, "Data received — heartRate=$heartRate, steps=$steps")

        if (heartRate != null || steps != null) {
            postMetrics()
        }
    }

    private fun postMetrics() {
        val hr = latestHeartRate
        val steps = latestSteps
        scope.launch {
            val ok = runCatching {
                PartnerApi.healthMetrics(applicationContext).post(hr, steps)
            }.onFailure { e ->
                Log.e(TAG, "Upload failed", e)
            }.getOrDefault(false)
            Log.d(TAG, "Upload result: $ok (heartRate=$hr, steps=$steps)")
        }
    }

    override fun onDestroy() {
        // Do NOT cancel the scope here — the OS destroys the service immediately after
        // onNewDataPointsReceived returns, while the network call is still in flight.
        // The coroutine only holds applicationContext (which outlives the service), so
        // letting it finish naturally is safe and avoids JobCancellationException.
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HealthMetricsService"

        suspend fun register(context: Context) {
            try {
                val client = HealthServices.getClient(context).passiveMonitoringClient
                val capabilities = client.getCapabilitiesAsync().await()
                val supported = capabilities.supportedDataTypesPassiveMonitoring
                Log.d(TAG, "Supported passive types: $supported")

                val requested = buildSet {
                    if (DataType.HEART_RATE_BPM in supported) add(DataType.HEART_RATE_BPM)
                    if (DataType.STEPS_DAILY in supported) add(DataType.STEPS_DAILY)
                }

                if (requested.isEmpty()) {
                    Log.w(TAG, "No supported data types — skipping registration")
                    return
                }

                val config = PassiveListenerConfig.builder()
                    .setDataTypes(requested)
                    .build()

                client.setPassiveListenerServiceAsync(HealthMetricsService::class.java, config).await()
                Log.d(TAG, "Passive listener registered for: $requested")
            } catch (e: Exception) {
                Log.e(TAG, "Registration failed", e)
            }
        }
    }
}
