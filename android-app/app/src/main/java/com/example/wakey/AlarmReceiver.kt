package com.example.wakey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("ALARM_ID")
        val alarmLabel = intent.getStringExtra("ALARM_LABEL") ?: "Wake Up!"
        Log.d("AlarmReceiver", "Alarm '$alarmLabel' (ID: $alarmId) fired! Starting AlarmRingerService.")

        // 1. Launch the foreground ringer service
        val serviceIntent = Intent(context, AlarmRingerService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", alarmLabel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 2. Reschedule recurring weekday alarm or toggle off one-time alarm
        AlarmRepository.onAlarmTriggered(context, alarmId)
    }
}
