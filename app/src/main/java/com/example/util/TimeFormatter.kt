package com.example.util

import com.example.data.AlarmEntity
import java.util.Calendar
import java.util.Locale

object TimeFormatter {

    /**
     * Formats 24-hour hour and minute to 12-hour clock string, e.g. "5:10", "12:00".
     */
    fun formatClockTime(hour: Int, minute: Int): Pair<String, String> {
        val amPm = if (hour < 12) "AM" else "PM"
        val hour12 = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        val formattedTime = String.format(Locale.getDefault(), "%d:%02d", hour12, minute)
        return Pair(formattedTime, amPm)
    }

    /**
     * Calculates milliseconds until the next alarm trigger relative to given nowMillis.
     */
    fun calculateNextTriggerMillis(hour: Int, minute: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the target time for today has already passed, schedule for tomorrow
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    /**
     * Formats remaining time human-readably, e.g. "Ring in 1 hour and 23 minutes".
     * Dynamically updates based on nowMillis.
     */
    fun formatRemainingTime(hour: Int, minute: Int, nowMillis: Long = System.currentTimeMillis()): String {
        val targetMillis = calculateNextTriggerMillis(hour, minute, nowMillis)
        val diffMillis = targetMillis - nowMillis

        val totalMinutes = (diffMillis / (1000 * 60)).coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 && minutes > 0 -> {
                val hourUnit = if (hours == 1L) "hour" else "hours"
                val minUnit = if (minutes == 1L) "minute" else "minutes"
                "Ring in $hours $hourUnit and $minutes $minUnit"
            }
            hours > 0 -> {
                val hourUnit = if (hours == 1L) "hour" else "hours"
                "Ring in $hours $hourUnit"
            }
            minutes > 0 -> {
                val minUnit = if (minutes == 1L) "minute" else "minutes"
                "Ring in $minutes $minUnit"
            }
            else -> "Ring in less than a minute"
        }
    }

    /**
     * Full subtitle status for alarm list item.
     * e.g., "Every day. Ring in 1 hour and 23 minutes" or "Once. Ring in 8 hours" or "Off".
     */
    fun formatAlarmStatus(alarm: AlarmEntity, nowMillis: Long = System.currentTimeMillis()): String {
        val repeatLabel = if (alarm.repeatType == AlarmEntity.REPEAT_DAILY) "Every day" else "Once"
        if (!alarm.isEnabled) {
            return "$repeatLabel · Off"
        }
        val remaining = formatRemainingTime(alarm.hour, alarm.minute, nowMillis)
        return "$repeatLabel. $remaining"
    }
}
