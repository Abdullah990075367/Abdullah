package com.example.util

import android.content.Context
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

data class RingtoneItem(
    val title: String,
    val uriString: String,
    val isCustom: Boolean = false
)

object RingtoneHelper {
    private const val TAG = "RingtoneHelper"

    /**
     * Returns a list of system alarm ringtones available on the device,
     * including default alarm and presets.
     */
    fun getAvailableSystemRingtones(context: Context): List<RingtoneItem> {
        val list = mutableListOf<RingtoneItem>()

        val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        list.add(
            RingtoneItem(
                title = "Default System Alarm",
                uriString = defaultAlarmUri?.toString() ?: "system_default",
                isCustom = false
            )
        )

        try {
            val ringtoneManager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM)
            }
            val cursor: Cursor? = ringtoneManager.cursor
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    val uri = ringtoneManager.getRingtoneUri(cursor.position)
                    if (uri != null && uri.toString() != defaultAlarmUri?.toString()) {
                        list.add(RingtoneItem(title = title, uriString = uri.toString()))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load system ringtones", e)
        }

        // Add standard system fallbacks if device cursor has few
        if (list.size <= 1) {
            val notifUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (notifUri != null) {
                list.add(RingtoneItem(title = "Notification Tone", uriString = notifUri.toString()))
            }
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (ringtoneUri != null) {
                list.add(RingtoneItem(title = "Phone Ringtone", uriString = ringtoneUri.toString()))
            }
        }

        return list
    }

    /**
     * Resolves human-readable file name from a content Uri (e.g. from Storage picker).
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "Custom Sound"
        try {
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = it.getString(nameIndex) ?: name
                        }
                    }
                }
            } else if (uri.path != null) {
                val cut = uri.path?.lastIndexOf('/') ?: -1
                if (cut != -1) {
                    name = uri.path!!.substring(cut + 1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving audio filename", e)
        }
        return name
    }
}
