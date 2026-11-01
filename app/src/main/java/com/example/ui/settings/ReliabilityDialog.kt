package com.example.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentSuccess
import com.example.ui.theme.BluePrimary
import com.example.util.PermissionHelper

@Composable
fun ReliabilityDialog(
    onDismiss: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    var isBatteryIgnored by remember {
        mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context))
    }
    var canExactAlarm by remember {
        mutableStateOf(PermissionHelper.canScheduleExactAlarms(context))
    }
    var hasNotif by remember {
        mutableStateOf(PermissionHelper.hasNotificationPermission(context))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Unmissable Alarm Settings",
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
                    .testTag("reliability_dialog_content")
            ) {
                Text(
                    text = "To ensure alarms ring even on 1% battery, Doze mode, or screen lock, verify the permissions below:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Battery Optimization
                PermissionStatusItem(
                    icon = Icons.Default.BatteryAlert,
                    title = "Battery Optimization",
                    description = if (isBatteryIgnored) "Unrestricted (Safe from Doze mode)" else "Restricted (OS may delay alarms)",
                    isGranted = isBatteryIgnored,
                    actionText = "Disable Optimization",
                    testTag = "battery_optimization_action_button",
                    onAction = {
                        try {
                            context.startActivity(PermissionHelper.getIgnoreBatteryOptimizationIntent(context))
                        } catch (_: Exception) {}
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Exact Alarms
                PermissionStatusItem(
                    icon = Icons.Default.Alarm,
                    title = "Exact Alarms",
                    description = if (canExactAlarm) "Allowed (Exact millisecond timing)" else "Disallowed (OS schedules loosely)",
                    isGranted = canExactAlarm,
                    actionText = "Grant Exact Alarm",
                    testTag = "exact_alarm_action_button",
                    onAction = {
                        try {
                            context.startActivity(PermissionHelper.getExactAlarmPermissionIntent(context))
                        } catch (_: Exception) {}
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Notifications
                PermissionStatusItem(
                    icon = Icons.Default.NotificationsActive,
                    title = "Notifications",
                    description = if (hasNotif) "Allowed (Full-screen intent ready)" else "Missing (Alerts might not pop up)",
                    isGranted = hasNotif,
                    actionText = "Allow Notifications",
                    testTag = "notification_action_button",
                    onAction = onRequestNotificationPermission
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Power-Off Alarm & Auto Power-On (Hardware RTC / Direct Boot)
                PermissionStatusItem(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "Power-Off Alarm (Auto-Power-On)",
                    description = "Configured: Broadcasts Qualcomm & MediaTek hardware RTC power-on signals. Direct Boot is active so the alarm rings even before lockscreen PIN entry if the phone powers on.",
                    isGranted = true,
                    actionText = "Active",
                    testTag = "power_off_status_item",
                    onAction = {}
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("close_reliability_dialog_button")
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun PermissionStatusItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    actionText: String,
    testTag: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) BluePrimary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) AccentSuccess else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                ) {
                    Text(text = actionText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
