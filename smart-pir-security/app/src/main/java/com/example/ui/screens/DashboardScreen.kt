package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.service.AlarmOperationalStatus
import com.example.service.BluetoothConnectionState
import com.example.service.SecuritySystemState
import com.example.ui.theme.AlarmRed
import com.example.ui.theme.AlarmRedContainer
import com.example.ui.theme.BluetoothCyan
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.OnAlarmRedContainer
import com.example.ui.theme.OnBluetoothCyanContainer
import com.example.ui.theme.OnSecurityGreenContainer
import com.example.ui.theme.OnWarningAmberContainer
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityGreenContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer

@Composable
fun DashboardScreen(
    state: SecuritySystemState,
    onSelectDeviceClick: () -> Unit,
    onArmClick: () -> Unit,
    onDisarmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("dashboard_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header & Armed/Disarmed Status Pill
            item {
                DashboardHeaderBar(state = state)
            }

            // 2. Security Controls Card (ARM SYSTEM / DISARM SYSTEM)
            item {
                SecurityArmControlsCard(
                    isSystemArmed = state.isSystemArmed,
                    onArmClick = onArmClick,
                    onDisarmClick = onDisarmClick
                )
            }

            // 3. Always-On Bluetooth Classic HC-05 Connection Card
            item {
                BluetoothConnectionCard(
                    state = state,
                    onSelectDeviceClick = onSelectDeviceClick
                )
            }

            // 4. Live PIR Sensor & Last Motion Telemetry Card
            item {
                PirSensorTelemetryCard(state = state)
            }
        }
    }
}

@Composable
private fun DashboardHeaderBar(state: SecuritySystemState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = if (state.isAlarmAudioLooping) {
                                AlarmRedContainer
                            } else if (state.isSystemArmed) {
                                SecurityGreenContainer
                            } else {
                                WarningAmberContainer
                            },
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (state.isAlarmAudioLooping) {
                            AlarmRed
                        } else if (state.isSystemArmed) {
                            SecurityGreen
                        } else {
                            WarningAmber
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        modifier = Modifier.testTag("dashboard_app_title")
                    )
                    Text(
                        text = "HC-05 Bluetooth Security Alarm",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // System Armed / Disarmed Pill
        val badgeBg = if (state.isSystemArmed) SecurityGreenContainer else WarningAmberContainer
        val badgeBorder = if (state.isSystemArmed) SecurityGreen else WarningAmber
        val badgeText = if (state.isSystemArmed) {
            stringResource(R.string.status_security_armed)
        } else {
            stringResource(R.string.status_security_disarmed)
        }
        val badgeTextColor = if (state.isSystemArmed) OnSecurityGreenContainer else OnWarningAmberContainer

        Surface(
            color = badgeBg,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .border(1.dp, badgeBorder, RoundedCornerShape(50))
                .testTag("armed_status_badge")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (state.isSystemArmed) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = badgeText,
                    tint = badgeBorder,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = badgeTextColor
                )
            }
        }
    }
}

