package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GestureSettings

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settingsState.collectAsState(initial = GestureSettings())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Futuristic Title Section
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Controls & Gestures",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Adjust tracking response coefficients and action switches",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }

        // Section 1: Response Sensitivity
        item {
            CardSectionHeader(title = "SENSITIVITY & MAPPING")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sensitivity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Base Nose Sensitivity",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = String.format("%.1fx", settings.pointerSensitivity),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Slider(
                            value = settings.pointerSensitivity,
                            onValueChange = { viewModel.updatePointerSensitivity(it) },
                            valueRange = 1.0f..15.0f,
                            modifier = Modifier.testTag("sensitivity_slider")
                        )
                        Text(
                            text = "Higher coefficients accelerate cursor speeds relative to nose angles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 0.5.dp)

                    // Blink Threshold Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Blink Lock Threshold",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = String.format("%.2f", settings.blinkThreshold),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Slider(
                            value = settings.blinkThreshold,
                            onValueChange = { viewModel.updateBlinkThreshold(it) },
                            valueRange = 0.05f..0.35f,
                            modifier = Modifier.testTag("blink_threshold_slider")
                        )
                        Text(
                            text = "Eye open probability index below which a blink/click is captured. Lower forces firmer closure.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 0.5.dp)

                    // Smile Threshold Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Smile Recognition Threshold",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = String.format("%.2f", settings.smileClickThreshold),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Slider(
                            value = settings.smileClickThreshold,
                            onValueChange = { viewModel.updateSmileClickThreshold(it) },
                            valueRange = 0.30f..0.90f,
                            modifier = Modifier.testTag("smile_threshold_slider")
                        )
                        Text(
                            text = "Smiling intensity required to trigger simulated clicks. Adjust higher to prevent accidental fire.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 0.5.dp)

                    // Eyebrow Raise Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Brow Raise Threshold (Scroll Up)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = String.format("%.2f", settings.browRaiseThreshold),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Slider(
                            value = settings.browRaiseThreshold,
                            onValueChange = { viewModel.updateBrowRaiseThreshold(it) },
                            valueRange = 0.32f..0.55f,
                            modifier = Modifier.testTag("brow_raise_slider")
                        )
                        Text(
                            text = "Standard eyebrow/eye height index to trigger Scroll Up. Eyebrow raises above this trigger the upward animation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 0.5.dp)

                    // Eyebrow Squint Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Brow Squint Threshold (Scroll Down)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = String.format("%.2f", settings.browSquintThreshold),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Slider(
                            value = settings.browSquintThreshold,
                            onValueChange = { viewModel.updateBrowSquintThreshold(it) },
                            valueRange = 0.18f..0.35f,
                            modifier = Modifier.testTag("brow_squint_slider")
                        )
                        Text(
                            text = "Standard index value below which Scroll Down is triggered. Furrowing brow limits eye-brow spacing, ignoring accidental winks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Section 2: Toggle Switches
        item {
            CardSectionHeader(title = "ACTIVE GESTURE ACTIONS")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Left Eye wink
                    GestureToggleRow(
                        title = "Left Eye Wink (Select Action)",
                        description = "Triggers a secondary click or 'back' layout operation when left eye closes.",
                        checked = settings.enableLeftEyeClick,
                        onCheckedChange = { viewModel.toggleLeftEyeClick(it) },
                        tag = "toggle_left_eye"
                    )

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 0.5.dp)

                    // Right eye wink
                    GestureToggleRow(
                        title = "Right Eye Wink (Click Action)",
                        description = "Triggers a standard pointer click operation when right eye closes.",
                        checked = settings.enableRightEyeClick,
                        onCheckedChange = { viewModel.toggleRightEyeClick(it) },
                        tag = "toggle_right_eye"
                    )

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 0.5.dp)

                    // Smile trigger
                    GestureToggleRow(
                        title = "Smile Click Mapping",
                        description = "Triggers confirmation/click selection when you smile.",
                        checked = settings.enableSmileClick,
                        onCheckedChange = { viewModel.toggleSmileClick(it) },
                        tag = "toggle_smile"
                    )

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 0.5.dp)

                    // Mouth open action
                    GestureToggleRow(
                        title = "Mouth Opened Action",
                        description = "Triggers simulated home screen/reset action when you expand your jaw.",
                        checked = settings.enableMouthOpenAction,
                        onCheckedChange = { viewModel.toggleMouthOpen(it) },
                        tag = "toggle_mouth_open"
                    )

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 0.5.dp)

                    // Eyebrow scrolling toggle
                    GestureToggleRow(
                        title = "Eyebrow Move Scrolling (Up / Down)",
                        description = "Enables scrolling menus up/down dynamically when raising or squinting/furrowing eyebrows.",
                        checked = settings.enableEyebrowScroll,
                        onCheckedChange = { viewModel.toggleEyebrowScroll(it) },
                        tag = "toggle_eyebrow"
                    )
                }
            }
        }

        // Section 3: Battery & Performance Tuning (Frame skips)
        item {
            CardSectionHeader(title = "HARDWARE & LATENCY CPU OPTIMIZATION")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GestureToggleRow(
                        title = "Haptic Vibration Feedback",
                        description = "Produces a physical click tap vibration upon successful gesture trigger.",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.toggleHaptics(it) },
                        tag = "toggle_haptics"
                    )

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 0.5.dp)

                    // CPU frame saver slider (to prevent lag on old devices)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CPU Frame Skip Factor",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (settings.frameSkipCount == 0) "No Skip (Ultra-low Latency)" else "${settings.frameSkipCount} frames",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = if (settings.frameSkipCount == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Slider(
                            value = settings.frameSkipCount.toFloat(),
                            onValueChange = {
                                viewModel.updateFrameSkipCount(it.toInt())
                            },
                            valueRange = 0f..3f,
                            steps = 2,
                            modifier = Modifier.testTag("frame_skip_slider")
                        )
                        Text(
                            text = "Excellent for saving battery on ultra-old processor chips. Skips intermediate camera frames before running the detector to reduce calculation load.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CardSectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun GestureToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
