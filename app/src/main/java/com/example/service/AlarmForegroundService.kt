package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.receiver.AlarmReceiver
import com.example.ui.ringing.AlarmRingingActivity
import com.example.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmForegroundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var volumeRampJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private var currentNormalVolume: Float = 1.0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        isChallengeMuted = false
        createNotificationChannel()

        // Acquire WakeLock to keep CPU running during alarm ringing
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlarmClock:ForegroundServiceWakeLock"
        )?.apply {
            acquire(15 * 60 * 1000L) // 15 minutes safety limit
        }

        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == ACTION_MUTE_CHALLENGE) {
            applyMutedState(true)
            return START_STICKY
        }
        if (action == ACTION_UNMUTE_CHALLENGE) {
            applyMutedState(false)
            return START_STICKY
        }

        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L)
        val hour = intent.getIntExtra(AlarmReceiver.EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(AlarmReceiver.EXTRA_MINUTE, 0)
        val repeatType = intent.getStringExtra(AlarmReceiver.EXTRA_REPEAT_TYPE) ?: "DAILY"
        val gradualVolume = intent.getBooleanExtra(AlarmReceiver.EXTRA_GRADUAL_VOLUME, false)
        val flipToStop = intent.getBooleanExtra(AlarmReceiver.EXTRA_FLIP_TO_STOP, false)
        val mathPuzzle = intent.getBooleanExtra(AlarmReceiver.EXTRA_MATH_PUZZLE, false)
        val enterKey = intent.getBooleanExtra(AlarmReceiver.EXTRA_ENTER_KEY, false)
        val vibrate = intent.getBooleanExtra(AlarmReceiver.EXTRA_VIBRATE, true)
        val ringtoneUri = intent.getStringExtra(AlarmReceiver.EXTRA_RINGTONE_URI) ?: "system_default"
        val ringtoneTitle = intent.getStringExtra(AlarmReceiver.EXTRA_RINGTONE_TITLE) ?: "Alarm"

        // Build Full-screen Intent notification
        val notification = buildAlarmNotification(
            alarmId = alarmId,
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

        // Start Foreground Service with mediaPlayback type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Directly launch full screen ringing activity immediately
        try {
            val fullScreenActivityIntent = Intent(this, AlarmRingingActivity::class.java).apply {
                setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
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
            }
            startActivity(fullScreenActivityIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error directly starting full screen activity from service", e)
        }

        // Start playing ringtone audio and vibration if enabled
        startAudioAndVibration(ringtoneUri, gradualVolume, vibrate)

        return START_STICKY
    }

    private fun startAudioAndVibration(ringtoneUriString: String, gradualVolume: Boolean, vibrate: Boolean) {
        stopAudio()

        try {
            val audioUri = if (ringtoneUriString == "system_default" || ringtoneUriString.isEmpty()) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } else {
                Uri.parse(ringtoneUriString)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, audioUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                val initialVol = if (gradualVolume) 0.05f else 1.0f
                currentNormalVolume = initialVol
                if (isChallengeMuted) {
                    setVolume(0f, 0f)
                } else {
                    setVolume(initialVol, initialVol)
                }
                prepare()
                start()
            }

            // Gradual volume ramp up over 30 seconds if enabled
            if (gradualVolume) {
                volumeRampJob?.cancel()
                volumeRampJob = serviceScope.launch {
                    val steps = 30
                    for (i in 1..steps) {
                        if (!isActive) break
                        delay(1000)
                        val vol = (i.toFloat() / steps).coerceIn(0.05f, 1.0f)
                        currentNormalVolume = vol
                        try {
                            if (!isChallengeMuted) {
                                mediaPlayer?.setVolume(vol, vol)
                            }
                        } catch (e: Exception) {
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio ringtone, fallback to system beep", e)
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer.create(applicationContext, fallbackUri)?.apply {
                    isLooping = true
                    start()
                }
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback audio failed", fallbackEx)
            }
        }

        // Start repeating vibration pattern only if vibrate toggle is ON
        if (vibrate) {
            try {
                val pattern = longArrayOf(0, 600, 400, 600, 400)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(pattern, 0)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering vibrator", e)
            }
        }
    }

    private fun buildAlarmNotification(
        alarmId: Long,
        hour: Int,
        minute: Int,
        repeatType: String,
        gradualVolume: Boolean,
        flipToStop: Boolean,
        mathPuzzle: Boolean,
        enterKey: Boolean,
        vibrate: Boolean,
        ringtoneUri: String,
        ringtoneTitle: String
    ): Notification {
        val (formattedTime, amPm) = TimeFormatter.formatClockTime(hour, minute)

        // Fullscreen Intent Activity for waking the lock screen
        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
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
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action intent
        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_REPEAT_TYPE, repeatType)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            (alarmId + 1000).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze 5 min action intent
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE_ALARM
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
            putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, 5)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            (alarmId + 2000).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Alarm $formattedTime $amPm")
            .setContentText("Ringing now • Tap to solve riddle / stop")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Open Full Screen", fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Snooze 5m", snoozePendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Trigger Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notification channel for triggering unmissable alarms"
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Audio is handled by our MediaPlayer
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopAudio() {
        volumeRampJob?.cancel()
        volumeRampJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing media player", e)
        }
        mediaPlayer = null
    }

    private fun stopAlarm() {
        stopAudio()
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling vibrator", e)
        }
    }

    fun applyMutedState(muted: Boolean) {
        isChallengeMuted = muted
        try {
            if (muted) {
                mediaPlayer?.setVolume(0f, 0f)
                Log.d(TAG, "Audio muted for unlock challenge (volume set to 0)")
            } else {
                mediaPlayer?.setVolume(currentNormalVolume, currentNormalVolume)
                Log.d(TAG, "Audio unmuted: restored to $currentNormalVolume")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying muted state", e)
        }
    }

    override fun onDestroy() {
        if (activeInstance == this) {
            activeInstance = null
        }
        stopAlarm()
        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock", e)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmForegroundService"
        const val CHANNEL_ID = "channel_alarm_unmissable"
        const val NOTIFICATION_ID = 4099

        const val ACTION_START = "com.example.alarm.ACTION_START"
        const val ACTION_STOP = "com.example.alarm.ACTION_STOP"
        const val ACTION_MUTE_CHALLENGE = "com.example.alarm.ACTION_MUTE_CHALLENGE"
        const val ACTION_UNMUTE_CHALLENGE = "com.example.alarm.ACTION_UNMUTE_CHALLENGE"

        private var activeInstance: AlarmForegroundService? = null
        var isChallengeMuted: Boolean = false
            private set

        fun setChallengeMuted(muted: Boolean) {
            isChallengeMuted = muted
            activeInstance?.applyMutedState(muted)
        }
    }
}
