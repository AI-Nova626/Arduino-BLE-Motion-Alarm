package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * High-speed, low-latency Bluetooth Classic RFCOMM Serial Port Profile (SPP) connection manager
 * for communicating with the Arduino UNO via an HC-05 Bluetooth module at 9600 baud.
 *
 * Standard HC-05 SPP UUID: 00001101-0000-1000-8000-00805F9B34FB
 */
class ClassicBluetoothManager(
    context: Context,
    private val onStateChanged: (
        state: BluetoothConnectionState,
        deviceName: String?,
        deviceAddress: String?,
        errorMessage: String?,
        reconnectAttempt: Int
    ) -> Unit,
    private val onPairedDevicesUpdated: (List<PairedBluetoothDevice>) -> Unit,
    private val onCommandReceived: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    @Volatile
    private var activeSocket: BluetoothSocket? = null

    @Volatile
    private var activeInputStream: InputStream? = null

    private var readLoopJob: Job? = null
    private var reconnectJob: Job? = null

    @Volatile
    private var userRequestedDisconnect: Boolean = false

    @Volatile
    private var targetDeviceAddress: String = ""

    @Volatile
    private var targetDeviceName: String = ""

    @Volatile
    private var autoReconnectEnabled: Boolean = true

    @Volatile
    private var isReceiverRegistered: Boolean = false

    private val streamBufferLock = Any()
    private val streamBuilder = StringBuilder(256)

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )
                    when (state) {
                        BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                            scope.launch {
                                closeSocketInternal()
                                onStateChanged(
                                    BluetoothConnectionState.BLUETOOTH_OFF,
                                    targetDeviceName.ifBlank { null },
                                    targetDeviceAddress.ifBlank { null },
                                    "Bluetooth is switched off on this phone. Turn Bluetooth ON to monitor HC-05.",
                                    0
                                )
                            }
                        }

                        BluetoothAdapter.STATE_ON -> {
                            refreshPairedDevices()
                            if (!userRequestedDisconnect &&
                                autoReconnectEnabled &&
                                targetDeviceAddress.isNotBlank()
                            ) {
                                triggerAutoReconnect("Bluetooth turned back on")
                            } else {
                                onStateChanged(
                                    BluetoothConnectionState.DISCONNECTED,
                                    targetDeviceName.ifBlank { null },
                                    targetDeviceAddress.ifBlank { null },
                                    null,
                                    0
                                )
                            }
                        }
                    }
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    if (device != null &&
                        targetDeviceAddress.isNotBlank() &&
                        device.address.equals(targetDeviceAddress, ignoreCase = true)
                    ) {
                        Log.w(TAG, "ACL disconnected for target HC-05 ${device.address}")
                        if (!userRequestedDisconnect) {
                            scope.launch {
                                handleUnexpectedDisconnection(
                                    reason = "HC-05 link disconnected.",
                                    disconnectedSocket = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        registerBluetoothReceiver()
    }

    fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        // Always-on auto-reconnect while the app is open
        autoReconnectEnabled = true
    }

    fun ensureReceiverRegistered() {
        registerBluetoothReceiver()
    }

    fun setTargetDevice(name: String, address: String) {
        targetDeviceName = name
        targetDeviceAddress = address
    }

    /**
     * Immediately drains any stale unread bytes in the Bluetooth InputStream and clears the
     * token buffer when the user presses STOP ALARM so the system is instantaneously ready
     * for the next fresh motion signal from the HC-05.
     */
    fun flushPendingInput() {
        synchronized(streamBufferLock) {
            streamBuilder.setLength(0)
        }
        try {
            val stream = activeInputStream ?: return
            val availableBytes = stream.available()
            if (availableBytes > 0) {
                val discard = ByteArray(availableBytes.coerceAtMost(4096))
                stream.read(discard, 0, discard.size)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Queries bonded (paired) Bluetooth Classic devices and prioritizes HC-05 modules.
     */
    @SuppressLint("MissingPermission")
    fun refreshPairedDevices(): List<PairedBluetoothDevice> {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            onStateChanged(
                BluetoothConnectionState.UNSUPPORTED,
                null,
                null,
                "This Android device does not have Bluetooth hardware.",
                0
            )
            return emptyList()
        }

        if (!hasBluetoothConnectPermission()) {
            onStateChanged(
                BluetoothConnectionState.PERMISSION_REQUIRED,
                targetDeviceName.ifBlank { null },
                targetDeviceAddress.ifBlank { null },
                "Grant Nearby Devices (Bluetooth) permission to list paired HC-05 modules.",
                0
            )
            return emptyList()
        }

        if (!adapter.isEnabled) {
            onStateChanged(
                BluetoothConnectionState.BLUETOOTH_OFF,
                targetDeviceName.ifBlank { null },
                targetDeviceAddress.ifBlank { null },
                "Bluetooth is currently turned off.",
                0
            )
            return emptyList()
        }

        return try {
            val bonded = adapter.bondedDevices.orEmpty().map { device ->
                val devName = device.name ?: "Unknown Bluetooth Device"
                val devAddr = device.address ?: ""
                val isHc05 = devName.contains("HC-05", ignoreCase = true) ||
                    devName.contains("HC-06", ignoreCase = true) ||
                    devName.contains("HC05", ignoreCase = true) ||
                    devName.contains("Arduino", ignoreCase = true) ||
                    devName.contains("SPP", ignoreCase = true)
                PairedBluetoothDevice(
                    name = devName,
                    address = devAddr,
                    isHc05Candidate = isHc05
                )
            }.sortedWith(
                compareByDescending<PairedBluetoothDevice> { it.isHc05Candidate }
                    .thenBy { it.name }
            )

            onPairedDevicesUpdated(bonded)
            bonded
        } catch (se: SecurityException) {
            onStateChanged(
                BluetoothConnectionState.PERMISSION_REQUIRED,
                null,
                null,
                "Bluetooth permission is required to read paired devices.",
                0
            )
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query paired devices: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Connects to the specified (or auto-detected HC-05) paired Bluetooth device over RFCOMM SPP.
     * Prevents duplicate connections using connectionMutex.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String? = null, name: String? = null) {
        ensureReceiverRegistered()
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null

        scope.launch {
            connectionMutex.withLock {
                val adapter = bluetoothAdapter
                if (adapter == null) {
                    onStateChanged(
                        BluetoothConnectionState.UNSUPPORTED,
                        null,
                        null,
                        "Bluetooth Classic adapter unavailable on this device.",
                        0
                    )
                    return@withLock
                }

                if (!hasBluetoothConnectPermission()) {
                    onStateChanged(
                        BluetoothConnectionState.PERMISSION_REQUIRED,
                        targetDeviceName.ifBlank { null },
                        targetDeviceAddress.ifBlank { null },
                        "Bluetooth permission required before connecting to HC-05.",
                        0
                    )
                    return@withLock
                }

                if (!adapter.isEnabled) {
                    onStateChanged(
                        BluetoothConnectionState.BLUETOOTH_OFF,
                        targetDeviceName.ifBlank { null },
                        targetDeviceAddress.ifBlank { null },
                        "Bluetooth is turned off. Enable Bluetooth to connect to HC-05.",
                        0
                    )
                    return@withLock
                }

                var resolvedAddress = address?.takeIf { it.isNotBlank() } ?: targetDeviceAddress
                var resolvedName = name?.takeIf { it.isNotBlank() } ?: targetDeviceName

                if (resolvedAddress.isBlank()) {
                    val paired = refreshPairedDevices()
                    val candidate = paired.firstOrNull { it.isHc05Candidate } ?: paired.firstOrNull()
                    if (candidate != null) {
                        resolvedAddress = candidate.address
                        resolvedName = candidate.name
                    }
                }

                if (resolvedAddress.isBlank()) {
                    onStateChanged(
                        BluetoothConnectionState.ERROR,
                        null,
                        null,
                        "No paired HC-05 device selected. Pair HC-05 (PIN 1234 or 0000) in Android Bluetooth Settings and select it.",
                        0
                    )
                    return@withLock
                }

                val currentSocket = activeSocket
                if (currentSocket != null &&
                    currentSocket.isConnected &&
                    resolvedAddress.equals(targetDeviceAddress, ignoreCase = true)
                ) {
                    onStateChanged(
                        BluetoothConnectionState.CONNECTED,
                        resolvedName,
                        resolvedAddress,
                        null,
                        0
                    )
                    return@withLock
                }

                targetDeviceAddress = resolvedAddress
                targetDeviceName = resolvedName.ifBlank { "HC-05" }

                closeSocketInternal()

                onStateChanged(
                    BluetoothConnectionState.CONNECTING,
                    targetDeviceName,
                    targetDeviceAddress,
                    null,
                    0
                )

                val opened = establishRfcommConnection(
                    adapter = adapter,
                    address = targetDeviceAddress,
                    fallbackName = targetDeviceName,
                    isReconnectAttempt = false,
                    attemptNumber = 0
                )
                if (!opened && !userRequestedDisconnect && autoReconnectEnabled) {
                    triggerAutoReconnect("Retrying HC-05 connection")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun establishRfcommConnection(
        adapter: BluetoothAdapter,
        address: String,
        fallbackName: String,
        isReconnectAttempt: Boolean = false,
        attemptNumber: Int = 0
    ): Boolean {
        return try {
            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            val resolvedName = try {
                device.name?.takeIf { it.isNotBlank() } ?: fallbackName
            } catch (_: Exception) {
                fallbackName
            }
            targetDeviceName = resolvedName

            // Always cancel discovery first so RFCOMM handshake happens at maximum speed
            try {
                adapter.cancelDiscovery()
            } catch (_: Exception) {
            }

            // HC-05 modules connect fastest over insecure RFCOMM SPP; fallback to secure SPP and channel 1 reflection
            var socket: BluetoothSocket? = null
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(HC05_SPP_UUID)
                socket.connect()
            } catch (firstEx: IOException) {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
                try {
                    socket = device.createRfcommSocketToServiceRecord(HC05_SPP_UUID)
                    socket.connect()
                } catch (secondEx: IOException) {
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                    val createRfcommMethod = device.javaClass.getMethod(
                        "createRfcommSocket",
                        Int::class.javaPrimitiveType
                    )
                    socket = createRfcommMethod.invoke(device, 1) as BluetoothSocket
                    socket.connect()
                }
            }

            activeSocket = socket
            activeInputStream = socket?.inputStream
            synchronized(streamBufferLock) {
                streamBuilder.setLength(0)
            }

            onStateChanged(
                BluetoothConnectionState.CONNECTED,
                targetDeviceName,
                targetDeviceAddress,
                null,
                0
            )
            startUltraFastReadLoop(socket!!)
            true
        } catch (se: SecurityException) {
            onStateChanged(
                BluetoothConnectionState.PERMISSION_REQUIRED,
                targetDeviceName,
                targetDeviceAddress,
                "Bluetooth permission denied while opening RFCOMM socket.",
                0
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "RFCOMM connection error to $address: ${e.message}")
            closeSocketInternal()
            if (isReconnectAttempt || autoReconnectEnabled) {
                onStateChanged(
                    BluetoothConnectionState.RECONNECTING,
                    targetDeviceName,
                    targetDeviceAddress,
                    null,
                    attemptNumber.coerceAtLeast(1)
                )
            } else {
                onStateChanged(
                    BluetoothConnectionState.ERROR,
                    targetDeviceName,
                    targetDeviceAddress,
                    "Could not connect to $targetDeviceName ($address). Verify HC-05 is powered and within range.",
                    0
                )
            }
            false
        }
    }

    /**
     * Ultra-low-latency direct byte reader from HC-05 InputStream (9600 baud).
     * Dispatches commands ("MOTION_DETECTED", "MOTION_ENDED", "SYSTEM_READY") the exact
     * millisecond the bytes arrive — even before a newline arrives, or on newline boundaries.
     */
    private fun startUltraFastReadLoop(socket: BluetoothSocket) {
        readLoopJob?.cancel()
        readLoopJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(128)
            try {
                val input = socket.inputStream
                activeInputStream = input

                while (isActive && socket.isConnected) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead == 0) continue

                    val matchedCommands = mutableListOf<String>()
                    synchronized(streamBufferLock) {
                        for (i in 0 until bytesRead) {
                            val ch = buffer[i].toInt().toChar()
                            if (ch == '\n' || ch == '\r') {
                                if (streamBuilder.isNotEmpty()) {
                                    val line = streamBuilder.toString().trim()
                                    streamBuilder.setLength(0)
                                    if (line.isNotEmpty()) {
                                        matchedCommands.add(line)
                                    }
                                }
                            } else {
                                streamBuilder.append(ch)
                                // Also check immediately if a known command token has completed
                                // even before '\n' arrives for zero-latency triggering!
                                val currentUpper = streamBuilder.toString().uppercase(Locale.US)
                                when {
                                    currentUpper.endsWith("MOTION_DETECTED") -> {
                                        matchedCommands.add("MOTION_DETECTED")
                                        streamBuilder.setLength(0)
                                    }
                                    currentUpper.endsWith("MOTION_ENDED") -> {
                                        matchedCommands.add("MOTION_ENDED")
                                        streamBuilder.setLength(0)
                                    }
                                    currentUpper.endsWith("SYSTEM_READY") -> {
                                        matchedCommands.add("SYSTEM_READY")
                                        streamBuilder.setLength(0)
                                    }
                                    streamBuilder.length > 256 -> {
                                        streamBuilder.delete(0, 128)
                                    }
                                }
                            }
                        }
                    }

                    for (cmd in matchedCommands) {
                        onCommandReceived(cmd)
                    }
                }

                if (!userRequestedDisconnect) {
                    handleUnexpectedDisconnection(
                        reason = "HC-05 stream closed by remote device.",
                        disconnectedSocket = socket
                    )
                }
            } catch (e: IOException) {
                if (!userRequestedDisconnect) {
                    handleUnexpectedDisconnection(
                        reason = "Connection lost with $targetDeviceName.",
                        disconnectedSocket = socket
                    )
                }
            } catch (e: Exception) {
                if (!userRequestedDisconnect) {
                    handleUnexpectedDisconnection(
                        reason = "Bluetooth read error: ${e.message}",
                        disconnectedSocket = socket
                    )
                }
            }
        }
    }

    private suspend fun handleUnexpectedDisconnection(
        reason: String,
        disconnectedSocket: BluetoothSocket?
    ) {
        val shouldTriggerReconnect = connectionMutex.withLock {
            if (disconnectedSocket != null && activeSocket != null && activeSocket !== disconnectedSocket) {
                // A newer socket is already established; ignore stale exit from an older socket
                return@withLock false
            }
            if (disconnectedSocket == null && activeSocket?.isConnected == true) {
                // Ignore stale ACL broadcast if a fresh socket is already connected
                return@withLock false
            }
            closeSocketInternal()
            true
        }

        if (!shouldTriggerReconnect) return

        if (bluetoothAdapter?.isEnabled == false) {
            onStateChanged(
                BluetoothConnectionState.BLUETOOTH_OFF,
                targetDeviceName.ifBlank { null },
                targetDeviceAddress.ifBlank { null },
                "Bluetooth was turned off. Turn ON Bluetooth to auto-connect to HC-05.",
                0
            )
            return
        }

        if (autoReconnectEnabled && !userRequestedDisconnect && targetDeviceAddress.isNotBlank()) {
            triggerAutoReconnect(reason)
        } else {
            onStateChanged(
                BluetoothConnectionState.ERROR,
                targetDeviceName.ifBlank { null },
                targetDeviceAddress.ifBlank { null },
                reason,
                0
            )
        }
    }

    private fun triggerAutoReconnect(reason: String) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var attempt = 1
            while (isActive &&
                !userRequestedDisconnect &&
                autoReconnectEnabled &&
                targetDeviceAddress.isNotBlank()
            ) {
                val adapter = bluetoothAdapter
                if (adapter == null) {
                    break
                }
                if (!adapter.isEnabled) {
                    onStateChanged(
                        BluetoothConnectionState.BLUETOOTH_OFF,
                        targetDeviceName.ifBlank { null },
                        targetDeviceAddress.ifBlank { null },
                        "Bluetooth is turned off. Turn ON Bluetooth to auto-connect to HC-05.",
                        0
                    )
                    break
                }
                if (!hasBluetoothConnectPermission()) {
                    onStateChanged(
                        BluetoothConnectionState.PERMISSION_REQUIRED,
                        targetDeviceName.ifBlank { null },
                        targetDeviceAddress.ifBlank { null },
                        "Bluetooth permission required before connecting to HC-05.",
                        0
                    )
                    break
                }
                if (activeSocket?.isConnected == true) {
                    break
                }

                onStateChanged(
                    BluetoothConnectionState.RECONNECTING,
                    targetDeviceName,
                    targetDeviceAddress,
                    null,
                    attempt
                )

                // Fast 800ms reconnect interval for always-on recovery
                delay(800L)

                if (userRequestedDisconnect || !autoReconnectEnabled) break

                val connected = connectionMutex.withLock {
                    if (activeSocket?.isConnected == true) {
                        true
                    } else {
                        establishRfcommConnection(
                            adapter = adapter,
                            address = targetDeviceAddress,
                            fallbackName = targetDeviceName,
                            isReconnectAttempt = true,
                            attemptNumber = attempt
                        )
                    }
                }

                if (connected) {
                    break
                }
                attempt++
            }
        }
    }

    /**
     * Explicitly disconnects from the HC-05 Bluetooth device and cancels auto-reconnect.
     */
    fun disconnect() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null

        scope.launch {
            connectionMutex.withLock {
                closeSocketInternal()
                onStateChanged(
                    BluetoothConnectionState.DISCONNECTED,
                    targetDeviceName.ifBlank { null },
                    targetDeviceAddress.ifBlank { null },
                    null,
                    0
                )
            }
        }
    }

    private suspend fun closeSocketInternal() {
        val jobToCancel = readLoopJob
        readLoopJob = null
        activeInputStream = null
        synchronized(streamBufferLock) {
            streamBuilder.setLength(0)
        }
        try {
            activeSocket?.close()
        } catch (_: Exception) {
        } finally {
            activeSocket = null
        }
        if (jobToCancel != null && jobToCancel != kotlin.coroutines.coroutineContext[Job]) {
            try {
                jobToCancel.cancelAndJoin()
            } catch (_: Exception) {
            }
        }
    }

    private fun registerBluetoothReceiver() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            ContextCompat.registerReceiver(
                appContext,
                bluetoothStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register Bluetooth receiver: ${e.message}")
        }
    }

    fun release() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        scope.launch {
            closeSocketInternal()
        }
        if (isReceiverRegistered) {
            try {
                appContext.unregisterReceiver(bluetoothStateReceiver)
            } catch (_: Exception) {
            }
            isReceiverRegistered = false
        }
    }

    companion object {
        private const val TAG = "ClassicBluetoothMgr"
        val HC05_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
