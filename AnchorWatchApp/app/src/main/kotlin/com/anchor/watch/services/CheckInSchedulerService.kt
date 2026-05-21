package com.anchor.watch.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.anchor.watch.receivers.CheckInAlarmReceiver
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CheckInSchedulerService(private val context: Context) {

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun schedule(timeOfDay: LocalTime, now: LocalDateTime = LocalDateTime.now()) {
        val target = nextTrigger(now, timeOfDay)
        val triggerMillis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val pi = pendingIntent()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, pi), pi)

        prefs.edit()
            .putInt(KEY_HOUR, timeOfDay.hour)
            .putInt(KEY_MINUTE, timeOfDay.minute)
            .putLong(KEY_NEXT, triggerMillis)
            .apply()
    }

    fun cancel() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent())
        prefs.edit().remove(KEY_NEXT).apply()
    }

    fun rescheduleIfConfigured(now: LocalDateTime = LocalDateTime.now()) {
        val hour = prefs.getInt(KEY_HOUR, -1)
        val minute = prefs.getInt(KEY_MINUTE, -1)
        if (hour !in 0..23 || minute !in 0..59) return
        schedule(LocalTime.of(hour, minute), now)
    }

    fun configuredTime(): LocalTime? {
        val hour = prefs.getInt(KEY_HOUR, -1)
        val minute = prefs.getInt(KEY_MINUTE, -1)
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    fun nextFireMillis(): Long = prefs.getLong(KEY_NEXT, 0L)

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, CheckInAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val PREFS_NAME = "anchor_checkin"
        const val KEY_HOUR = "scheduled_hour"
        const val KEY_MINUTE = "scheduled_minute"
        const val KEY_NEXT = "next_fire_millis"
        const val ACTION_FIRE = "com.anchor.watch.action.CHECKIN_FIRE"
        const val REQUEST_CODE = 4201

        fun nextTrigger(now: LocalDateTime, time: LocalTime): LocalDateTime {
            val today = now.toLocalDate().atTime(time)
            return if (today.isAfter(now)) today else today.plusDays(1)
        }
    }
}
