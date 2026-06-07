package com.anchor.watch.services

import android.content.Context
import com.anchor.watch.data.MedicationRepository
import com.anchor.watch.data.local.MedicationLocalStore
import com.anchor.watch.data.local.MedicationStatus
import com.anchor.watch.network.PartnerApi
import com.anchor.watch.network.WatchKeyStore
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pulls today's reminders from the backend (via [MedicationRepository.today], which
 * also caches them into Room) and (re)schedules an exact alarm for each one.
 *
 * This is the missing pull-then-schedule spine: before this, [MedicationRepository.today]
 * was never called in production and [SosReceiver] only re-armed whatever happened to
 * already be in the local Room store — so a dashboard-created reminder never fired.
 *
 * The core [run] takes its collaborators as plain lambdas so it is unit-testable with a
 * fake repository, a fixed clock and a capturing [scheduleAlarm]; [syncAndReschedule]
 * wires the real Room store, Retrofit API and [android.app.AlarmManager].
 */
class MedicationScheduler(
    private val repository: MedicationRepository,
    private val scheduleAlarm: (medicationId: String, triggerAtMillis: Long) -> Unit,
    private val cancelAlarm: (medicationId: String) -> Unit = {},
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
        for (med in reminders) {
            // Skip reminders already resolved for this fire; only PENDING needs an alarm.
            if (med.status != MedicationStatus.PENDING) continue
            val time = parseTime(med.scheduledTime) ?: continue
            val triggerMillis = MedicationAlarmService.nextTriggerMillis(
                now = current,
                scheduledTime = time,
                allowedDays = med.daysOfWeek.toSet(),
            )
            scheduleAlarm(med.id, triggerMillis)
            repository.scheduleAck(med.id)
        }
    }

    private fun parseTime(value: String): LocalTime? =
        runCatching { LocalTime.parse(value) }.getOrNull()

    companion object {
        /**
         * Production entry point: pull reminders for the paired user and reschedule alarms.
         * Safe to call from a coroutine (App launch, boot, periodic worker). Builds the
         * repository with the persisted user id so the GET path is correct.
         */
        suspend fun syncAndReschedule(context: Context) {
            val appContext = context.applicationContext
            val store = MedicationLocalStore(appContext)
            val keyStore = WatchKeyStore.get(appContext)
            val repository = MedicationRepository(
                store = store,
                api = PartnerApi.medication(appContext),
                onQueueForRetry = { MedicationSyncWorker.enqueue(appContext) },
                userIdProvider = { runBlocking { keyStore.userId() } ?: "self" },
            )
            MedicationScheduler(
                repository = repository,
                scheduleAlarm = { id, triggerMillis ->
                    MedicationAlarmService.schedule(appContext, id, triggerMillis)
                },
                cancelAlarm = { id ->
                    MedicationAlarmService.cancel(appContext, id)
                },
            ).run()
        }
    }
}
