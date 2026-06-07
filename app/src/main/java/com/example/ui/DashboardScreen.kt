package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val isFaceDetected by viewModel.isFaceDetected.collectAsState()
    val pointerPos by viewModel.pointerPosition.collectAsState()
    val score by viewModel.score.collectAsState()
    val targets by viewModel.targets.collectAsState()

    val scrollState = rememberLazyListState()
    val scrollSignal by viewModel.scrollSignal.collectAsState()

    // Smooth scroll offset animation triggered by eyebrow movements
    LaunchedEffect(scrollSignal) {
        scrollSignal?.let { signal ->
            when (signal) {
                "UP" -> scrollState.animateScrollBy(-500f)
                "DOWN" -> scrollState.animateScrollBy(500f)
            }
        }
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
    ) {
        // Futuristic Brand Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OptiSync",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            letterSpacing = (-1).sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "On-Device Tech Gesture Navigator",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
                
                // Minimalist status bar pill
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isFaceDetected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFaceDetected) Color(0xFF00E5FF)
                                        else Color(0xFFFF2D55)
                                    )
                            )
                            Text(
                                text = if (isFaceDetected) "TRACKING" else "NO FACE",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = if (isFaceDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Recenter button — tap to make current nose position the new center
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                            .clickable { viewModel.requestCenterRecalibration() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Recenter pointer",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "RECENTER",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        // Mini status instructions card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Tips",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "How to Interact:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Tilt your nose to slide the cyber pointer marker. Raise eyebrows to SCROLL UP, and squint/furrow eyebrows to SCROLL DOWN! Smile or wink to click targets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Live scroll gesture action indicator toast overlay
        if (scrollSignal != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    border = borderGlowBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (scrollSignal == "UP") Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll Signal Status",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BROW MOVEMENT: SCROLLING ${scrollSignal}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // Main Simulated Smartphone Deck playground
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (MaterialTheme.colorScheme.background == Color(0xFF030914)) Color(0xFF070F1E)
                        else Color(0xFFECEFF3)
                    )
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .testTag("smartphone_deck")
            ) {
                // Interactive playground grid targets
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val heightPx = constraints.maxHeight.toFloat()

                    // Render targets
                    targets.forEach { target ->
                        // Convert normalized points (0-1000) to absolute screen sizes dynamically
                        val targetX = (target.point.x / 1000f) * widthPx
                        val targetY = (target.point.y / 1000f) * heightPx

                        // Check if pointer is hovering (within bounds)
                        val isHovered = Math.abs(pointerPos.x - target.point.x) < 90f && Math.abs(pointerPos.y - target.point.y) < 90f

                        val cardColor by animateColorAsState(
                            targetValue = when {
                                target.isHit -> Color(0xFF00E5FF)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )

                        val borderGlow = 1.dp
                        val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

                        // Render simulated deck tile button
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = (targetX / LocalContext.current.resources.displayMetrics.density).dp - 45.dp,
                                    y = (targetY / LocalContext.current.resources.displayMetrics.density).dp - 45.dp
                                )
                                .size(90.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardColor)
                                .border(borderGlow, borderColor, RoundedCornerShape(16.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = when (target.label) {
                                        "Menu" -> Icons.Default.Menu
                                        "Photos" -> Icons.Default.AccountBox
                                        "Settings" -> Icons.Default.Settings
                                        "Messages" -> Icons.Default.Email
                                        "OptiPlay" -> Icons.Default.FavoriteBorder
                                        "Browser" -> Icons.Default.Search
                                        else -> Icons.Default.Home
                                    },
                                    contentDescription = target.label,
                                    tint = if (target.isHit) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = target.label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.sp
                                    ),
                                    color = if (target.isHit) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Target Deck score banner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "TARGETS CLEARED: $score",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                // Manual reset trigger
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .clickable { viewModel.resetGameTargets() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Targets",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "RESET DECK",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        // Cyber System dynamic stream logs card at the scroll region
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SYNCED OPERATIONAL ACTIVITIES LOGGER",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    
                    val logs = listOf(
                        "On-device gesture engine initialized.",
                        "Biometric comfort level profiles saved.",
                        "Autocentered depth coordinates mapped securely.",
                        "Eyebrows scroll gesture active & listening.",
                        "Wink suppression bounds verified dynamically."
                    )
                    
                    logs.forEach { logLine ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                            )
                            Text(
                                text = logLine,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun borderGlowBorder() = BorderStroke(
    width = 1.5.dp,
    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
)
