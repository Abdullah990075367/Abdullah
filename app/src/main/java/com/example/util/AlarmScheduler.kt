package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.data.AlarmEntity
import com.example.receiver.AlarmReceiver

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    /**
     * Schedules an exact, unmissable alarm using AlarmManager.setAlarmClock.
     * AlarmClockInfo guarantees wakeup through Doze mode and shows an alarm icon in system status.
     */
    fun scheduleAlarm(context: Context, alarm: AlarmEntity) {
        if (!alarm.isEnabled) {
            cancelAlarm(context, alarm.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerMillis = TimeFormatter.calculateNextTriggerMillis(alarm.hour, alarm.minute)

        val triggerIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
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

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(TAG, "Scheduled AlarmClock for alarm ${alarm.id} at $triggerMillis")
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission denied, falling back to setAndAllowWhileIdle", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed fallback alarm scheduling", fallbackEx)
            }
        }

        // Hardware RTC / OEM Power-off Alarm Integration:
        // Many Qualcomm, MediaTek, and OEM chipsets (Xiaomi, Vivo, Oppo, Huawei) listen for
        // specific system broadcasts to program PMIC RTC power-on timers when phone is turned off.
        if (alarm.powerOffAlarm) {
            scheduleOemPowerOffAlarm(context, alarm.id, triggerMillis)
        }
    }

    /**
     * Schedules a Snooze alarm trigger after the specified minutes.
     */
    fun scheduleSnooze(
        context: Context,
        alarmId: Long,
        snoozeMinutes: Int,
        hour: Int,
        minute: Int,
        repeatType: String,
        gradualVolume: Boolean,
        flipToStop: Boolean,
        mathPuzzle: Boolean,
        enterKey: Boolean,
        vibrate: Boolean = true,
        ringtoneUri: String,
        ringtoneTitle: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        val triggerIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_HOUR, hour)
            putExtra(AlarmReceiver.EXTRA_MINUTE, minute)
            putExtra(AlarmReceiver.EXTRA_REPEAT_TYPE, repeatType)
            putExtra(AlarmReceiver.EXTRA_GRADUAL_VOLUME, gradualVolume)
            putExtra(AlarmReceiver.EXTRA_FLIP_TO_STOP, flipToStop)
            putExtra(AlarmReceiver.EXTRA_MATH_PUZZLE, mathPuzzle)
            putExtra(AlarmReceiver.EXTRA_ENTER_KEY, enterKey)
            putExtra(AlarmReceiver.EXTRA_VIBRATE, vibrate)
            putExtra(AlarmReceiver.EXTRA_RINGTONE_URI, ringtoneUri)
            putExtra(AlarmReceiver.EXTRA_RINGTONE_TITLE, ringtoneTitle)
            putExtra(AlarmReceiver.EXTRA_IS_SNOOZED, true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled snooze for $snoozeMinutes min (alarm $alarmId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling snooze", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
    }

    /**
     * Cancels an alarm pending intent.
     */
    fun cancelAlarm(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        cancelOemPowerOffAlarm(context, alarmId)
        Log.d(TAG, "Canceled alarm $alarmId")
    }

    /**
     * Broadcasts hardware RTC Power-Off Alarm intents recognized by Qualcomm and MediaTek
     * chipsets, and OEM frameworks (Xiaomi, Huawei, Vivo, Oppo) to power up the device automatically.
     */
    private fun scheduleOemPowerOffAlarm(context: Context, alarmId: Long, triggerMillis: Long) {
        try {
            // Qualcomm Snapdragon Power-Off Alarm broadcast
            val qcomIntent = Intent("org.codeaurora.poweroffalarm.action.SET_ALARM").apply {
                putExtra("time", triggerMillis)
                putExtra("package_name", context.packageName)
                putExtra("enable_power_off_alarm", true)
                putExtra("alarm_id", alarmId)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            context.sendBroadcast(qcomIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Qualcomm power-off alarm broadcast failed", e)
        }

        try {
            // MediaTek MTK Power-Off Alarm broadcast
            val mtkIntent = Intent("com.mediatek.intent.action.SET_ALARM_POWER_ON").apply {
                putExtra("time", triggerMillis)
                putExtra("alarm_id", alarmId)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            context.sendBroadcast(mtkIntent)
        } catch (e: Exception) {
            Log.w(TAG, "MediaTek power-off alarm broadcast failed", e)
        }
    }

    /**
     * Clears hardware RTC power-off alarm on OEM chipsets.
     */
    private fun cancelOemPowerOffAlarm(context: Context, alarmId: Long) {
        try {
            val qcomCancel = Intent("org.codeaurora.poweroffalarm.action.CANCEL_ALARM").apply {
                putExtra("alarm_id", alarmId)
            }
            context.sendBroadcast(qcomCancel)
        } catch (_: Exception) {}

        try {
            val mtkCancel = Intent("com.mediatek.intent.action.CANCEL_ALARM_POWER_ON").apply {
                putExtra("alarm_id", alarmId)
            }
            context.sendBroadcast(mtkCancel)
        } catch (_: Exception) {}
    }
}
