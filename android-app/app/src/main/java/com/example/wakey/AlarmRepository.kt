package com.example.wakey

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONArray
import java.util.Calendar

object AlarmRepository {

    private const val PREFS_NAME = "WakeyAlarmsStorage"
    private const val KEY_ALARMS = "alarms_json_list"
    private const val TAG = "AlarmRepository"

    fun getAlarms(context: Context): MutableList<AlarmModel> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ALARMS, null)

        if (jsonStr.isNullOrEmpty()) {
            // Seed default alarms
            val defaultList = mutableListOf(
                AlarmModel(
                    hour = 6,
                    minute = 30,
                    label = "Morning Routine",
                    isEnabled = true,
                    days = mutableSetOf(
                        Calendar.MONDAY,
                        Calendar.TUESDAY,
                        Calendar.WEDNESDAY,
                        Calendar.THURSDAY,
                        Calendar.FRIDAY
                    ),
                    mission = "PC Desktop Verification",
                    sound = "Heavy Alarm"
                ),
                AlarmModel(
                    hour = 7,
                    minute = 15,
                    label = "Work Kickoff",
                    isEnabled = false,
                    days = mutableSetOf(
                        Calendar.MONDAY,
                        Calendar.TUESDAY,
                        Calendar.WEDNESDAY,
                        Calendar.THURSDAY,
                        Calendar.FRIDAY
                    ),
                    mission = "PC Desktop Verification",
                    sound = "Heavy Alarm"
                ),
                AlarmModel(
                    hour = 18,
                    minute = 0,
                    label = "Evening Study / Gym",
                    isEnabled = false,
                    days = mutableSetOf(
                        Calendar.MONDAY,
                        Calendar.WEDNESDAY,
                        Calendar.FRIDAY
                    ),
                    mission = "PC Desktop Verification",
                    sound = "Heavy Alarm"
                )
            )
            saveAlarms(context, defaultList)
            return defaultList
        }

        val list = mutableListOf<AlarmModel>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                list.add(AlarmModel.fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing saved alarms JSON", e)
        }
        return list
    }

    fun saveAlarms(context: Context, alarms: List<AlarmModel>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (alarm in alarms) {
            jsonArray.put(alarm.toJson())
        }
        prefs.edit().putString(KEY_ALARMS, jsonArray.toString()).apply()
    }

    fun addOrUpdateAlarm(context: Context, alarm: AlarmModel) {
        val alarms = getAlarms(context)
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index != -1) {
            alarms[index] = alarm
        } else {
            alarms.add(0, alarm)
        }
        saveAlarms(context, alarms)

        if (alarm.isEnabled) {
            scheduleAlarm(context, alarm)
        } else {
            cancelAlarm(context, alarm.id)
        }
    }

    fun deleteAlarm(context: Context, alarmId: String) {
        cancelAlarm(context, alarmId)
        val alarms = getAlarms(context)
        alarms.removeAll { it.id == alarmId }
        saveAlarms(context, alarms)
    }

    fun toggleAlarm(context: Context, alarmId: String, isEnabled: Boolean) {
        val alarms = getAlarms(context)
        val alarm = alarms.find { it.id == alarmId } ?: return
        alarm.isEnabled = isEnabled
        saveAlarms(context, alarms)

        if (isEnabled) {
            scheduleAlarm(context, alarm)
        } else {
            cancelAlarm(context, alarmId)
        }
    }

    fun scheduleAlarm(context: Context, alarm: AlarmModel) {
        if (!alarm.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMillis = alarm.getNextTriggerMillis()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.wakey.START_ALARM"
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_LABEL", alarm.label)
        }

        val requestCode = alarm.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled alarm '${alarm.label}' for epoch millis: $triggerMillis")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm exact wakeup", e)
        }
    }

    fun cancelAlarm(context: Context, alarmId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.wakey.START_ALARM"
        }
        val requestCode = alarmId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm ID: $alarmId")
        }
    }

    fun scheduleAllActiveAlarms(context: Context) {
        val alarms = getAlarms(context)
        Log.d(TAG, "Rescheduling all active alarms on boot/sync (${alarms.size} total)")
        for (alarm in alarms) {
            if (alarm.isEnabled) {
                scheduleAlarm(context, alarm)
            }
        }
    }

    fun onAlarmTriggered(context: Context, alarmId: String?) {
        if (alarmId == null) return
        val alarms = getAlarms(context)
        val alarm = alarms.find { it.id == alarmId } ?: return

        if (alarm.days.isEmpty()) {
            // One-time alarm
            alarm.isEnabled = false
            saveAlarms(context, alarms)
        } else {
            // Recurring alarm: immediately compute and schedule the NEXT occurrence
            scheduleAlarm(context, alarm)
        }
    }

    fun getNextUpcomingAlarm(context: Context): AlarmModel? {
        val alarms = getAlarms(context).filter { it.isEnabled }
        if (alarms.isEmpty()) return null

        return alarms.minByOrNull { it.getNextTriggerMillis() }
    }
}
