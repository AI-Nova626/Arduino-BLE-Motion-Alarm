package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.AlarmOperationalStatus
import com.example.service.SecurityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Smart PIR Security`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Smart PIR Security", appName)
    }

    @Test
    fun `arduino protocol and instant readiness after stop alarm work as required`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SecurityEngine.getInstance(context)

        // Ensure armed and clean starting state
        engine.armSystem()
        engine.stopAlarmManually("TestInit")

        // 1. Receive SYSTEM_READY
        engine.processIncomingArduinoCommand("SYSTEM_READY")
        assertTrue(engine.systemState.value.arduinoSystemReady)
        assertEquals(
            AlarmOperationalStatus.SYSTEM_READY,
            engine.systemState.value.alarmOperationalStatus
        )

        // 2. Receive MOTION_DETECTED -> immediately activates continuous alarm
        engine.processIncomingArduinoCommand("MOTION_DETECTED")
        assertTrue(engine.systemState.value.isAlarmAudioLooping)
        assertTrue(engine.systemState.value.isPirMotionCurrentlyActive)
        assertEquals(
            AlarmOperationalStatus.ALARM_ACTIVE,
            engine.systemState.value.alarmOperationalStatus
        )

        // 3. Repeated MOTION_DETECTED while alarm is active keeps alarm playing smoothly
        engine.processIncomingArduinoCommand("MOTION_DETECTED")
        assertTrue(engine.systemState.value.isAlarmAudioLooping)

        // 4. User presses STOP ALARM -> immediately stops alarm and prepares for next motion detection with 0ms delay
        engine.stopAlarmManually("UserTest")
        assertFalse(engine.systemState.value.isAlarmAudioLooping)
        assertTrue(engine.systemState.value.isSystemArmed)
        assertEquals(
            AlarmOperationalStatus.ALARM_STOPPED,
            engine.systemState.value.alarmOperationalStatus
        )
        assertFalse(engine.systemState.value.isSuppressedUntilMotionEnded)

        // 5. Next MOTION_DETECTED event arrives immediately after STOP ALARM -> MUST ring alarm immediately!
        engine.processIncomingArduinoCommand("MOTION_DETECTED")
        assertTrue(engine.systemState.value.isAlarmAudioLooping)
        assertEquals(
            AlarmOperationalStatus.ALARM_ACTIVE,
            engine.systemState.value.alarmOperationalStatus
        )

        // 6. MOTION_ENDED arrives while alarm is playing -> MUST NOT stop the alarm automatically!
        engine.processIncomingArduinoCommand("MOTION_ENDED")
        assertTrue(engine.systemState.value.isAlarmAudioLooping)
        assertEquals(
            AlarmOperationalStatus.ALARM_ACTIVE,
            engine.systemState.value.alarmOperationalStatus
        )

        // 7. DISARM SYSTEM stops active alarm and disables future alerts
        engine.disarmSystem()
        assertFalse(engine.systemState.value.isSystemArmed)
        assertFalse(engine.systemState.value.isAlarmAudioLooping)

        engine.processIncomingArduinoCommand("MOTION_DETECTED")
        assertFalse(engine.systemState.value.isAlarmAudioLooping)

        // Re-arm for normal operation
        engine.armSystem()
    }

    @Test
    fun `selecting hc05 configures always-on target across armed and disarmed transitions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SecurityEngine.getInstance(context)

        val device = com.example.service.PairedBluetoothDevice(
            name = "HC-05",
            address = "00:14:03:05:59:10",
            isHc05Candidate = true
        )
        engine.selectBluetoothDevice(device, connectNow = false)

        assertEquals("HC-05", engine.systemState.value.selectedDeviceName)
        assertEquals("00:14:03:05:59:10", engine.systemState.value.selectedDeviceAddress)
        assertTrue(engine.systemState.value.autoReconnectEnabled)

        engine.armSystem()
        assertEquals("00:14:03:05:59:10", engine.systemState.value.selectedDeviceAddress)

        engine.disarmSystem()
        assertEquals("00:14:03:05:59:10", engine.systemState.value.selectedDeviceAddress)

        engine.shutdownOnAppClosed()
        assertFalse(engine.systemState.value.isAlarmAudioLooping)
        assertEquals("00:14:03:05:59:10", engine.systemState.value.selectedDeviceAddress)
    }

    @Test
    fun `motion ended resets stopped status and settings update state immediately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SecurityEngine.getInstance(context)

        engine.armSystem()
        engine.processIncomingArduinoCommand("SYSTEM_READY")
        engine.processIncomingArduinoCommand("MOTION_DETECTED")
        engine.stopAlarmManually("UserTest")
        assertEquals(AlarmOperationalStatus.ALARM_STOPPED, engine.systemState.value.alarmOperationalStatus)

        engine.processIncomingArduinoCommand("MOTION_ENDED")
        assertEquals(AlarmOperationalStatus.SYSTEM_READY, engine.systemState.value.alarmOperationalStatus)

        engine.updateAlarmVolume(0.55f)
        assertEquals(0.55f, engine.systemState.value.alarmVolume, 0.001f)

        engine.updateVibrationEnabled(false)
        assertFalse(engine.systemState.value.vibrationEnabled)

        engine.updateHighPriorityNotifications(false)
        assertFalse(engine.systemState.value.highPriorityNotifications)

        // Restore defaults
        engine.updateAlarmVolume(1.0f)
        engine.updateVibrationEnabled(true)
        engine.updateHighPriorityNotifications(true)
    }
}
