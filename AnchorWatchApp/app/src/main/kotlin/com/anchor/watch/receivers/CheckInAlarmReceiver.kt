package com.anchor.watch.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anchor.watch.CheckInActivity
import com.anchor.watch.services.CheckInSchedulerService

class CheckInAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CheckInSchedulerService.ACTION_FIRE) return

        val launch = Intent(context, CheckInActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
        }
        context.startActivity(launch)

        CheckInSchedulerService(context).rescheduleIfConfigured()
    }
}