@Composable
private fun SecurityArmControlsCard(
    isSystemArmed: Boolean,
    onArmClick: () -> Unit,
    onDisarmClick: () -> Unit
) {
    val modeAccent = if (isSystemArmed) SecurityGreen else WarningAmber
    val modeBgTint = if (isSystemArmed) SecurityGreenContainer.copy(alpha = 0.25f) else WarningAmberContainer.copy(alpha = 0.25f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, modeAccent.copy(alpha = 0.28f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .background(modeBgTint)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SECURITY SYSTEM MODE",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isSystemArmed) {
                            stringResource(R.string.status_security_armed)
                        } else {
                            stringResource(R.string.status_security_disarmed)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = modeAccent,
                        modifier = Modifier.testTag("security_mode_status_text")
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = if (isSystemArmed) SecurityGreenContainer else WarningAmberContainer,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSystemArmed) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = modeAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onArmClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("arm_system_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSystemArmed) SecurityGreen else NavySurfaceElevated,
                        contentColor = if (isSystemArmed) Color.White else TextPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (isSystemArmed) 4.dp else 0.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_arm_system),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = onDisarmClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("disarm_system_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isSystemArmed) WarningAmber else NavySurfaceElevated,
                        contentColor = if (!isSystemArmed) Color.White else TextPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (!isSystemArmed) 4.dp else 0.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_disarm_system),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothConnectionCard(
    state: SecuritySystemState,
    onSelectDeviceClick: () -> Unit
) {
    val hasSelectedDevice = state.selectedDeviceAddress.isNotBlank()
    val isConnected = state.bluetoothState == BluetoothConnectionState.CONNECTED
    val isConnecting = state.bluetoothState == BluetoothConnectionState.CONNECTING ||
        state.bluetoothState == BluetoothConnectionState.RECONNECTING
    val isErrorOrOff = state.bluetoothState == BluetoothConnectionState.ERROR ||
        state.bluetoothState == BluetoothConnectionState.BLUETOOTH_OFF ||
        state.bluetoothState == BluetoothConnectionState.PERMISSION_REQUIRED ||
        state.bluetoothState == BluetoothConnectionState.UNSUPPORTED

    val statusColor = when {
        isConnected -> SecurityGreen
        isConnecting -> BluetoothCyan
        isErrorOrOff -> AlarmRed
        else -> TextSecondary
    }

    val statusHeadline = when {
        !hasSelectedDevice && state.bluetoothState == BluetoothConnectionState.DISCONNECTED ->
            "Select HC-05 to Auto-Connect"
        else -> state.bluetoothState.displayLabel
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NavyBorder, RoundedCornerShape(22.dp))
            .testTag("bluetooth_connection_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Title + Status + Select HC-05 Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isConnected -> Icons.Default.BluetoothConnected
                            isConnecting -> Icons.AutoMirrored.Filled.BluetoothSearching
                            isErrorOrOff -> Icons.Default.BluetoothDisabled
                            else -> Icons.Default.Bluetooth
                        },
                        contentDescription = "Bluetooth Status",
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "BLUETOOTH CONNECTION",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = statusHeadline,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = statusColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("bluetooth_status_text")
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSelectDeviceClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("select_hc05_device_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsBluetooth,
                        contentDescription = "Select Paired HC-05",
                        tint = BluetoothCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasSelectedDevice) "Change HC-05" else "Select HC-05",
                        style = MaterialTheme.typography.labelMedium,
                        color = BluetoothCyan,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            HorizontalDivider(color = NavyBorder.copy(alpha = 0.6f))

            // Device Name & MAC Address Telemetry
            Surface(
                color = NavySurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device Name:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.connectedDeviceName.ifBlank {
                                state.selectedDeviceName.ifBlank { "No HC-05 Selected" }
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("connected_device_name_text")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MAC Address:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.connectedDeviceAddress.takeIf { it != "—" }
                                ?: state.selectedDeviceAddress.ifBlank { "—" },
                            style = MaterialTheme.typography.labelMedium,
                            color = BluetoothCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("connected_device_address_text")
                        )
                    }
                }
            }

            // Connection Error / Status Banner
            AnimatedVisibility(visible = !state.lastBluetoothError.isNullOrBlank()) {
                Surface(
                    color = AlarmRedContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AlarmRed.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Bluetooth Alert",
                            tint = OnAlarmRedContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.lastBluetoothError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnAlarmRedContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Always-On Automatic Link Indicator (no manual connect/disconnect buttons needed)
            val linkBannerBg = when {
                isConnected -> SecurityGreenContainer.copy(alpha = 0.55f)
                isConnecting -> NavySurfaceElevated
                else -> NavySurfaceElevated
            }
            val linkBannerBorder = when {
                isConnected -> SecurityGreen.copy(alpha = 0.5f)
                isConnecting -> BluetoothCyan.copy(alpha = 0.5f)
                else -> NavyBorder
            }

            Surface(
                color = linkBannerBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, linkBannerBorder, RoundedCornerShape(12.dp))
                    .testTag("always_on_bluetooth_status_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        isConnecting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = BluetoothCyan
                            )
                        }
                        isConnected -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SecurityGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = BluetoothCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            isConnected ->
                                "Always-On Link Active • Stays connected while Armed or Disarmed until the app closes completely"
                            state.bluetoothState == BluetoothConnectionState.RECONNECTING ->
                                "Auto-reconnecting to HC-05 (Attempt #${state.reconnectAttemptCount.coerceAtLeast(1)})..."
                            state.bluetoothState == BluetoothConnectionState.CONNECTING ->
                                "Auto-connecting to selected HC-05 module..."
                            hasSelectedDevice ->
                                "Auto-connect enabled • Link stays active until app closes completely"
                            else ->
                                "Tap Select HC-05 above to auto-connect and stay linked until the app closes"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isConnected -> OnSecurityGreenContainer
                            isConnecting -> OnBluetoothCyanContainer
                            else -> TextSecondary
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PirSensorTelemetryCard(state: SecuritySystemState) {
    val isMotion = state.isPirMotionCurrentlyActive || state.isAlarmAudioLooping
    val accentColor = when {
        isMotion -> AlarmRed
        state.arduinoSystemReady -> SecurityGreen
        else -> BluetoothCyan
    }
    val statusBadgeBg = when {
        isMotion -> AlarmRedContainer
        state.arduinoSystemReady -> SecurityGreenContainer
        else -> NavySurfaceElevated
    }
    val statusLabel = when {
        state.isAlarmAudioLooping -> AlarmOperationalStatus.ALARM_ACTIVE.displayTitle
        state.isPirMotionCurrentlyActive && !state.isSystemArmed -> "MOTION DETECTED (DISARMED)"
        state.alarmOperationalStatus == AlarmOperationalStatus.ALARM_STOPPED -> AlarmOperationalStatus.ALARM_STOPPED.displayTitle
        state.arduinoSystemReady -> AlarmOperationalStatus.SYSTEM_READY.displayTitle
        else -> AlarmOperationalStatus.NORMAL_MONITORING.displayTitle
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NavyBorder, RoundedCornerShape(22.dp))
            .testTag("pir_sensor_telemetry_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color = statusBadgeBg, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "PIR Sensor Status",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PIR MOTION SENSOR",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("pir_sensor_status_text")
                    )
                }
            }

            Surface(
                color = NavySurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Arduino Status:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = if (state.arduinoSystemReady) "SYSTEM_READY" else "Standby / Monitoring",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (state.arduinoSystemReady) SecurityGreen else TextPrimary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last Motion:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = if (state.lastMotionDate == "—") {
                                state.lastMotionTime
                            } else {
                                "${state.lastMotionDate} • ${state.lastMotionTime}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.lastMotionDate == "—") TextSecondary else TextPrimary,
                            modifier = Modifier.testTag("last_motion_time_text")
                        )
                    }
                }
            }
        }
    }
}
