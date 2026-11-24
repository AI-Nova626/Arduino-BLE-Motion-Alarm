package com.example.service

import android.content.Context
import com.example.data.AlarmSoundOption
import com.example.data.SecuritySettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Central application-scoped security engine coordinating:
 * - Instant HC-05 Bluetooth Classic RFCOMM SPP command processing
 * - Zero-latency continuous looping alarm playback & vibration
 * - Immediate readiness for the next motion detection as soon as STOP ALARM is pressed
 */
class SecurityEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsRepository = SecuritySettingsRepository.getInstance(appContext)
    val alarmController = AlarmAudioVibrationController(appContext)

    private val _systemState = MutableStateFlow(SecuritySystemState())
    val systemState: StateFlow<SecuritySystemState> = _systemState.asStateFlow()

    @Volatile
    private var hasAutoConnectedSavedDevice: Boolean = false

    private val bluetoothManager = ClassicBluetoothManager(
        context = appContext,
        onStateChanged = { btState, devName, devAddr, errorMsg, reconnectAttempt ->
            handleBluetoothStateChanged(btState, devName, devAddr, errorMsg, reconnectAttempt)
        },
        onPairedDevicesUpdated = { paired ->
            _systemState.update { current ->
                current.copy(pairedDevices = paired)
            }
        },
        onCommandReceived = { rawCommand ->
            processIncomingArduinoCommand(rawCommand)
        }
    )

    init {
        scope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                bluetoothManager.setAutoReconnect(true)
                if (settings.savedDeviceAddress.isNotBlank()) {
                    bluetoothManager.setTargetDevice(
                        settings.savedDeviceName,
                        settings.savedDeviceAddress
                    )
                }
                alarmController.updateVolume(settings.alarmVolume)
                alarmController.preWarmPlayer(settings.alarmSound, settings.alarmVolume)

                _systemState.update { current ->
                    current.copy(
                        isSystemArmed = settings.isSystemArmed,
                        alarmSound = settings.alarmSound,
                        alarmVolume = settings.alarmVolume,
                        vibrationEnabled = settings.vibrationEnabled,
                        autoReconnectEnabled = true,
                        selectedDeviceName = settings.savedDeviceName.ifBlank { current.selectedDeviceName },
                        selectedDeviceAddress = settings.savedDeviceAddress.ifBlank { current.selectedDeviceAddress },
                        highPriorityNotifications = settings.highPriorityNotifications
                    )
                }

                if (settings.savedDeviceAddress.isNotBlank() &&
                    !hasAutoConnectedSavedDevice &&
                    bluetoothManager.hasBluetoothConnectPermission()
                ) {
                    hasAutoConnectedSavedDevice = true
                    val currentBtState = _systemState.value.bluetoothState
                    if (currentBtState != BluetoothConnectionState.CONNECTED &&
                        currentBtState != BluetoothConnectionState.CONNECTING &&
                        currentBtState != BluetoothConnectionState.RECONNECTING
                    ) {
                        connectBluetooth(settings.savedDeviceAddress, settings.savedDeviceName)
                    }
                }

                PirSecurityForegroundService.syncNotificationIfRunning(appContext)
            }
        }
    }

    private fun handleBluetoothStateChanged(
        btState: BluetoothConnectionState,
        devName: String?,
        devAddr: String?,
        errorMsg: String?,
        reconnectAttempt: Int
    ) {
        _systemState.update { current ->
            val updatedName = when (btState) {
                BluetoothConnectionState.CONNECTED,
                BluetoothConnectionState.CONNECTING,
                BluetoothConnectionState.RECONNECTING -> devName ?: current.selectedDeviceName.ifBlank { "HC-05" }
                else -> devName ?: current.selectedDeviceName.ifBlank { "Not Connected" }
            }
            val updatedAddr = devAddr ?: current.selectedDeviceAddress.ifBlank { "—" }

            current.copy(
                bluetoothState = btState,
                connectedDeviceName = updatedName,
                connectedDeviceAddress = updatedAddr,
                lastBluetoothError = errorMsg,
                reconnectAttemptCount = reconnectAttempt,
                arduinoSystemReady = if (btState == BluetoothConnectionState.CONNECTED) {
                    current.arduinoSystemReady
                } else {
                    false
                }
            )
        }
        PirSecurityForegroundService.syncNotificationIfRunning(appContext)
    }

    /**
     * Processes commands from the Arduino UNO over HC-05 SPP (9600 baud) with zero latency:
     * 1. "SYSTEM_READY"
     * 2. "MOTION_DETECTED"
     * 3. "MOTION_ENDED"
     */
    fun processIncomingArduinoCommand(rawLine: String) {
        val normalized = rawLine.trim().uppercase(Locale.US)
        if (normalized.isEmpty()) return

        when {
            normalized == "SYSTEM_READY" || normalized.contains("SYSTEM_READY") -> {
                _systemState.update { current ->
                    val nextStatus = if (current.isAlarmAudioLooping) {
                        AlarmOperationalStatus.ALARM_ACTIVE
                    } else {
                        AlarmOperationalStatus.SYSTEM_READY
                    }
                    current.copy(
                        arduinoSystemReady = true,
                        alarmOperationalStatus = nextStatus
                    )
                }
                PirSecurityForegroundService.syncNotificationIfRunning(appContext)
            }

            normalized == "MOTION_DETECTED" || normalized.contains("MOTION_DETECTED") -> {
                handleMotionDetectedCommand()
            }

            normalized == "MOTION_ENDED" || normalized.contains("MOTION_ENDED") -> {
                handleMotionEndedCommand()
            }
        }
    }

    private fun handleMotionDetectedCommand() {
        val current = _systemState.value
        val nowMs = System.currentTimeMillis()
        val dateStr = formatDate(nowMs)
        val timeStr = formatTime(nowMs)

        // If the alarm is already actively ringing, keep it playing smoothly without restarting the audio track
        if (current.isAlarmAudioLooping) {
            if (!current.isPirMotionCurrentlyActive) {
                _systemState.update { it.copy(isPirMotionCurrentlyActive = true) }
            }
            return
        }

        // If the system is DISARMED, do not ring the alarm
        if (!current.isSystemArmed) {
            _systemState.update {
                it.copy(
                    isPirMotionCurrentlyActive = true,
                    lastMotionTime = timeStr,
                    lastMotionDate = dateStr
                )
            }
            return
        }

        // System is ARMED and not currently ringing -> IMMEDIATELY RING ALARM (0ms delay)!
        alarmController.startContinuousAlarm(
            soundOption = current.alarmSound,
            volume = current.alarmVolume,
            enableVibration = current.vibrationEnabled
        )

        _systemState.update { state ->
            state.copy(
                isPirMotionCurrentlyActive = true,
                alarmOperationalStatus = AlarmOperationalStatus.ALARM_ACTIVE,
                isAlarmAudioLooping = true,
                isSuppressedUntilMotionEnded = false,
                lastMotionTime = timeStr,
                lastMotionDate = dateStr,
                activeAlarmTriggerTime = "$dateStr • $timeStr"
            )
        }

        PirSecurityForegroundService.ensureServiceStarted(appContext)

        if (current.highPriorityNotifications) {
            PirSecurityForegroundService.postHighPriorityAlarmNotification(
                context = appContext,
                detectionTime = timeStr,
                deviceName = current.connectedDeviceName,
                enableFullScreen = true
            )
        }
        PirSecurityForegroundService.syncNotificationIfRunning(appContext)
    }

    private fun handleMotionEndedCommand() {
        _systemState.update { state ->
            val nextStatus = when {
                state.isAlarmAudioLooping -> AlarmOperationalStatus.ALARM_ACTIVE
                state.arduinoSystemReady -> AlarmOperationalStatus.SYSTEM_READY
                else -> AlarmOperationalStatus.NORMAL_MONITORING
            }
            state.copy(
                isPirMotionCurrentlyActive = false,
                isSuppressedUntilMotionEnded = false,
                alarmOperationalStatus = nextStatus
            )
        }
        PirSecurityForegroundService.syncNotificationIfRunning(appContext)
    }

    /**
     * When STOP ALARM is pressed:
     * 1. Immediately stops the alarm audio and rewinds the pre-warmed player to 0ms.
     * 2. Immediately flushes any old unread bytes sitting in the HC-05 Bluetooth buffer from the previous trigger.
     * 3. Immediately resets motion state so the whole system is 100% ready for the next motion detection with zero delay!
     */
    fun stopAlarmManually(source: String = "Dashboard") {
        alarmController.stopAlarmImmediately()
        bluetoothManager.flushPendingInput()

        val stopTimeStr = formatTime(System.currentTimeMillis())

        _systemState.update { state ->
            state.copy(
                alarmOperationalStatus = AlarmOperationalStatus.ALARM_STOPPED,
                isAlarmAudioLooping = false,
                isPirMotionCurrentlyActive = false,
                isSuppressedUntilMotionEnded = false
            )
        }

        PirSecurityForegroundService.onAlarmStoppedNotificationUpdate(
            context = appContext,
            stoppedAtTime = stopTimeStr
        )
        PirSecurityForegroundService.syncNotificationIfRunning(appContext)
    }

    fun armSystem() {
        bluetoothManager.flushPendingInput()
        scope.launch {
            settingsRepository.setSystemArmed(true)
        }
        _systemState.update { state ->
            val nextStatus = if (state.arduinoSystemReady) {
                AlarmOperationalStatus.SYSTEM_READY
            } else {
                AlarmOperationalStatus.NORMAL_MONITORING
            }
            state.copy(
                isSystemArmed = true,
                alarmOperationalStatus = if (state.isAlarmAudioLooping) {
                    AlarmOperationalStatus.ALARM_ACTIVE
                } else {
                    nextStatus
                },
                isPirMotionCurrentlyActive = false,
                isSuppressedUntilMotionEnded = false
            )
        }
        PirSecurityForegroundService.ensureServiceStarted(appContext)
        PirSecurityForegroundService.syncNotificationIfRunning(appContext)
    }

    fun disarmSystem() {
        val wasAlarmPlaying = _systemState.value.isAlarmAudioLooping
        alarmController.stopAlarmImmediately()
        bluetoothManager.flushPendingInput()

        scope.launch {
            settingsRepository.setSystemArmed(false)
        }

        if (wasAlarmPlaying) {
            PirSecurityForegroundService.cancelAlarmAlertNotification(appContext)
        }

        _systemState.update { state ->
            state.copy(
                isSystemArmed = false,
                alarmOperationalStatus = AlarmOperationalStatus.NORMAL_MONITORING,
                isAlarmAudioLooping = false,
                isSuppressedUntilMotionEnded = false,
                isPirMotionCurrentlyActive = false
            )
        }
        PirSecurityForegroundService.syncNotificationIfRunning(appContext)
    }

    fun triggerTestAlarm() {
        val current = _systemState.value
        if (!current.isSystemArmed) {
            armSystem()
        }
        handleMotionDetectedCommand()
    }

    fun refreshPairedDevices(): List<PairedBluetoothDevice> {
        return bluetoothManager.refreshPairedDevices()
    }

    fun onAppForegroundOrPermissionsReady() {
        bluetoothManager.ensureReceiverRegistered()
        bluetoothManager.setAutoReconnect(true)
        if (!bluetoothManager.hasBluetoothConnectPermission()) return

        bluetoothManager.refreshPairedDevices()
        val current = _systemState.value
        val savedAddr = current.selectedDeviceAddress
        if (savedAddr.isNotBlank() &&
            current.bluetoothState != BluetoothConnectionState.CONNECTED &&
            current.bluetoothState != BluetoothConnectionState.CONNECTING &&
            current.bluetoothState != BluetoothConnectionState.RECONNECTING
        ) {
            hasAutoConnectedSavedDevice = true
            connectBluetooth(savedAddr, current.selectedDeviceName)
        }
    }

    fun selectBluetoothDevice(device: PairedBluetoothDevice, connectNow: Boolean = true) {
        hasAutoConnectedSavedDevice = true
        scope.launch {
            settingsRepository.setSavedBluetoothDevice(device.name, device.address)
        }
        bluetoothManager.setAutoReconnect(true)
        bluetoothManager.setTargetDevice(device.name, device.address)
        _systemState.update {
            it.copy(
                selectedDeviceName = device.name,
                selectedDeviceAddress = device.address,
                lastBluetoothError = null
            )
        }
        if (connectNow) {
            connectBluetooth(device.address, device.name)
        }
    }

    fun connectBluetooth(address: String? = null, name: String? = null) {
        PirSecurityForegroundService.ensureServiceStarted(appContext)
        bluetoothManager.connectToDevice(address, name)
    }

    fun shutdownOnAppClosed() {
        hasAutoConnectedSavedDevice = false
        alarmController.stopAlarmImmediately()
        PirSecurityForegroundService.cancelAlarmAlertNotification(appContext)
        bluetoothManager.disconnect()
        _systemState.update { state ->
            state.copy(
                isAlarmAudioLooping = false,
                isPirMotionCurrentlyActive = false,
                isSuppressedUntilMotionEnded = false
            )
        }
    }

    fun updateAlarmSound(option: AlarmSoundOption) {
        _systemState.update { it.copy(alarmSound = option) }
        scope.launch {
            settingsRepository.setAlarmSound(option)
        }
        if (_systemState.value.isAlarmAudioLooping) {
            alarmController.stopAlarmImmediately()
            alarmController.startContinuousAlarm(
                soundOption = option,
                volume = _systemState.value.alarmVolume,
                enableVibration = _systemState.value.vibrationEnabled
            )
        } else {
            alarmController.playPreview(option, _systemState.value.alarmVolume)
        }
    }

    fun updateAlarmVolume(volume: Float) {
        val clamped = volume.coerceIn(0.1f, 1.0f)
        _systemState.update { it.copy(alarmVolume = clamped) }
        alarmController.updateVolume(clamped)
        scope.launch {
            settingsRepository.setAlarmVolume(clamped)
        }
    }

    fun updateVibrationEnabled(enabled: Boolean) {
        _systemState.update { it.copy(vibrationEnabled = enabled) }
        scope.launch {
            settingsRepository.setVibrationEnabled(enabled)
        }
        if (!enabled) {
            alarmController.stopVibration()
        } else if (_systemState.value.isAlarmAudioLooping) {
            alarmController.startVibrationOnly()
        }
    }

    fun updateAutoReconnectEnabled(enabled: Boolean) {
        bluetoothManager.setAutoReconnect(enabled)
        scope.launch {
            settingsRepository.setAutoReconnectEnabled(enabled)
        }
    }

    fun updateHighPriorityNotifications(enabled: Boolean) {
        _systemState.update { it.copy(highPriorityNotifications = enabled) }
        scope.launch {
            settingsRepository.setHighPriorityNotifications(enabled)
        }
    }

    private fun formatDate(timestampMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMs))
    }

    private fun formatTime(timestampMs: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestampMs))
    }

    companion object {
        @Volatile
        private var INSTANCE: SecurityEngine? = null

        fun getInstance(context: Context): SecurityEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = SecurityEngine(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
