package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.AlarmSoundOption
import com.example.service.PairedBluetoothDevice
import com.example.service.SecurityEngine
import com.example.service.SecuritySystemState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SecurityTab(val route: String, val label: String) {
    DASHBOARD("dashboard", "Dashboard"),
    SETTINGS("settings", "Settings")
}

class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = SecurityEngine.getInstance(application)

    val systemState: StateFlow<SecuritySystemState> = engine.systemState

    private val _selectedTab = MutableStateFlow(SecurityTab.DASHBOARD)
    val selectedTab: StateFlow<SecurityTab> = _selectedTab.asStateFlow()

    private val _showDevicePickerDialog = MutableStateFlow(false)
    val showDevicePickerDialog: StateFlow<Boolean> = _showDevicePickerDialog.asStateFlow()

    fun selectTab(tab: SecurityTab) {
        _selectedTab.value = tab
    }

    fun openDevicePicker() {
        engine.refreshPairedDevices()
        _showDevicePickerDialog.value = true
    }

    fun closeDevicePicker() {
        _showDevicePickerDialog.value = false
    }

    fun refreshPairedDevices(): List<PairedBluetoothDevice> {
        return engine.refreshPairedDevices()
    }

    fun selectAndConnectDevice(device: PairedBluetoothDevice) {
        _showDevicePickerDialog.value = false
        engine.selectBluetoothDevice(device, connectNow = true)
    }

    fun onPermissionsOrLifecycleReady() {
        engine.onAppForegroundOrPermissionsReady()
    }

    fun armSystem() {
        engine.armSystem()
    }

    fun disarmSystem() {
        engine.disarmSystem()
    }

    fun stopAlarm(source: String = "Dashboard Button") {
        engine.stopAlarmManually(source)
    }

    fun triggerTestAlarm() {
        engine.triggerTestAlarm()
    }

    fun setAlarmSound(option: AlarmSoundOption) {
        engine.updateAlarmSound(option)
    }

    fun setAlarmVolume(volume: Float) {
        engine.updateAlarmVolume(volume)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        engine.updateVibrationEnabled(enabled)
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        engine.updateAutoReconnectEnabled(enabled)
    }

    fun setHighPriorityNotifications(enabled: Boolean) {
        engine.updateHighPriorityNotifications(enabled)
    }
}
