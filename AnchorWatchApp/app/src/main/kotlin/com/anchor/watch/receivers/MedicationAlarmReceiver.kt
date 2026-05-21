package com.anchor.watch.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.anchor.watch.services.MedicationAlarmService

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MedicationAlarmService.ACTION_FIRE) return
        val medicationId = intent.getStringExtra(MedicationAlarmService.EXTRA_MED_ID) ?: return

        val serviceIntent = Intent(context, MedicationAlarmService::class.java).apply {
            action = MedicationAlarmService.ACTION_FIRE
            putExtra(MedicationAlarmService.EXTRA_MED_ID, medicationId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
