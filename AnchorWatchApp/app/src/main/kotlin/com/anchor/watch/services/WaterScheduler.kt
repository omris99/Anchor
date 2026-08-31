package com.anchor.watch.services

import android.content.Context
import com.anchor.watch.data.WaterRepository
import com.anchor.watch.data.local.WaterLocalStore
import com.anchor.watch.data.local.WaterStatus
import com.anchor.watch.network.PartnerApi
import com.anchor.watch.network.WatchKeyStore
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pulls today's water reminders from the backend (via [WaterRepository.today], which
 * also caches them into Room) and (re)schedules an exact alarm for each one.
 *
 * Mirrors [MedicationScheduler] exactly — the dashboard already converts the
 * frequency+active-window settings into concrete `scheduled_time` items server-side
 * (water-reminders-dashboard PUT), so no frequency math happens on the watch.
 */
class WaterScheduler(
    private val repository: WaterRepository,
    private val scheduleAlarm: (waterReminderId: String, triggerAtMillis: Long) -> Unit,
    private val cancelAlarm: (waterReminderId: String) -> Unit = {},
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend fun run() {
        val localIds = repository.localIds()
        val reminders = repository.today()
        val remoteIds = reminders.map { it.id }.toSet()
        for (id in localIds - remoteIds) {
            cancelAlarm(id)
        }
        val current = now()
        for (water in reminders) {
            if (water.status != WaterStatus.PENDING) continue
            val time = parseTime(water.scheduledTime) ?: continue
            val triggerMillis = MedicationAlarmService.nextTriggerMillis(
                now = current,
                scheduledTime = time,
                allowedDays = water.daysOfWeek.toSet(),
            )
            scheduleAlarm(water.id, triggerMillis)
            repository.scheduleAck(water.id)
        }
    }

    private fun parseTime(value: String): LocalTime? =
        runCatching { LocalTime.parse(value) }.getOrNull()

    companion object {
        /**
         * Production entry point: pull water reminders for the paired user and reschedule
         * alarms. Safe to call from a coroutine (App launch, boot, periodic worker).
         */
        suspend fun syncAndReschedule(context: Context) {
            val appContext = context.applicationContext
            val store = WaterLocalStore(appContext)
            val keyStore = WatchKeyStore.get(appContext)
            val repository = WaterRepository(
                store = store,
                api = PartnerApi.water(appContext),
                onQueueForRetry = { MedicationSyncWorker.enqueue(appContext) },
                userIdProvider = { runBlocking { keyStore.userId() } ?: "self" },
            )
            WaterScheduler(
                repository = repository,
                scheduleAlarm = { id, triggerMillis ->
                    WaterAlarmService.schedule(appContext, id, triggerMillis)
                },
                cancelAlarm = { id ->
                    WaterAlarmService.cancel(appContext, id)
                },
            ).run()
        }
    }
}
