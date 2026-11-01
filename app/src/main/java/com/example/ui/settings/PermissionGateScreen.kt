package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.AccentSuccess
import com.example.ui.theme.BluePrimary
import com.example.util.PermissionHelper

@Composable
fun PermissionGateScreen(
    onRequestNotificationPermission: () -> Unit,
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBatteryIgnored by remember {
        mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context))
    }
    var canExactAlarm by remember {
        mutableStateOf(PermissionHelper.canScheduleExactAlarms(context))
    }
    var hasNotif by remember {
        mutableStateOf(PermissionHelper.hasNotificationPermission(context))
    }
    var canDrawOverlays by remember {
        mutableStateOf(PermissionHelper.canDrawOverlays(context))
    }

    var showSuccessPopup by remember { mutableStateOf(false) }

    fun refreshStates() {
        isBatteryIgnored = PermissionHelper.isBatteryOptimizationIgnored(context)
        canExactAlarm = PermissionHelper.canScheduleExactAlarms(context)
        hasNotif = PermissionHelper.hasNotificationPermission(context)
        canDrawOverlays = PermissionHelper.canDrawOverlays(context)

        if (isBatteryIgnored && canExactAlarm && hasNotif && canDrawOverlays) {
            showSuccessPopup = true
        }
    }

    // Refresh permissions state whenever user returns from settings (every time app is resumed)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allGranted = isBatteryIgnored && canExactAlarm && hasNotif && canDrawOverlays

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("permission_gate_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(BluePrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Required Permissions",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "To guarantee the alarm screen wakes up when the phone is locked and cannot be bypassed, all 4 permissions below must be allowed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 1. Exact Alarm
                PermissionGateCard(
                    icon = Icons.Default.Alarm,
                    title = "Exact Alarms Permission",
                    subtitle = "Rings at the exact second even during deep phone sleep.",
                    isGranted = canExactAlarm,
                    buttonText = "Grant Exact Alarm",
                    testTag = "gate_grant_exact_alarm",
                    onAction = {
                        try {
                            context.startActivity(PermissionHelper.getExactAlarmPermissionIntent(context))
                        } catch (_: Exception) {}
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Battery Optimization
                PermissionGateCard(
                    icon = Icons.Default.BatteryAlert,
                    title = "Disable Battery Optimization",
                    subtitle = "Prevents OS from killing alarm when battery is low (1%).",
                    isGranted = isBatteryIgnored,
                    buttonText = "Disable Optimization",
                    testTag = "gate_disable_battery_opt",
                    onAction = {
                        try {
                            context.startActivity(PermissionHelper.getIgnoreBatteryOptimizationIntent(context))
                        } catch (_: Exception) {}
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Display Over Other Apps (Overlay)
                PermissionGateCard(
                    icon = Icons.Default.Layers,
                    title = "Display Over Other Apps",
                    subtitle = "Required to wake screen over lock and prevent exiting alarm.",
                    isGranted = canDrawOverlays,
                    buttonText = "Allow Display Over Apps",
                    testTag = "gate_allow_overlay",
                    onAction = {
                        try {
                            context.startActivity(PermissionHelper.getDrawOverlaysIntent(context))
                        } catch (_: Exception) {}
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Notifications
                PermissionGateCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "Notifications & Full Screen",
                    subtitle = "Allows high-priority alarm wake-up intents.",
                    isGranted = hasNotif,
                    buttonText = "Allow Notifications",
                    testTag = "gate_allow_notifications",
                    onAction = onRequestNotificationPermission
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Action Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (allGranted) {
                    Button(
                        onClick = { showSuccessPopup = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSuccess),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("gate_continue_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All Granted • Continue",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Button(
                        onClick = { refreshStates() },
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("gate_check_again_button")
                    ) {
                        Text(
                            text = "Check Permissions Again",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Success Pop-up dialog when all permissions are granted
    if (showSuccessPopup) {
        AlertDialog(
            onDismissRequest = {
                showSuccessPopup = false
                onAllPermissionsGranted()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentSuccess,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "All Permissions Allowed! ✅",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "All 4 critical unmissable permissions are now active!\n\nYour alarm will now reliably wake the screen even when the phone is locked and will not be killed by the system.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessPopup = false
                        onAllPermissionsGranted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("popup_continue_to_app_button")
                ) {
                    Text("Continue to Alarm App", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun PermissionGateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    buttonText: String,
    testTag: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) AccentSuccess else BluePrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
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
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag(testTag)
                ) {
                    Text(
                        text = buttonText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
