package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AlarmDatabase
import com.example.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "BootReceiver received: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == ACTION_QUICKBOOT_POWERON ||
            action == ACTION_HTC_QUICKBOOT_POWERON ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            // Reschedule all active alarms from Room database
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AlarmDatabase.getInstance(context)
                    val activeAlarms = db.alarmDao().getEnabledAlarms()
                    Log.d(TAG, "Processing ${activeAlarms.size} active alarms after $action")
                    val now = System.currentTimeMillis()

                    for (alarm in activeAlarms) {
                        val alarmCalToday = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, alarm.hour)
                            set(java.util.Calendar.MINUTE, alarm.minute)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        val diffMillis = now - alarmCalToday.timeInMillis

                        // If alarm was scheduled for today and its trigger time elapsed while
                        // the phone was switched off (within the last 45 minutes), ring immediately!
                        if (diffMillis in 0..(45 * 60 * 1000L)) {
                            Log.d(TAG, "Alarm ${alarm.id} time elapsed during power-off. Ringing immediately!")
                            val triggerIntent = Intent(context, AlarmReceiver::class.java).apply {
                                this.action = AlarmReceiver.ACTION_TRIGGER_ALARM
                                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
                                putExtra(AlarmReceiver.EXTRA_HOUR, alarm.hour)
                                putExtra(AlarmReceiver.EXTRA_MINUTE, alarm.minute)
                                putExtra(AlarmReceiver.EXTRA_REPEAT_TYPE, alarm.repeatType)
                                putExtra(AlarmReceiver.EXTRA_GRADUAL_VOLUME, alarm.gradualVolume)
                                putExtra(AlarmReceiver.EXTRA_FLIP_TO_STOP, alarm.flipToStop)
                                putExtra(AlarmReceiver.EXTRA_MATH_PUZZLE, alarm.mathPuzzle)
                                putExtra(AlarmReceiver.EXTRA_ENTER_KEY, alarm.enterKey)
                                putExtra(AlarmReceiver.EXTRA_VIBRATE, alarm.vibrate)
                                putExtra(AlarmReceiver.EXTRA_POWER_OFF_ALARM, alarm.powerOffAlarm)
                                putExtra(AlarmReceiver.EXTRA_RINGTONE_URI, alarm.ringtoneUri)
                                putExtra(AlarmReceiver.EXTRA_RINGTONE_TITLE, alarm.ringtoneTitle)
                            }
                            context.sendBroadcast(triggerIntent)
                        } else {
                            AlarmScheduler.scheduleAlarm(context, alarm)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule alarms on boot", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
