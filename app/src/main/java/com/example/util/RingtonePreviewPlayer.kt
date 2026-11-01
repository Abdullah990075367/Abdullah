package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

/**
 * Handles previewing ringtones and custom audio tracks reliably in UI screens,
 * allowing users to listen before setting alarms.
 */
class RingtonePreviewPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null
    private var onFinishedCallback: (() -> Unit)? = null

    fun play(uriString: String, onFinished: () -> Unit = {}) {
        stop()
        onFinishedCallback = onFinished

        val audioUri = resolveUri(uriString)
        try {
            val player = MediaPlayer().apply {
                setDataSource(context, audioUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = false
                setOnCompletionListener {
                    stop()
                    onFinishedCallback?.invoke()
                    onFinishedCallback = null
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error: what=$what extra=$extra")
                    stop()
                    onFinishedCallback?.invoke()
                    onFinishedCallback = null
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer initialization failed, attempting fallback ringtone: ${e.message}")
            try {
                val ringtone = RingtoneManager.getRingtone(context, audioUri)
                if (ringtone != null) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    ringtone.play()
                    fallbackRingtone = ringtone
                } else {
                    onFinished()
                }
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback ringtone playback failed", fallbackEx)
                onFinished()
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }

        try {
            fallbackRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping fallback ringtone", e)
        } finally {
            fallbackRingtone = null
        }
    }

    fun release() {
        stop()
        onFinishedCallback = null
    }

    private fun resolveUri(uriString: String): Uri {
        return if (uriString == "system_default" || uriString.isEmpty()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: Uri.EMPTY
        } else {
            Uri.parse(uriString)
        }
    }

    companion object {
        private const val TAG = "RingtonePreviewPlayer"
    }
}
