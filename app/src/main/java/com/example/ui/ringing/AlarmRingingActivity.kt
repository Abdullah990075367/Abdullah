package com.example.ui.ringing

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.receiver.AlarmReceiver
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow

class AlarmRingingActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isFlipToStopEnabled = false
    private var alarmId: Long = -1L
    private var hour: Int = 0
    private var minute: Int = 0
    private var repeatType: String = "DAILY"
    private var gradualVolume: Boolean = false
    private var mathPuzzle: Boolean = false
    private var enterKey: Boolean = false
    private var ringtoneUri: String = "system_default"
    private var ringtoneTitle: String = "Alarm"

    val enterKeyPressedEvent = MutableStateFlow(0)
    val backPressedWarningEvent = MutableStateFlow(0)
    val flipDetectedEvent = MutableStateFlow(0)

    private fun setAudioMuted(muted: Boolean) {
        com.example.service.AlarmForegroundService.setChallengeMuted(muted)
        val action = if (muted) {
            com.example.service.AlarmForegroundService.ACTION_MUTE_CHALLENGE
        } else {
            com.example.service.AlarmForegroundService.ACTION_UNMUTE_CHALLENGE
        }
        try {
            startService(Intent(this, com.example.service.AlarmForegroundService::class.java).apply {
                this.action = action
            })
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Wake screen, turn screen on, and show over keyguard/lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        // Read intent extras
        alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L)
        hour = intent.getIntExtra(AlarmReceiver.EXTRA_HOUR, 0)
        minute = intent.getIntExtra(AlarmReceiver.EXTRA_MINUTE, 0)
        repeatType = intent.getStringExtra(AlarmReceiver.EXTRA_REPEAT_TYPE) ?: "DAILY"
        gradualVolume = intent.getBooleanExtra(AlarmReceiver.EXTRA_GRADUAL_VOLUME, false)
        isFlipToStopEnabled = intent.getBooleanExtra(AlarmReceiver.EXTRA_FLIP_TO_STOP, false)
        mathPuzzle = intent.getBooleanExtra(AlarmReceiver.EXTRA_MATH_PUZZLE, false)
        enterKey = intent.getBooleanExtra(AlarmReceiver.EXTRA_ENTER_KEY, false)
        ringtoneUri = intent.getStringExtra(AlarmReceiver.EXTRA_RINGTONE_URI) ?: "system_default"
        ringtoneTitle = intent.getStringExtra(AlarmReceiver.EXTRA_RINGTONE_TITLE) ?: "Alarm"

        // Initialize accelerometer for flip-to-stop if enabled
        if (isFlipToStopEnabled) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        setContent {
            MyApplicationTheme {
                AlarmRingingScreen(
                    hour = hour,
                    minute = minute,
                    ringtoneTitle = ringtoneTitle,
                    gradualVolume = gradualVolume,
                    flipToStop = isFlipToStopEnabled,
                    mathPuzzle = mathPuzzle,
                    enterKey = enterKey,
                    enterKeyPressedEvent = enterKeyPressedEvent,
                    backPressedWarningEvent = backPressedWarningEvent,
                    flipDetectedEvent = flipDetectedEvent,
                    onSetMute = { muted -> setAudioMuted(muted) },
                    onSnooze = { minutes ->
                        setAudioMuted(false)
                        handleSnooze(minutes)
                    },
                    onDismissSuccess = {
                        setAudioMuted(false)
                        handleDismiss()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFlipToStopEnabled && accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroy() {
        setAudioMuted(false)
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isFlipToStopEnabled) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val z = event.values[2]
            // When device is placed face down, z-axis gravity is strongly negative (< -7.5 m/s^2)
            if (z < -7.5f) {
                flipDetectedEvent.value += 1
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (enterKey && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            enterKeyPressedEvent.value += 1
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent backing out while alarm is ringing
        backPressedWarningEvent.value += 1
        Toast.makeText(
            this,
            "⚠️ Alarm is ringing! You must Snooze or Solve the riddle to stop the alarm.",
            Toast.LENGTH_LONG
        ).show()
    }

    private var isFinishedDueToAction = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        reassertAlarmScreen()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishedDueToAction) {
            reassertAlarmScreen()
        }
    }

    private fun reassertAlarmScreen() {
        if (!isFinishedDueToAction) {
            val bringBackIntent = Intent(this, AlarmRingingActivity::class.java).apply {
                setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            try {
                startActivity(bringBackIntent)
            } catch (_: Exception) {}
            Toast.makeText(
                this,
                "⚠️ Alarm is ringing! You must Snooze or Solve the riddle to stop the alarm.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleDismiss() {
        isFinishedDueToAction = true
        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_REPEAT_TYPE, repeatType)
        }
        sendBroadcast(dismissIntent)
        finishAndRemoveTask()
    }

    private fun handleSnooze(snoozeMinutes: Int) {
        isFinishedDueToAction = true
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_HOUR, hour)
            putExtra(AlarmReceiver.EXTRA_MINUTE, minute)
            putExtra(AlarmReceiver.EXTRA_REPEAT_TYPE, repeatType)
            putExtra(AlarmReceiver.EXTRA_GRADUAL_VOLUME, gradualVolume)
            putExtra(AlarmReceiver.EXTRA_FLIP_TO_STOP, isFlipToStopEnabled)
            putExtra(AlarmReceiver.EXTRA_MATH_PUZZLE, mathPuzzle)
            putExtra(AlarmReceiver.EXTRA_ENTER_KEY, enterKey)
            putExtra(AlarmReceiver.EXTRA_RINGTONE_URI, ringtoneUri)
            putExtra(AlarmReceiver.EXTRA_RINGTONE_TITLE, ringtoneTitle)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        sendBroadcast(snoozeIntent)
        finishAndRemoveTask()
    }
}
