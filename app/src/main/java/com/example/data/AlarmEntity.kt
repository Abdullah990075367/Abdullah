package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an Alarm.
 * Contains scheduling rules, ringtone source, and dismissal challenge settings.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val repeatType: String = REPEAT_DAILY, // "DAILY" or "ONCE"
    val gradualVolume: Boolean = false,
    val flipToStop: Boolean = false,
    val mathPuzzle: Boolean = false,
    val enterKey: Boolean = false,
    val vibrate: Boolean = true,
    val ringtoneUri: String = DEFAULT_RINGTONE_URI,
    val ringtoneTitle: String = "Default Alarm",
    val snoozeDurationMinutes: Int = 5,
    val powerOffAlarm: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val REPEAT_DAILY = "DAILY"
        const val REPEAT_ONCE = "ONCE"
        const val DEFAULT_RINGTONE_URI = "system_default"
    }

    val isDaily: Boolean
        get() = repeatType == REPEAT_DAILY
}
