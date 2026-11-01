package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlarmDatabase
import com.example.data.AlarmEntity
import com.example.data.AlarmRepository
import com.example.util.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AlarmRepository
    val alarms: StateFlow<List<AlarmEntity>>

    init {
        val db = AlarmDatabase.getInstance(application)
        repository = AlarmRepository(db.alarmDao())
        alarms = repository.allAlarms.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    /**
     * Saves a new or edited alarm to Room and schedules it in AlarmManager.
     */
    fun saveAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            if (alarm.id == 0L) {
                val newId = repository.insertAlarm(alarm)
                val savedAlarm = alarm.copy(id = newId)
                if (savedAlarm.isEnabled) {
                    AlarmScheduler.scheduleAlarm(getApplication(), savedAlarm)
                }
            } else {
                repository.updateAlarm(alarm)
                if (alarm.isEnabled) {
                    AlarmScheduler.scheduleAlarm(getApplication(), alarm)
                } else {
                    AlarmScheduler.cancelAlarm(getApplication(), alarm.id)
                }
            }
        }
    }

    /**
     * Toggles alarm active state on/off.
     */
    fun toggleAlarm(alarm: AlarmEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.setAlarmEnabled(alarm.id, isEnabled)
            val updated = alarm.copy(isEnabled = isEnabled)
            if (isEnabled) {
                AlarmScheduler.scheduleAlarm(getApplication(), updated)
            } else {
                AlarmScheduler.cancelAlarm(getApplication(), alarm.id)
            }
        }
    }

    /**
     * Deletes an alarm and cancels its pending intent.
     */
    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancelAlarm(getApplication(), alarm.id)
            repository.deleteAlarm(alarm)
        }
    }

    /**
     * Schedules a quick test alarm (e.g. 5 seconds from now)
     * so user can see the unmissable alarm trigger.
     */
    fun scheduleTestAlarm() {
        viewModelScope.launch {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
            }
            val testAlarm = AlarmEntity(
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE),
                isEnabled = true,
                repeatType = AlarmEntity.REPEAT_ONCE,
                gradualVolume = true,
                flipToStop = true,
                mathPuzzle = false,
                enterKey = true,
                ringtoneTitle = "Test Alarm (Quick Trigger)"
            )
            val newId = repository.insertAlarm(testAlarm)
            AlarmScheduler.scheduleAlarm(getApplication(), testAlarm.copy(id = newId))
        }
    }
}
