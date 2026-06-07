package com.anchor.watch.services

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anchor.watch.CheckInActivity
import com.anchor.watch.network.PartnerApi
import com.anchor.watch.network.WatchKeyStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.fcmTokenDataStore by preferencesDataStore(name = "fcm_token_store")
private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")

class WatchFcmService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called when FCM assigns a new token (first install or token rotation).
     * Saves the token locally first, then tries to register with the backend.
     * If the watch isn't paired yet (no X-Watch-Key), the token will be
     * registered later via [registerSavedTokenIfPaired].
     */
    override fun onNewToken(token: String) {
        scope.launch {
            saveFcmTokenLocally(applicationContext, token)
            runCatching { PartnerApi.registerFcmToken(applicationContext, token) }
        }
    }

    /**
     * Called when a silent data-only FCM message arrives.
     * "medication_sync"  → pull latest reminders and reschedule alarms immediately.
     * "request_checkin"  → open CheckInActivity so the elder can respond.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "medication_sync" -> scope.launch {
                runCatching { MedicationScheduler.syncAndReschedule(applicationContext) }
            }
            "medication_delete" -> {
                val medId = message.data["med_id"] ?: return
                MedicationAlarmService.cancel(applicationContext, medId)
            }
            "request_checkin" -> {
                val intent = Intent(applicationContext, CheckInActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                applicationContext.startActivity(intent)
            }
        }
    }

    companion object {
        private suspend fun saveFcmTokenLocally(context: Context, token: String) {
            context.fcmTokenDataStore.edit { it[KEY_FCM_TOKEN] = token }
        }

        suspend fun getSavedToken(context: Context): String? =
            context.fcmTokenDataStore.data.first()[KEY_FCM_TOKEN]

        /**
         * Called after pairing completes. If a token was saved before pairing,
         * registers it with the backend now that X-Watch-Key is available.
         */
        suspend fun registerSavedTokenIfPaired(context: Context) {
            val token = getSavedToken(context) ?: return
            val hasKey = WatchKeyStore.get(context).apiKey() != null
            if (hasKey) {
                runCatching { PartnerApi.registerFcmToken(context, token) }
            }
        }
    }
}
