package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.AlarmDatabase
import com.example.data.AlarmEntity
import com.example.service.AlarmForegroundService
import com.example.ui.ringing.AlarmRingingActivity
import com.example.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "AlarmReceiver received action: $action")

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        val repeatType = intent.getStringExtra(EXTRA_REPEAT_TYPE) ?: AlarmEntity.REPEAT_DAILY
        val gradualVolume = intent.getBooleanExtra(EXTRA_GRADUAL_VOLUME, false)
        val flipToStop = intent.getBooleanExtra(EXTRA_FLIP_TO_STOP, false)
        val mathPuzzle = intent.getBooleanExtra(EXTRA_MATH_PUZZLE, false)
        val enterKey = intent.getBooleanExtra(EXTRA_ENTER_KEY, false)
        val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
        val powerOffAlarm = intent.getBooleanExtra(EXTRA_POWER_OFF_ALARM, true)
        val ringtoneUri = intent.getStringExtra(EXTRA_RINGTONE_URI) ?: AlarmEntity.DEFAULT_RINGTONE_URI
        val ringtoneTitle = intent.getStringExtra(EXTRA_RINGTONE_TITLE) ?: "Alarm"
        val isSnoozed = intent.getBooleanExtra(EXTRA_IS_SNOOZED, false)

        when (action) {
            ACTION_TRIGGER_ALARM -> {
                // Acquire screen-wake wake lock to physically turn the device display ON from sleep
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                try {
                    @Suppress("DEPRECATION")
                    val screenWakeLock = powerManager?.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                                PowerManager.ON_AFTER_RELEASE,
                        "AlarmClock:PhysicalScreenWakeUp"
                    )
                    screenWakeLock?.acquire(60 * 1000L) // Physically wake up screen for 60s
                } catch (_: Exception) {
                }

                // Also acquire partial wake lock for uninterrupted CPU operations
                val cpuWakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AlarmClock:CPUWakeLock"
                )
                cpuWakeLock?.acquire(60 * 1000L)

                // 1. Start the unmissable Foreground Service with mediaPlayback
                val serviceIntent = Intent(context, AlarmForegroundService::class.java).apply {
                    this.action = AlarmForegroundService.ACTION_START
                    putExtra(EXTRA_ALARM_ID, alarmId)
                    putExtra(EXTRA_HOUR, hour)
                    putExtra(EXTRA_MINUTE, minute)
                    putExtra(EXTRA_REPEAT_TYPE, repeatType)
                    putExtra(EXTRA_GRADUAL_VOLUME, gradualVolume)
                    putExtra(EXTRA_FLIP_TO_STOP, flipToStop)
                    putExtra(EXTRA_MATH_PUZZLE, mathPuzzle)
                    putExtra(EXTRA_ENTER_KEY, enterKey)
                    putExtra(EXTRA_VIBRATE, vibrate)
                    putExtra(EXTRA_RINGTONE_URI, ringtoneUri)
                    putExtra(EXTRA_RINGTONE_TITLE, ringtoneTitle)
                }
                ContextCompat.startForegroundService(context, serviceIntent)

                // 2. Launch Full-Screen Intent Activity directly
                val activityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    putExtra(EXTRA_ALARM_ID, alarmId)
                    putExtra(EXTRA_HOUR, hour)
                    putExtra(EXTRA_MINUTE, minute)
                    putExtra(EXTRA_REPEAT_TYPE, repeatType)
                    putExtra(EXTRA_GRADUAL_VOLUME, gradualVolume)
                    putExtra(EXTRA_FLIP_TO_STOP, flipToStop)
                    putExtra(EXTRA_MATH_PUZZLE, mathPuzzle)
                    putExtra(EXTRA_ENTER_KEY, enterKey)
                    putExtra(EXTRA_VIBRATE, vibrate)
                    putExtra(EXTRA_RINGTONE_URI, ringtoneUri)
                    putExtra(EXTRA_RINGTONE_TITLE, ringtoneTitle)
                }
                try {
                    context.startActivity(activityIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start AlarmRingingActivity directly", e)
                }
            }

            ACTION_DISMISS_ALARM -> {
                // Stop foreground service
                val stopIntent = Intent(context, AlarmForegroundService::class.java).apply {
                    this.action = AlarmForegroundService.ACTION_STOP
                }
                context.startService(stopIntent)

                // If alarm is "ONCE" and not snoozed, mark as disabled in Room DB
                if (alarmId > 0 && repeatType == AlarmEntity.REPEAT_ONCE && !isSnoozed) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AlarmDatabase.getInstance(context)
                        db.alarmDao().updateAlarmEnabled(alarmId, false)
                    }
                }
            }

            ACTION_SNOOZE_ALARM -> {
                val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)

                // Stop active ringing service
                val stopIntent = Intent(context, AlarmForegroundService::class.java).apply {
                    this.action = AlarmForegroundService.ACTION_STOP
                }
                context.startService(stopIntent)

                // Schedule snooze alarm
                AlarmScheduler.scheduleSnooze(
                    context = context,
                    alarmId = alarmId,
                    snoozeMinutes = snoozeMinutes,
                    hour = hour,
                    minute = minute,
                    repeatType = repeatType,
                    gradualVolume = gradualVolume,
                    flipToStop = flipToStop,
                    mathPuzzle = mathPuzzle,
                    enterKey = enterKey,
                    vibrate = vibrate,
                    ringtoneUri = ringtoneUri,
                    ringtoneTitle = ringtoneTitle
                )
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"

        const val ACTION_TRIGGER_ALARM = "com.example.alarm.ACTION_TRIGGER_ALARM"
        const val ACTION_DISMISS_ALARM = "com.example.alarm.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.alarm.ACTION_SNOOZE_ALARM"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
        const val EXTRA_REPEAT_TYPE = "extra_repeat_type"
        const val EXTRA_GRADUAL_VOLUME = "extra_gradual_volume"
        const val EXTRA_FLIP_TO_STOP = "extra_flip_to_stop"
        const val EXTRA_MATH_PUZZLE = "extra_math_puzzle"
        const val EXTRA_ENTER_KEY = "extra_enter_key"
        const val EXTRA_VIBRATE = "extra_vibrate"
        const val EXTRA_POWER_OFF_ALARM = "extra_power_off_alarm"
        const val EXTRA_RINGTONE_URI = "extra_ringtone_uri"
        const val EXTRA_RINGTONE_TITLE = "extra_ringtone_title"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_IS_SNOOZED = "extra_is_snoozed"
    }
}
