package com.example.service

import com.example.data.AlarmSoundOption

enum class BluetoothConnectionState(val displayLabel: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting to HC-05..."),
    CONNECTED("Connected (RFCOMM SPP)"),
    RECONNECTING("Auto-Reconnecting..."),
    BLUETOOTH_OFF("Bluetooth Switched Off"),
    PERMISSION_REQUIRED("Bluetooth Permission Required"),
    UNSUPPORTED("Bluetooth Hardware Unavailable"),
    ERROR("Connection Error")
}

enum class AlarmOperationalStatus(val displayTitle: String) {
    NORMAL_MONITORING("AREA SECURE • NO MOTION"),
    SYSTEM_READY("SYSTEM READY"),
    ALARM_ACTIVE("WARNING! MOTION DETECTED"),
    ALARM_STOPPED("ALARM STOPPED")
}

data class PairedBluetoothDevice(
    val name: String,
    val address: String,
    val isHc05Candidate: Boolean
)

data class SecuritySystemState(
    val bluetoothState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,
    val connectedDeviceName: String = "Not Connected",
    val connectedDeviceAddress: String = "—",
    val selectedDeviceName: String = "",
    val selectedDeviceAddress: String = "",
    val lastBluetoothError: String? = null,
    val pairedDevices: List<PairedBluetoothDevice> = emptyList(),
    val isSystemArmed: Boolean = true,
    val arduinoSystemReady: Boolean = false,
    val isPirMotionCurrentlyActive: Boolean = false,
    val alarmOperationalStatus: AlarmOperationalStatus = AlarmOperationalStatus.NORMAL_MONITORING,
    val isAlarmAudioLooping: Boolean = false,
    val isSuppressedUntilMotionEnded: Boolean = false,
    val lastMotionTime: String = "No motion recorded",
    val lastMotionDate: String = "—",
    val activeAlarmTriggerTime: String? = null,
    val alarmSound: AlarmSoundOption = AlarmSoundOption.TACTICAL_SIREN,
    val alarmVolume: Float = 1.0f,
    val vibrationEnabled: Boolean = true,
    val autoReconnectEnabled: Boolean = true,
    val highPriorityNotifications: Boolean = true,
    val reconnectAttemptCount: Int = 0
)
