package com.example.wakey

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

data class AlarmModel(
    val id: String = UUID.randomUUID().toString(),
    var hour: Int,
    var minute: Int,
    var label: String = "Morning Alarm",
    var isEnabled: Boolean = true,
    var days: MutableSet<Int> = mutableSetOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY
    ), // Calendar.SUNDAY(1) .. Calendar.SATURDAY(7)
    var mission: String = "PC Desktop Verification",
    var sound: String = "Heavy Alarm"
) {

    fun getFormattedTime(): String {
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        return String.format("%02d:%02d", h, minute)
    }

    fun getAmPm(): String {
        return if (hour >= 12) "pm" else "am"
    }

    fun get24HourTime(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    fun getDaysSummary(): String {
        if (days.isEmpty()) return "Once (Next occurrence)"
        if (days.size == 7) return "Every day"
        if (days.size == 5 &&
            days.contains(Calendar.MONDAY) &&
            days.contains(Calendar.TUESDAY) &&
            days.contains(Calendar.WEDNESDAY) &&
            days.contains(Calendar.THURSDAY) &&
            days.contains(Calendar.FRIDAY)
        ) {
            return "Weekdays"
        }
        if (days.size == 2 &&
            days.contains(Calendar.SATURDAY) &&
            days.contains(Calendar.SUNDAY)
        ) {
            return "Weekends"
        }

        val dayNames = listOf(
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat",
            Calendar.SUNDAY to "Sun"
        )

        return dayNames.filter { days.contains(it.first) }
            .joinToString(", ") { it.second }
    }

    /**
     * Calculates the next epoch timestamp (millis) when this alarm should fire.
     */
    fun getNextTriggerMillis(): Long {
        val now = Calendar.getInstance()

        // If no repeat days are selected, alarm triggers today or tomorrow
        if (days.isEmpty()) {
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now) || target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        // Search for the closest upcoming day matching one of the repeat days
        for (dayOffset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            if (days.contains(dayOfWeek)) {
                if (dayOffset > 0 || candidate.after(now)) {
                    return candidate.timeInMillis
                }
            }
        }

        // Fallback: 1 day later
        return now.timeInMillis + 86400000L
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("hour", hour)
        json.put("minute", minute)
        json.put("label", label)
        json.put("isEnabled", isEnabled)
        json.put("mission", mission)
        json.put("sound", sound)

        val daysArray = JSONArray()
        days.forEach { daysArray.put(it) }
        json.put("days", daysArray)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): AlarmModel {
            val daysSet = mutableSetOf<Int>()
            val daysArray = json.optJSONArray("days")
            if (daysArray != null) {
                for (i in 0 until daysArray.length()) {
                    daysSet.add(daysArray.getInt(i))
                }
            }
            return AlarmModel(
                id = json.optString("id", UUID.randomUUID().toString()),
                hour = json.optInt("hour", 6),
                minute = json.optInt("minute", 30),
                label = json.optString("label", "Morning Alarm"),
                isEnabled = json.optBoolean("isEnabled", true),
                days = daysSet,
                mission = json.optString("mission", "PC Desktop Verification"),
                sound = json.optString("sound", "Heavy Alarm")
            )
        }
    }
}
