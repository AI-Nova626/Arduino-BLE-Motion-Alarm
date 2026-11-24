package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "smart_pir_security_settings"
)

enum class AlarmSoundOption(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val rawResId: Int?
) {
    TACTICAL_SIREN(
        id = "TACTICAL_SIREN",
        displayName = "Tactical Siren",
        subtitle = "Sweeping 650Hz–1450Hz dual-tone emergency siren",
        rawResId = R.raw.alarm_siren
    ),
    RAPID_PULSE(
        id = "RAPID_PULSE",
        displayName = "Rapid Pulse",
        subtitle = "High-frequency triple staccato intrusion alert",
        rawResId = R.raw.alarm_pulse
    ),
    SECURITY_KLAXON(
        id = "SECURITY_KLAXON",
        displayName = "Security Klaxon",
        subtitle = "Alternating 880Hz / 1320Hz industrial security horn",
        rawResId = R.raw.alarm_klaxon
    ),
    SYSTEM_DEFAULT(
        id = "SYSTEM_DEFAULT",
        displayName = "System Default Alarm",
        subtitle = "Uses Android device default alarm ringtone",
        rawResId = null
    );

    companion object {
        fun fromId(id: String?): AlarmSoundOption {
            return entries.firstOrNull { it.id == id } ?: TACTICAL_SIREN
        }
    }
}

data class SecurityUserSettings(
    val isSystemArmed: Boolean = true,
    val alarmSound: AlarmSoundOption = AlarmSoundOption.TACTICAL_SIREN,
    val alarmVolume: Float = 1.0f,
    val vibrationEnabled: Boolean = true,
    val autoReconnectEnabled: Boolean = true,
    val savedDeviceName: String = "",
    val savedDeviceAddress: String = "",
    val highPriorityNotifications: Boolean = true,
    val fullScreenAlarmOverlay: Boolean = true
)

class SecuritySettingsRepository(private val context: Context) {

    private object Keys {
        val IS_ARMED = booleanPreferencesKey("is_system_armed")
        val ALARM_SOUND = stringPreferencesKey("alarm_sound_id")
        val ALARM_VOLUME = floatPreferencesKey("alarm_volume")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect_enabled")
        val SAVED_DEVICE_NAME = stringPreferencesKey("saved_device_name")
        val SAVED_DEVICE_ADDRESS = stringPreferencesKey("saved_device_address")
        val HIGH_PRIORITY_NOTIFICATIONS = booleanPreferencesKey("high_priority_notifications")
        val FULL_SCREEN_OVERLAY = booleanPreferencesKey("full_screen_alarm_overlay")
    }

    val settingsFlow: Flow<SecurityUserSettings> = context.securityDataStore.data.map { prefs ->
        SecurityUserSettings(
            isSystemArmed = prefs[Keys.IS_ARMED] ?: true,
            alarmSound = AlarmSoundOption.fromId(prefs[Keys.ALARM_SOUND]),
            alarmVolume = (prefs[Keys.ALARM_VOLUME] ?: 1.0f).coerceIn(0.1f, 1.0f),
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            autoReconnectEnabled = prefs[Keys.AUTO_RECONNECT] ?: true,
            savedDeviceName = prefs[Keys.SAVED_DEVICE_NAME] ?: "",
            savedDeviceAddress = prefs[Keys.SAVED_DEVICE_ADDRESS] ?: "",
            highPriorityNotifications = prefs[Keys.HIGH_PRIORITY_NOTIFICATIONS] ?: true,
            fullScreenAlarmOverlay = prefs[Keys.FULL_SCREEN_OVERLAY] ?: true
        )
    }

    suspend fun setSystemArmed(armed: Boolean) {
        context.securityDataStore.edit { it[Keys.IS_ARMED] = armed }
    }

    suspend fun setAlarmSound(option: AlarmSoundOption) {
        context.securityDataStore.edit { it[Keys.ALARM_SOUND] = option.id }
    }

    suspend fun setAlarmVolume(volume: Float) {
        context.securityDataStore.edit { it[Keys.ALARM_VOLUME] = volume.coerceIn(0.1f, 1.0f) }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun setAutoReconnectEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun setSavedBluetoothDevice(name: String, address: String) {
        context.securityDataStore.edit {
            it[Keys.SAVED_DEVICE_NAME] = name
            it[Keys.SAVED_DEVICE_ADDRESS] = address
        }
    }

    suspend fun setHighPriorityNotifications(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.HIGH_PRIORITY_NOTIFICATIONS] = enabled }
    }

    suspend fun setFullScreenAlarmOverlay(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.FULL_SCREEN_OVERLAY] = enabled }
    }

    companion object {
        @Volatile
        private var INSTANCE: SecuritySettingsRepository? = null

        fun getInstance(context: Context): SecuritySettingsRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = SecuritySettingsRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
