package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.service.SecuritySystemState
import com.example.ui.theme.AlarmRed
import com.example.ui.theme.AlarmRedBright
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.WarningAmber

/**
 * Prominent Full-Screen Emergency Alarm Interface shown when MOTION_DETECTED triggers the alarm.
 * Displays:
 * - "WARNING! MOTION DETECTED"
 * - Detection time & date
 * - PIR sensor live state (even if MOTION_ENDED arrived, explains alarm continues until manually stopped)
 * - Very large, unmistakable STOP ALARM button
 */
@Composable
fun FullScreenAlarmOverlay(
    state: SecuritySystemState,
    onStopAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha"
    )
    val ringRadiusFactor by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_radius"
    )

    val bgGlowColor by animateColorAsState(
        targetValue = if (pulseScale > 1.0f) Color(0xFF7F1D1D) else Color(0xFF450A0A),
        animationSpec = tween(400),
        label = "bg_glow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF991B1B),
                        bgGlowColor,
                        Color(0xFF2B070B)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("fullscreen_alarm_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Emergency Banner Pill
            Surface(
                color = Color(0xFF450A0A).copy(alpha = 0.85f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.border(1.5.dp, Color(0xFFFCA5A5), RoundedCornerShape(50))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Emergency Sensor Alert",
                        tint = Color(0xFFFECACA),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INTRUSION ALERT • CONTINUOUS SIREN ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Radar Warning Graphic
            Box(
                modifier = Modifier.size(190.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxRadius = size.minDimension / 2f
                    drawCircle(
                        color = AlarmRed.copy(alpha = ringAlpha),
                        radius = maxRadius * ringRadiusFactor,
                        style = Stroke(width = 6.dp.toPx())
                    )
                    drawCircle(
                        color = AlarmRed.copy(alpha = ringAlpha * 0.6f),
                        radius = maxRadius * ((ringRadiusFactor + 0.35f) % 1.0f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .scale(pulseScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(AlarmRedBright, Color(0xFF991B1B))
                            ),
                            shape = CircleShape
                        )
                        .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = stringResource(R.string.warning_motion_detected),
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Warning Headline
            Text(
                text = stringResource(R.string.warning_motion_detected),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("alarm_warning_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "PIR Motion Sensor triggered an emergency security alarm. Audio & vibration will loop continuously until you press STOP ALARM.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFECACA),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Telemetry Details Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2B070B).copy(alpha = 0.88f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DETECTION TIME",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFECACA)
                        )
                        Text(
                            text = state.activeAlarmTriggerTime ?: "${state.lastMotionDate} • ${state.lastMotionTime}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.testTag("alarm_detection_time_text")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PIR SENSOR STREAM",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFECACA)
                        )
                        Text(
                            text = if (state.isPirMotionCurrentlyActive) {
                                "MOTION_DETECTED (ACTIVE)"
                            } else {
                                "MOTION_ENDED (ALARM LOCKED)"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (state.isPirMotionCurrentlyActive) AlarmRedBright else WarningAmber
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BluetoothConnected,
                                contentDescription = "HC-05 Device",
                                tint = Color(0xFFFECACA),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "HC-05 LINK",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFECACA)
                            )
                        }
                        Text(
                            text = "${state.connectedDeviceName} (${state.connectedDeviceAddress})",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Very Large Prominent STOP ALARM Button
            Button(
                onClick = onStopAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .border(2.5.dp, Color.White, RoundedCornerShape(24.dp))
                    .semantics { contentDescription = "Stop Alarm Immediately" }
                    .testTag("fullscreen_stop_alarm_button"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlarmRedBright,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 12.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = stringResource(R.string.btn_stop_alarm),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Silences siren & vibration • Keeps HC-05 connected & armed",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
