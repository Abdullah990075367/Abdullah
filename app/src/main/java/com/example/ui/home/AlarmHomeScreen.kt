package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AlarmEntity
import com.example.ui.edit.SetAlarmBottomSheet
import com.example.ui.settings.ReliabilityDialog
import com.example.ui.theme.BlueOnPrimary
import com.example.ui.theme.BluePrimary
import com.example.util.PermissionHelper
import com.example.util.TimeFormatter
import kotlinx.coroutines.delay

@Composable
fun AlarmHomeScreen(
    alarms: List<AlarmEntity>,
    onSaveAlarm: (AlarmEntity) -> Unit,
    onToggleAlarm: (AlarmEntity, Boolean) -> Unit,
    onDeleteAlarm: (AlarmEntity) -> Unit,
    onScheduleTestAlarm: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showReliabilityDialog by remember { mutableStateOf(false) }
    var showPowerOffGuideDialog by remember { mutableStateOf(false) }

    // Live periodic tick (every 1 second) to refresh countdown status texts in real-time
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L) // update every 1 second
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    // Check if reliability setup is needed on initial launch
    var hasPromptedReliability by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasPromptedReliability && !PermissionHelper.areCriticalPermissionsGranted(context)) {
            hasPromptedReliability = true
            showReliabilityDialog = true
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("alarm_home_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // Top Section: Live Time on Top-Left Corner, and Pill-shaped '+' & 3-dot menu on Top-Right Corner
            val liveCal = remember(currentTimeMillis) {
                java.util.Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
            }
            val (liveTime, liveAmPm) = remember(currentTimeMillis) {
                val h = liveCal.get(java.util.Calendar.HOUR_OF_DAY)
                val m = liveCal.get(java.util.Calendar.MINUTE)
                TimeFormatter.formatClockTime(h, m)
            }
            val liveDateStr = remember(currentTimeMillis) {
                val sdf = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
                sdf.format(java.util.Date(currentTimeMillis))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-Left Corner: Live Current Time Card
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("top_left_current_time_display")
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = liveTime,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = liveAmPm,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Text(
                            text = liveDateStr,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                // Vertical Pill-shaped dark/blue container
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF041E49)) // Dark blue pill
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .testTag("top_right_pill_container")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // "+" Add Alarm Icon Button
                        IconButton(
                            onClick = {
                                editingAlarm = null
                                showSheet = true
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("add_alarm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Alarm",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Subtle micro-divider inside pill
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(1.dp)
                                .background(Color(0x33FFFFFF))
                        )

                        // 3-dot Menu Icon Button
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("three_dot_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Unmissable Alarm Settings") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = BluePrimary)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showReliabilityDialog = true
                                    },
                                    modifier = Modifier.testTag("menu_reliability_settings")
                                )
                                DropdownMenuItem(
                                    text = { Text("Power-Off Alarm Guide") },
                                    leadingIcon = {
                                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = BluePrimary)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showPowerOffGuideDialog = true
                                    },
                                    modifier = Modifier.testTag("menu_power_off_guide")
                                )
                                DropdownMenuItem(
                                    text = { Text("Test Alarm (Rings in 1m)") },
                                    leadingIcon = {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BluePrimary)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onScheduleTestAlarm()
                                    },
                                    modifier = Modifier.testTag("menu_test_alarm")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Large Title: "Alarm"
            Text(
                text = "Alarm",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("alarm_screen_title")
            )

            // Vertical Alarm List
            if (alarms.isEmpty()) {
                EmptyAlarmsView(onAddClick = {
                    editingAlarm = null
                    showSheet = true
                })
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("alarms_list"),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = alarms,
                        key = { it.id }
                    ) { alarm ->
                        AlarmListItem(
                            alarm = alarm,
                            currentTimeMillis = currentTimeMillis,
                            onItemClick = {
                                editingAlarm = alarm
                                showSheet = true
                            },
                            onToggle = { isChecked ->
                                onToggleAlarm(alarm, isChecked)
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // Set / Edit Alarm Sheet
    if (showSheet) {
        SetAlarmBottomSheet(
            alarm = editingAlarm,
            onDismissRequest = {
                showSheet = false
                editingAlarm = null
            },
            onSaveAlarm = { alarm ->
                onSaveAlarm(alarm)
                showSheet = false
                editingAlarm = null
            },
            onDeleteAlarm = { alarm ->
                onDeleteAlarm(alarm)
                showSheet = false
                editingAlarm = null
            }
        )
    }

    // Reliability & Unmissable Permissions Dialog
    if (showReliabilityDialog) {
        ReliabilityDialog(
            onDismiss = { showReliabilityDialog = false },
            onRequestNotificationPermission = onRequestNotificationPermission
        )
    }

    // Power-Off Alarm Guide Dialog
    if (showPowerOffGuideDialog) {
        PowerOffGuideDialog(
            onDismiss = { showPowerOffGuideDialog = false }
        )
    }
}

@Composable
private fun AlarmListItem(
    alarm: AlarmEntity,
    currentTimeMillis: Long,
    onItemClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val (timeString, amPm) = TimeFormatter.formatClockTime(alarm.hour, alarm.minute)
    val statusText = TimeFormatter.formatAlarmStatus(alarm, currentTimeMillis)

    val textColor by animateColorAsState(
        targetValue = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.outline,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "alarm_text_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onItemClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag("alarm_item_${alarm.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Large digital clock font with AM/PM
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = timeString,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = textColor,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = amPm,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (alarm.isEnabled) BluePrimary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Status text (e.g. "Every day. Ring in 1 hour and 23 minutes")
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                fontWeight = if (alarm.isEnabled) FontWeight.Medium else FontWeight.Normal
            )

            // Feature Badges (Dismissal challenge & Power-Off indicators)
            if (alarm.isEnabled && (alarm.flipToStop || alarm.mathPuzzle || alarm.enterKey || alarm.gradualVolume || alarm.vibrate || alarm.powerOffAlarm)) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (alarm.powerOffAlarm) {
                        FeatureBadge(icon = Icons.Default.PowerSettingsNew, label = "Auto-On")
                    }
                    if (alarm.vibrate) {
                        FeatureBadge(icon = Icons.Default.Vibration, label = "Vibrate")
                    }
                    if (alarm.flipToStop) {
                        FeatureBadge(icon = Icons.Default.ScreenRotation, label = "Flip")
                    }
                    if (alarm.mathPuzzle) {
                        FeatureBadge(icon = Icons.Default.Calculate, label = "Math")
                    }
                    if (alarm.enterKey) {
                        FeatureBadge(icon = Icons.Default.KeyboardReturn, label = "Enter")
                    }
                    if (alarm.gradualVolume) {
                        FeatureBadge(icon = Icons.Default.VolumeUp, label = "Fade-in")
                    }
                }
            }
        }

        // Animated Toggle Switch on right
        Switch(
            checked = alarm.isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BluePrimary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .padding(start = 16.dp)
                .testTag("alarm_switch_${alarm.id}")
        )
    }
}

@Composable
private fun FeatureBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BluePrimary.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BluePrimary,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = BluePrimary
        )
    }
}

@Composable
private fun EmptyAlarmsView(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(BluePrimary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "No Alarms Set",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap the '+' in the top right to create your first unmissable alarm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun PowerOffGuideDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Power-Off Alarm & Auto-On",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "How Auto Power-On Works:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Hardware RTC Wakeup:\n" +
                            "For devices powered by Qualcomm Snapdragon or MediaTek MTK chipsets (Xiaomi/Redmi, POCO, Vivo, Oppo, Realme, Huawei), the app programs PMIC hardware RTC alarm registers. The phone automatically powers on 1-2 minutes before the alarm time to ring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "2. Direct Boot Awareness:\n" +
                            "When the phone turns on, this app starts immediately in Direct Boot mode before lockscreen PIN or password is typed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "3. Missed Alarm Protection:\n" +
                            "If your phone was powered off when the alarm time arrived and you turn the phone on afterwards, the app catches the missed alarm and rings immediately upon boot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Note: In phone Settings, keep 'Scheduled Power On' enabled if supported by your device vendor (MIUI/HyperOS or ColorOS).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        }
    )
}

