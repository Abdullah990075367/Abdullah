package com.example.ui.edit

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AlarmEntity
import com.example.ui.theme.AccentDismiss
import com.example.ui.theme.BluePrimary
import com.example.util.RingtoneHelper
import com.example.util.RingtoneItem
import com.example.util.RingtonePreviewPlayer
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetAlarmBottomSheet(
    alarm: AlarmEntity?,
    onDismissRequest: () -> Unit,
    onSaveAlarm: (AlarmEntity) -> Unit,
    onDeleteAlarm: ((AlarmEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Initial values
    val currentCal = remember { Calendar.getInstance() }
    val initialHour = alarm?.hour ?: currentCal.get(Calendar.HOUR_OF_DAY)
    val initialMinute = alarm?.minute ?: currentCal.get(Calendar.MINUTE)

    var isAm by remember { mutableStateOf(initialHour < 12) }
    val init12 = when (val h = initialHour % 12) {
        0 -> 12
        else -> h
    }

    // Direct keyboard text state for Hour and Minute
    var hourText by remember { mutableStateOf(String.format(Locale.getDefault(), "%02d", init12)) }
    var minuteText by remember { mutableStateOf(String.format(Locale.getDefault(), "%02d", initialMinute)) }

    fun getCalculatedHour24(): Int {
        val h12 = hourText.toIntOrNull()?.coerceIn(1, 12) ?: 12
        return when {
            isAm && h12 == 12 -> 0
            isAm -> h12
            !isAm && h12 == 12 -> 12
            else -> h12 + 12
        }
    }

    fun getCalculatedMinute(): Int {
        return minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
    }

    // Repeat option: Daily or Once
    var repeatType by remember {
        mutableStateOf(alarm?.repeatType ?: AlarmEntity.REPEAT_DAILY)
    }

    // Dismissal options
    var gradualVolume by remember { mutableStateOf(alarm?.gradualVolume ?: false) }
    var flipToStop by remember { mutableStateOf(alarm?.flipToStop ?: false) }
    var mathPuzzle by remember { mutableStateOf(alarm?.mathPuzzle ?: false) }
    var enterKey by remember { mutableStateOf(alarm?.enterKey ?: false) }
    var vibrate by remember { mutableStateOf(alarm?.vibrate ?: true) }
    var powerOffAlarm by remember { mutableStateOf(alarm?.powerOffAlarm ?: true) }

    // Ringtone option
    var ringtoneUri by remember {
        mutableStateOf(alarm?.ringtoneUri ?: AlarmEntity.DEFAULT_RINGTONE_URI)
    }
    var ringtoneTitle by remember {
        mutableStateOf(alarm?.ringtoneTitle ?: "Default System Alarm")
    }
    var showRingtoneDialog by remember { mutableStateOf(false) }

    // Preview player for testing the selected ringtone before saving
    val sheetPreviewPlayer = remember { RingtonePreviewPlayer(context) }
    var isSheetPreviewPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            sheetPreviewPlayer.release()
        }
    }

    // Snooze option: 2, 5, 10
    var snoozeMinutes by remember { mutableIntStateOf(alarm?.snoozeDurationMinutes ?: 5) }

    // Custom audio file picker
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val title = RingtoneHelper.getFileNameFromUri(context, uri)
            ringtoneUri = uri.toString()
            ringtoneTitle = title
            // Automatically play preview of the chosen custom MP3 so user can verify audio
            sheetPreviewPlayer.play(uri.toString()) {
                isSheetPreviewPlaying = false
            }
            isSheetPreviewPlaying = true
            showRingtoneDialog = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("set_alarm_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // Header: Title & optional delete icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (alarm == null) "New Alarm" else "Edit Alarm",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (alarm != null && onDeleteAlarm != null) {
                    IconButton(
                        onClick = {
                            onDeleteAlarm(alarm)
                            onDismissRequest()
                        },
                        modifier = Modifier.testTag("delete_alarm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Alarm",
                            tint = AccentDismiss
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time Picker Card with KEYBOARD TYPING SUPPORT
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enter time using keyboard or steppers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Hour Keyboard Input with Steppers
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val cur = hourText.toIntOrNull() ?: 12
                                    val next = if (cur >= 12) 1 else cur + 1
                                    hourText = String.format(Locale.getDefault(), "%02d", next)
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Hour")
                            }

                            OutlinedTextField(
                                value = hourText,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(2)
                                    hourText = filtered
                                    if (filtered.length == 2) {
                                        focusManager.moveFocus(FocusDirection.Next)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Next) }
                                ),
                                modifier = Modifier
                                    .width(84.dp)
                                    .testTag("hour_text_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BluePrimary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            Text(
                                text = "Hour (1-12)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            IconButton(
                                onClick = {
                                    val cur = hourText.toIntOrNull() ?: 12
                                    val prev = if (cur <= 1) 12 else cur - 1
                                    hourText = String.format(Locale.getDefault(), "%02d", prev)
                                }
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Minus Hour")
                            }
                        }

                        Text(
                            text = ":",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 20.dp)
                        )

                        // Minute Keyboard Input with Steppers
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val cur = minuteText.toIntOrNull() ?: 0
                                    val next = if (cur >= 59) 0 else cur + 1
                                    minuteText = String.format(Locale.getDefault(), "%02d", next)
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Minute")
                            }

                            OutlinedTextField(
                                value = minuteText,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(2)
                                    minuteText = filtered
                                    if (filtered.length == 2) {
                                        focusManager.clearFocus()
                                    }
                                },
                                textStyle = TextStyle(
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                modifier = Modifier
                                    .width(84.dp)
                                    .testTag("minute_text_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BluePrimary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            Text(
                                text = "Min (00-59)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            IconButton(
                                onClick = {
                                    val cur = minuteText.toIntOrNull() ?: 0
                                    val prev = if (cur <= 0) 59 else cur - 1
                                    minuteText = String.format(Locale.getDefault(), "%02d", prev)
                                }
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Minus Minute")
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // AM/PM selector
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AmPmButton(
                                text = "AM",
                                isSelected = isAm,
                                onClick = { isAm = true }
                            )
                            AmPmButton(
                                text = "PM",
                                isSelected = !isAm,
                                onClick = { isAm = false }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Repeat Options: "Daily" or "Once"
            Text(
                text = "Repeat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptionChoiceChip(
                    title = "Daily (Every day)",
                    selected = repeatType == AlarmEntity.REPEAT_DAILY,
                    modifier = Modifier.weight(1f),
                    testTag = "repeat_daily_chip",
                    onClick = { repeatType = AlarmEntity.REPEAT_DAILY }
                )
                OptionChoiceChip(
                    title = "Once",
                    selected = repeatType == AlarmEntity.REPEAT_ONCE,
                    modifier = Modifier.weight(1f),
                    testTag = "repeat_once_chip",
                    onClick = { repeatType = AlarmEntity.REPEAT_ONCE }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Ringtone Selector Section
            Text(
                text = "Ringtone Sound",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSheetPreviewPlaying)
                        BluePrimary.copy(alpha = 0.12f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        sheetPreviewPlayer.stop()
                        isSheetPreviewPlaying = false
                        showRingtoneDialog = true
                    }
                    .testTag("ringtone_selector_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSheetPreviewPlaying) BluePrimary.copy(alpha = 0.22f)
                                else BluePrimary.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSheetPreviewPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                            contentDescription = if (isSheetPreviewPlaying) "Playing audio preview" else "Ringtone icon",
                            tint = BluePrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ringtoneTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isSheetPreviewPlaying) "Playing preview 🔊 (Tap Stop or change)" else "Tap to choose or test audio",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSheetPreviewPlaying) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSheetPreviewPlaying) BluePrimary else MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Play / Stop Preview button
                    FilledIconButton(
                        onClick = {
                            if (isSheetPreviewPlaying) {
                                sheetPreviewPlayer.stop()
                                isSheetPreviewPlaying = false
                            } else {
                                sheetPreviewPlayer.play(ringtoneUri) {
                                    isSheetPreviewPlaying = false
                                }
                                isSheetPreviewPlaying = true
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isSheetPreviewPlaying) BluePrimary else BluePrimary.copy(alpha = 0.15f),
                            contentColor = if (isSheetPreviewPlaying) Color.White else BluePrimary
                        ),
                        modifier = Modifier.testTag("toggle_ringtone_preview_button")
                    ) {
                        Icon(
                            imageVector = if (isSheetPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isSheetPreviewPlaying) "Stop preview" else "Play preview"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Alarm Dismissal Options
            Text(
                text = "Alarm Dismissal Challenges",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "When alarm rings, STOP will require completing selected challenges before stopping!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val activeChallengeCount = (if (mathPuzzle) 1 else 0) + (if (flipToStop) 1 else 0) + (if (enterKey) 1 else 0)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (activeChallengeCount > 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BluePrimary.copy(alpha = 0.12f))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = null,
                                        tint = BluePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Multi-Task Unlock Sequence ($activeChallengeCount Tasks)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = BluePrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tasks execute sequentially when alarm rings (e.g. Solve Math ➔ Flip phone). Alarm voice automatically mutes during challenges so you can concentrate!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // 1. Math puzzle to dismiss (Riddle)
                    DismissOptionRow(
                        icon = Icons.Default.Calculate,
                        title = "Math Puzzle / Riddle",
                        subtitle = "Must solve math equation before alarm can be stopped",
                        checked = mathPuzzle,
                        testTag = "math_puzzle_switch",
                        onCheckedChange = { mathPuzzle = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 2. Flip-to-stop
                    DismissOptionRow(
                        icon = Icons.Default.ScreenRotation,
                        title = "Flip-to-stop",
                        subtitle = "Turn phone face-down to stop alarm",
                        checked = flipToStop,
                        testTag = "flip_to_stop_switch",
                        onCheckedChange = { flipToStop = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 3. Enter key to stop
                    DismissOptionRow(
                        icon = Icons.Default.KeyboardReturn,
                        title = "Enter Key to Dismiss",
                        subtitle = "Press physical or on-screen Enter key to stop",
                        checked = enterKey,
                        testTag = "enter_key_switch",
                        onCheckedChange = { enterKey = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 4. Gradual Volume Increase
                    DismissOptionRow(
                        icon = Icons.Default.VolumeUp,
                        title = "Gradual Volume Increase",
                        subtitle = "Gently ramps volume from low to max over 30s",
                        checked = gradualVolume,
                        testTag = "gradual_volume_switch",
                        onCheckedChange = { gradualVolume = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 5. Vibration Toggle
                    DismissOptionRow(
                        icon = Icons.Default.Vibration,
                        title = "Vibrate on Alarm",
                        subtitle = "Device vibrates continuously when alarm rings",
                        checked = vibrate,
                        testTag = "vibrate_switch",
                        onCheckedChange = { vibrate = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 6. Power-Off Alarm / Auto Power-On Toggle
                    DismissOptionRow(
                        icon = Icons.Default.PowerSettingsNew,
                        title = "Power-Off Alarm (Auto Power-On)",
                        subtitle = "Wakes phone from power-off (MediaTek/Qualcomm RTC) & rings on boot",
                        checked = powerOffAlarm,
                        testTag = "power_off_alarm_switch",
                        onCheckedChange = { powerOffAlarm = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Snooze Duration Default
            Text(
                text = "Snooze Duration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(2, 5, 10).forEach { mins ->
                    OptionChoiceChip(
                        title = "$mins min",
                        selected = snoozeMinutes == mins,
                        modifier = Modifier.weight(1f),
                        testTag = "snooze_setting_${mins}m_chip",
                        onClick = { snoozeMinutes = mins }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons: Cancel and Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        sheetPreviewPlayer.stop()
                        isSheetPreviewPlaying = false
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("cancel_alarm_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        sheetPreviewPlayer.stop()
                        isSheetPreviewPlaying = false
                        val finalHour24 = getCalculatedHour24()
                        val finalMinute = getCalculatedMinute()

                        val newAlarm = (alarm ?: AlarmEntity(
                            hour = finalHour24,
                            minute = finalMinute
                        )).copy(
                            hour = finalHour24,
                            minute = finalMinute,
                            isEnabled = true,
                            repeatType = repeatType,
                            gradualVolume = gradualVolume,
                            flipToStop = flipToStop,
                            mathPuzzle = mathPuzzle,
                            enterKey = enterKey,
                            vibrate = vibrate,
                            ringtoneUri = ringtoneUri,
                            ringtoneTitle = ringtoneTitle,
                            snoozeDurationMinutes = snoozeMinutes,
                            powerOffAlarm = powerOffAlarm
                        )
                        onSaveAlarm(newAlarm)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("save_alarm_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Save Alarm", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Ringtone Selection Dialog
    if (showRingtoneDialog) {
        RingtoneSelectionDialog(
            currentUri = ringtoneUri,
            currentTitle = ringtoneTitle,
            onSelectCustomAudio = {
                sheetPreviewPlayer.stop()
                isSheetPreviewPlaying = false
                audioPickerLauncher.launch("audio/*")
            },
            onSelectRingtone = { uri, title ->
                ringtoneUri = uri
                ringtoneTitle = title
            },
            onDismiss = { showRingtoneDialog = false }
        )
    }
}

@Composable
private fun AmPmButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) BluePrimary else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("ampm_${text.lowercase()}_button"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OptionChoiceChip(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    testTag: String = "",
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) BluePrimary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) BluePrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DismissOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) BluePrimary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BluePrimary
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun RingtoneSelectionDialog(
    currentUri: String,
    currentTitle: String,
    onSelectCustomAudio: () -> Unit,
    onSelectRingtone: (uri: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ringtones = remember { RingtoneHelper.getAvailableSystemRingtones(context) }
    val previewPlayer = remember { RingtonePreviewPlayer(context) }
    var currentlyPlayingUri by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            previewPlayer.release()
        }
    }

    AlertDialog(
        onDismissRequest = {
            previewPlayer.release()
            onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Select & Preview Tone",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                // Button to pick custom audio file from storage (MP3)
                Button(
                    onClick = {
                        previewPlayer.stop()
                        currentlyPlayingUri = null
                        onSelectCustomAudio()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("choose_custom_audio_button")
                ) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Custom File (MP3)")
                }

                // Currently selected tone quick-preview card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Choice:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                if (currentlyPlayingUri == currentUri) {
                                    previewPlayer.stop()
                                    currentlyPlayingUri = null
                                } else {
                                    previewPlayer.play(currentUri) {
                                        currentlyPlayingUri = null
                                    }
                                    currentlyPlayingUri = currentUri
                                }
                            },
                            modifier = Modifier.testTag("play_current_choice_button")
                        ) {
                            Icon(
                                imageVector = if (currentlyPlayingUri == currentUri) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (currentlyPlayingUri == currentUri) "Stop current tone" else "Listen current tone",
                                tint = if (currentlyPlayingUri == currentUri) MaterialTheme.colorScheme.error else BluePrimary
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                Text(
                    text = "System Default Ringtones",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(ringtones) { item ->
                        val isSelected = currentUri == item.uriString
                        val isPlaying = currentlyPlayingUri == item.uriString

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Select and preview sound immediately
                                    onSelectRingtone(item.uriString, item.title)
                                    previewPlayer.play(item.uriString) {
                                        currentlyPlayingUri = null
                                    }
                                    currentlyPlayingUri = item.uriString
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectRingtone(item.uriString, item.title)
                                    previewPlayer.play(item.uriString) {
                                        currentlyPlayingUri = null
                                    }
                                    currentlyPlayingUri = item.uriString
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isPlaying) {
                                    Text(
                                        text = "Playing now 🔊",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BluePrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Dedicated Play/Stop button for each item
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        previewPlayer.stop()
                                        currentlyPlayingUri = null
                                    } else {
                                        onSelectRingtone(item.uriString, item.title)
                                        previewPlayer.play(item.uriString) {
                                            currentlyPlayingUri = null
                                        }
                                        currentlyPlayingUri = item.uriString
                                    }
                                },
                                modifier = Modifier.testTag("preview_${item.title}")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop" else "Play",
                                    tint = if (isPlaying) MaterialTheme.colorScheme.error else BluePrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    previewPlayer.release()
                    currentlyPlayingUri = null
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            if (currentlyPlayingUri != null) {
                TextButton(
                    onClick = {
                        previewPlayer.stop()
                        currentlyPlayingUri = null
                    }
                ) {
                    Text("Stop Audio")
                }
            }
        }
    )
}
