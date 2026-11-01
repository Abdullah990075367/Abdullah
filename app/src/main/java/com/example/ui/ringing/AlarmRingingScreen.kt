package com.example.ui.ringing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentDismiss
import com.example.ui.theme.BluePrimary
import com.example.util.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

enum class ChallengeStep(val title: String, val badge: String) {
    MATH_PUZZLE("Math Riddle", "Mental Challenge"),
    ENTER_KEY("Press Enter", "Confirmation Challenge"),
    FLIP_DEVICE("Flip Phone Face-Down", "Sensor Challenge")
}

@Composable
fun AlarmRingingScreen(
    hour: Int,
    minute: Int,
    ringtoneTitle: String,
    gradualVolume: Boolean,
    flipToStop: Boolean,
    mathPuzzle: Boolean,
    enterKey: Boolean,
    enterKeyPressedEvent: StateFlow<Int>,
    backPressedWarningEvent: StateFlow<Int>,
    flipDetectedEvent: StateFlow<Int>,
    onSetMute: (Boolean) -> Unit,
    onSnooze: (Int) -> Unit,
    onDismissSuccess: () -> Unit
) {
    val (formattedTime, amPm) = TimeFormatter.formatClockTime(hour, minute)

    // Pulse animation for ringing clock
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "clock_pulse"
    )

    // Flip device animation for flip step
    val flipAngle by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flip_angle"
    )

    // Build ordered list of active challenges:
    // 1. Math Puzzle -> 2. Enter Key -> 3. Flip Device
    val activeChallenges = remember(mathPuzzle, enterKey, flipToStop) {
        buildList {
            if (mathPuzzle) add(ChallengeStep.MATH_PUZZLE)
            if (enterKey) add(ChallengeStep.ENTER_KEY)
            if (flipToStop) add(ChallengeStep.FLIP_DEVICE)
        }
    }

    // -1 = Ringing main screen; 0 until activeChallenges.size = Actively solving that challenge
    var currentStepIndex by remember { mutableIntStateOf(-1) }
    var showSnoozePicker by remember { mutableStateOf(false) }
    var showExitWarningDialog by remember { mutableStateOf(false) }

    // When exiting screen or unmounting, ensure unmute
    DisposableEffect(Unit) {
        onDispose {
            onSetMute(false)
        }
    }

    // Handle back button
    BackHandler(enabled = true) {
        if (currentStepIndex >= 0) {
            // User backed out of challenge screen: restore volume to full and return to main screen
            currentStepIndex = -1
            onSetMute(false)
        } else {
            showExitWarningDialog = true
        }
    }

    // Listen for back press warning from activity
    val backCount by backPressedWarningEvent.collectAsState()
    LaunchedEffect(backCount) {
        if (backCount > 0) {
            if (currentStepIndex >= 0) {
                currentStepIndex = -1
                onSetMute(false)
            } else {
                showExitWarningDialog = true
            }
        }
    }

    // Helper to advance or complete challenges
    fun advanceOrCompleteStep() {
        val next = currentStepIndex + 1
        if (next < activeChallenges.size) {
            currentStepIndex = next
            // Keep muted during next sequential challenge
            onSetMute(true)
        } else {
            // All tasks finished! Stop alarm
            currentStepIndex = -1
            onSetMute(false)
            onDismissSuccess()
        }
    }

    fun cancelChallengeAndResumeAudio() {
        currentStepIndex = -1
        onSetMute(false)
    }

    // Math puzzle state
    var num1 by remember { mutableIntStateOf(Random.nextInt(15, 50)) }
    var num2 by remember { mutableIntStateOf(Random.nextInt(12, 45)) }
    var isSubtraction by remember { mutableStateOf(Random.nextBoolean()) }
    val correctAnswer = remember(num1, num2, isSubtraction) {
        if (isSubtraction) (num1 + num2) - num1 else num1 + num2
    }
    val equationText = remember(num1, num2, isSubtraction) {
        if (isSubtraction) "${num1 + num2} - $num1" else "$num1 + $num2"
    }
    var mathInput by remember { mutableStateOf("") }
    var mathErrorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Physical Enter key listener
    val enterEventCount by enterKeyPressedEvent.collectAsState()
    LaunchedEffect(enterEventCount) {
        if (enterEventCount > 0 && currentStepIndex >= 0 &&
            currentStepIndex < activeChallenges.size &&
            activeChallenges[currentStepIndex] == ChallengeStep.ENTER_KEY
        ) {
            advanceOrCompleteStep()
        }
    }

    // Accelerometer Flip listener
    val flipCount by flipDetectedEvent.collectAsState()
    LaunchedEffect(flipCount) {
        if (flipCount > 0 && currentStepIndex >= 0 &&
            currentStepIndex < activeChallenges.size &&
            activeChallenges[currentStepIndex] == ChallengeStep.FLIP_DEVICE
        ) {
            advanceOrCompleteStep()
        }
    }

    // Main Ringing Screen (when currentStepIndex == -1)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("alarm_ringing_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Ringtone badge & multi-task reminder
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Alarm Active",
                        tint = BluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ringtoneTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (activeChallenges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val taskSummary = activeChallenges.joinToString(" ➔ ") { it.title }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BluePrimary.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Multi-Task Unlock: $taskSummary",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary
                            )
                        }
                    }
                }
            }

            // Middle Section: Large pulsating digital clock display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(pulseScale)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .background(BluePrimary.copy(alpha = 0.08f))
                        .border(3.dp, BluePrimary.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(80.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-1.5).sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = amPm,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BluePrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Text(
                    text = "Alarm is ringing! Wake up!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Bottom Section: STOP & SNOOZE Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // STOP Button
                Button(
                    onClick = {
                        if (activeChallenges.isEmpty()) {
                            onDismissSuccess()
                        } else {
                            // Start sequential challenge at step 0 and mute alarm volume!
                            currentStepIndex = 0
                            mathInput = ""
                            mathErrorMessage = null
                            onSetMute(true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDismiss),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("alarm_ringing_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    val stopLabel = when {
                        activeChallenges.size > 1 -> "STOP (${activeChallenges.size} Tasks to Unlock)"
                        activeChallenges.size == 1 -> "STOP (${activeChallenges.first().title})"
                        else -> "STOP ALARM"
                    }
                    Text(
                        text = stopLabel,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SNOOZE Button
                Button(
                    onClick = { showSnoozePicker = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("alarm_ringing_snooze_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Snooze,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SNOOZE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // SEQUENTIAL MULTI-TASK UNLOCK CHALLENGE DIALOG / MODAL
    // When currentStepIndex >= 0:
    // Volume is set to 0. If user clicks Back, volume resumes!
    // -------------------------------------------------------------
    if (currentStepIndex in activeChallenges.indices) {
        val activeStep = activeChallenges[currentStepIndex]
        val stepNumber = currentStepIndex + 1
        val totalSteps = activeChallenges.size

        AlertDialog(
            onDismissRequest = {
                // Backing out restores volume to full and goes back to alarm ringing
                cancelChallengeAndResumeAudio()
            },
            icon = {
                when (activeStep) {
                    ChallengeStep.MATH_PUZZLE -> Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(40.dp)
                    )
                    ChallengeStep.ENTER_KEY -> Icon(
                        imageVector = Icons.Default.KeyboardReturn,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(40.dp)
                    )
                    ChallengeStep.FLIP_DEVICE -> Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Step Progress Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BluePrimary)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Step $stepNumber of $totalSteps",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = activeStep.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    // Volume Muted Notice Banner
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8F5E9))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeMute,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Alarm voice is 0 (Muted for challenge)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (activeStep) {
                        ChallengeStep.MATH_PUZZLE -> {
                            LaunchedEffect(currentStepIndex) {
                                delay(150)
                                focusRequester.requestFocus()
                            }

                            Text(
                                text = "Calculate this equation to unlock:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "$equationText = ?",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = BluePrimary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = mathInput,
                                onValueChange = {
                                    mathInput = it.filter { ch -> ch.isDigit() }.take(5)
                                    mathErrorMessage = null
                                },
                                placeholder = { Text("Answer") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (mathInput.toIntOrNull() == correctAnswer) {
                                            advanceOrCompleteStep()
                                        } else {
                                            mathErrorMessage = "Incorrect answer! Try again."
                                        }
                                    }
                                ),
                                textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .testTag("math_riddle_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BluePrimary
                                )
                            )

                            if (mathErrorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = mathErrorMessage!!,
                                    color = AccentDismiss,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        ChallengeStep.ENTER_KEY -> {
                            Text(
                                text = "Press physical/virtual Enter key or tap button below to proceed:",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { advanceOrCompleteStep() },
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Press Enter ⏎ to Continue", fontWeight = FontWeight.Bold)
                            }
                        }

                        ChallengeStep.FLIP_DEVICE -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(BluePrimary.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = null,
                                    tint = BluePrimary,
                                    modifier = Modifier.size(50.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Turn your phone face-down on a flat surface or bed to silence and stop the alarm.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Waiting for flip sensor...",
                                style = MaterialTheme.typography.labelSmall,
                                color = BluePrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Notice explaining back action restores volume
                    Text(
                        text = "Note: If you go back, the alarm ringtone volume will resume playing at full volume.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                if (activeStep == ChallengeStep.MATH_PUZZLE) {
                    Button(
                        onClick = {
                            if (mathInput.toIntOrNull() == correctAnswer) {
                                advanceOrCompleteStep()
                            } else {
                                mathErrorMessage = "❌ Incorrect answer! Try again."
                                num1 = Random.nextInt(15, 50)
                                num2 = Random.nextInt(12, 45)
                                isSubtraction = Random.nextBoolean()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                        modifier = Modifier.testTag("submit_math_riddle_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (stepNumber < totalSteps) "Next Step ➔" else "Finish & Stop Alarm")
                    }
                } else if (activeStep == ChallengeStep.FLIP_DEVICE) {
                    Button(
                        onClick = {
                            // Manual fallback if device sensor is not present
                            advanceOrCompleteStep()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                    ) {
                        Text("Confirm Flip Action")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        // User backed out of challenge: restore voice to full
                        cancelChallengeAndResumeAudio()
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back (Resume Sound)")
                }
            }
        )
    }

    // Snooze Duration Picker Dialog (2m, 5m, 10m)
    if (showSnoozePicker) {
        AlertDialog(
            onDismissRequest = { showSnoozePicker = false },
            title = {
                Text("Snooze Alarm For:", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(2, 5, 10).forEach { mins ->
                        Button(
                            onClick = {
                                showSnoozePicker = false
                                onSnooze(mins)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("snooze_${mins}_picker_button")
                        ) {
                            Text(
                                text = "Snooze for $mins minutes",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSnoozePicker = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Exit Warning Dialog (When user tries back button or home)
    if (showExitWarningDialog) {
        AlertDialog(
            onDismissRequest = { showExitWarningDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = AccentDismiss,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Alarm Is Ringing!", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = if (activeChallenges.isNotEmpty()) {
                        "You cannot exit while the alarm is ringing! Tap STOP to solve the unlock challenges (alarm sound will mute during challenges)."
                    } else {
                        "You cannot exit while the alarm is ringing! Please tap STOP or SNOOZE to proceed."
                    },
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitWarningDialog = false
                        if (activeChallenges.isEmpty()) {
                            onDismissSuccess()
                        } else {
                            currentStepIndex = 0
                            mathInput = ""
                            mathErrorMessage = null
                            onSetMute(true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDismiss)
                ) {
                    Text(if (activeChallenges.isNotEmpty()) "Unlock Challenge" else "Stop Alarm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showExitWarningDialog = false
                        onSnooze(5)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text("Snooze 5m", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        )
    }
}
