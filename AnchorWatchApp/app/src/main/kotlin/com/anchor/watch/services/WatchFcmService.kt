package com.anchor.watch.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anchor.anchorwatchapp.presentation.MainActivity
import com.anchor.watch.CheckInActivity
import com.anchor.watch.R
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
     * "water_sync"       → pull latest water reminder settings and reschedule alarms immediately.
     * "request_checkin"  → open CheckInActivity so the elder can respond.
     * "watch_unpair"     → clear the local X-Watch-Key and jump to the pairing screen.
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
            "water_sync" -> scope.launch {
                runCatching { WaterScheduler.syncAndReschedule(applicationContext) }
            }
            "request_checkin" -> {
                val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                Log.d(TAG, "request_checkin received  screenOn=${pm.isInteractive}")

                // Wake the screen immediately — same pattern as MedicationAlarmReceiver.
                // Firebase holds a CPU WakeLock during onMessageReceived(), but that
                // doesn't turn the screen on. ACQUIRE_CAUSES_WAKEUP does.
                @Suppress("DEPRECATION")
                pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "anchor:checkin_wakeup",
                ).acquire(10_000L)

                Log.d(TAG, "WakeLock acquired  screenOn=${pm.isInteractive}")

                val intent = Intent(applicationContext, CheckInActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pi = PendingIntent.getActivity(
                    applicationContext,
                    REQUEST_CHECKIN_ALERT,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                // Layer 1: direct startActivity() — succeeds when Firebase grants a BAL
                // window (high-priority FCM) and the process is already warm.
                runCatching { applicationContext.startActivity(intent) }
                    .onSuccess { Log.d(TAG, "startActivity(CheckInActivity): success") }
                    .onFailure { Log.e(TAG, "startActivity(CheckInActivity): FAILED — ${it::class.simpleName}: ${it.message}") }

                // Layer 2: AlarmManager.setAlarmClock() — not rate-limited and bypasses
                // BAL restrictions entirely. Fires immediately; reliable even on cold-start.
                val am = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(System.currentTimeMillis(), pi),
                    pi,
                )
                Log.d(TAG, "setAlarmClock (immediate) fired")

                // Layer 3: FullScreenIntent notification — visual fallback above lock screen.
                val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                ensureCheckinChannel(nm)
                val notif = NotificationCompat.Builder(applicationContext, CHECKIN_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(applicationContext.getString(R.string.checkin_notification_title))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(pi, true)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                nm.notify(CHECKIN_NOTIFICATION_ID, notif)
                Log.d(TAG, "fullScreenIntent notification posted")
            }
            "watch_unpair" -> scope.launch {
                // Clear the key first: MainActivity.onCreate() picks Screen.Pairing vs
                // Screen.Main purely from WatchKeyStore, so relaunching it below is
                // enough to land on the pairing screen — no separate live-state needed.
                WatchKeyStore.get(applicationContext).clear()

                val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "anchor:unpair_wakeup",
                ).acquire(10_000L)

                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                val pi = PendingIntent.getActivity(
                    applicationContext,
                    UNPAIR_ALERT,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                // Single trigger only — unlike CheckInActivity, MainActivity is NOT
                // singleInstance (it's the launcher), so a duplicate direct startActivity()
                // alongside this alarm would recreate it twice in a row, and each
                // recreation composes WatchPairingScreen fresh, which calls initPairing()
                // on mount — silently creating a second orphaned pairing row per unpair.
                // AlarmManager.setAlarmClock() alone bypasses BAL restrictions and is
                // reliable even on cold-start, so it doesn't need a direct-call companion.
                val am = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(System.currentTimeMillis(), pi),
                    pi,
                )

                // Visual fallback above lock screen — same PendingIntent, so tapping it
                // (or the OS auto-showing it while locked) doesn't add a distinct launch.
                val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                ensureUnpairChannel(nm)
                val notif = NotificationCompat.Builder(applicationContext, UNPAIR_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(applicationContext.getString(R.string.watch_unpaired_notification_title))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(pi, true)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
                nm.notify(UNPAIR_NOTIFICATION_ID, notif)
            }
        }
    }

    companion object {
        private const val TAG = "AnchorCheckInDebug"
        private const val CHECKIN_CHANNEL_ID = "anchor_checkin_alert"
        private const val CHECKIN_NOTIFICATION_ID = 920
        private const val REQUEST_CHECKIN_ALERT = 921
        private const val UNPAIR_CHANNEL_ID = "anchor_unpair_alert"
        private const val UNPAIR_NOTIFICATION_ID = 930
        private const val UNPAIR_ALERT = 931

        private fun ensureCheckinChannel(nm: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                nm.getNotificationChannel(CHECKIN_CHANNEL_ID) == null
            ) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHECKIN_CHANNEL_ID,
                        "Check-in alert",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    },
                )
            }
        }

        private fun ensureUnpairChannel(nm: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                nm.getNotificationChannel(UNPAIR_CHANNEL_ID) == null
            ) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        UNPAIR_CHANNEL_ID,
                        "Watch unpaired",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    },
                )
            }
        }

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
