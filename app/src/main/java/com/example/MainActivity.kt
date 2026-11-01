package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.home.AlarmHomeScreen
import com.example.ui.settings.PermissionGateScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.PermissionHelper
import com.example.viewmodel.AlarmViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    AlarmAppContent()
                }
            }
        }
    }
}

@Composable
fun AlarmAppContent(
    viewModel: AlarmViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()

    var permissionsGranted by remember {
        mutableStateOf(PermissionHelper.areCriticalPermissionsGranted(context))
    }

    // Refresh permission check whenever app is resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsGranted = PermissionHelper.areCriticalPermissionsGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionsGranted = PermissionHelper.areCriticalPermissionsGranted(context)
        }
    }

    if (!permissionsGranted) {
        // Enforce permission gate before user can use the app
        PermissionGateScreen(
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onAllPermissionsGranted = {
                permissionsGranted = true
            }
        )
    } else {
        // Show main home screen
        AlarmHomeScreen(
            alarms = alarms,
            onSaveAlarm = { alarm ->
                viewModel.saveAlarm(alarm)
            },
            onToggleAlarm = { alarm, isEnabled ->
                viewModel.toggleAlarm(alarm, isEnabled)
            },
            onDeleteAlarm = { alarm ->
                viewModel.deleteAlarm(alarm)
            },
            onScheduleTestAlarm = {
                viewModel.scheduleTestAlarm()
            },
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }
}
