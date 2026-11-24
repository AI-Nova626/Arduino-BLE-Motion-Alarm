package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.service.PairedBluetoothDevice
import com.example.ui.theme.BluetoothCyan
import com.example.ui.theme.BluetoothCyanContainer
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.OnBluetoothCyanContainer
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityGreenContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BluetoothDevicePickerDialog(
    pairedDevices: List<PairedBluetoothDevice>,
    selectedAddress: String,
    onSelectDevice: (PairedBluetoothDevice) -> Unit,
    onRefreshDevices: () -> Unit,
    onRequestPermissions: () -> Unit,
    hasBluetoothPermission: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SettingsBluetooth,
                    contentDescription = null,
                    tint = BluetoothCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Select Paired HC-05 Module",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose your paired HC-05 Bluetooth device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (!hasBluetoothPermission) {
                    Surface(
                        color = NavySurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Nearby Devices (Bluetooth Connect) permission is required to read paired HC-05 devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                            Button(
                                onClick = onRequestPermissions,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BluetoothCyan,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Grant Bluetooth Permission")
                            }
                        }
                    }
                } else if (pairedDevices.isEmpty()) {
                    Surface(
                        color = NavySurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No paired Bluetooth devices found.",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "1. Power on your Arduino UNO + HC-05 module.\n2. Open Android Bluetooth Settings and pair 'HC-05' using PIN 1234 or 0000.\n3. Tap Refresh below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pairedDevices, key = { it.address }) { device ->
                            val isSelected = device.address.equals(selectedAddress, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) BluetoothCyanContainer else NavySurfaceElevated
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) BluetoothCyan else NavyBorder,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onSelectDevice(device) }
                                    .padding(12.dp)
                                    .testTag("paired_device_${device.address}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (device.isHc05Candidate) {
                                            Icons.Default.BluetoothConnected
                                        } else {
                                            Icons.Default.Bluetooth
                                        },
                                        contentDescription = null,
                                        tint = if (device.isHc05Candidate) SecurityGreen else BluetoothCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = device.address,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OnBluetoothCyanContainer
                                        )
                                    }
                                }

                                if (device.isHc05Candidate) {
                                    Surface(
                                        color = SecurityGreenContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "HC-05",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SecurityGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefreshDevices,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = BluetoothCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Refresh",
                            style = MaterialTheme.typography.labelMedium,
                            color = BluetoothCyan
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Pair in OS Settings",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextMuted)
            }
        }
    )
}
