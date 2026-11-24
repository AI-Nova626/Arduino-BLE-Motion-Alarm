package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Android Foreground Service ensuring continuous background Bluetooth Classic (HC-05)
 * serial monitoring and continuous alarm playback when the screen is off or app is backgrounded.
 */
class PirSecurityForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannels(this)
        val engine = SecurityEngine.getInstance(this)

        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                engine.stopAlarmManually("Notification Action")
            }
            ACTION_ARM_SYSTEM -> {
                engine.armSystem()
            }
            ACTION_DISARM_SYSTEM -> {
                engine.disarmSystem()
            }
            ACTION_SHUTDOWN_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isServiceRunning = false
                return START_NOT_STICKY
            }
        }

        val notification = buildForegroundMonitorNotification(this, engine.systemState.value)
        val hasBtPermission = hasBluetoothPermission(this)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasBtPermission) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }
        } else {
            0
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID_MONITOR,
                    notification,
                    fgsType
                )
            } else {
                startForeground(NOTIFICATION_ID_MONITOR, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground start fallback: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID_MONITOR,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID_MONITOR, notification)
                }
            } catch (inner: Exception) {
                Log.e(TAG, "Unable to call startForeground: ${inner.message}")
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        SecurityEngine.getInstance(this).shutdownOnAppClosed()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isServiceRunning = false
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        SecurityEngine.getInstance(this).shutdownOnAppClosed()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PirSecurityFGS"

        const val CHANNEL_MONITOR_ID = "smart_pir_security_monitor_channel"
        const val CHANNEL_ALARM_ID = "smart_pir_security_alarm_channel_v2"

        const val NOTIFICATION_ID_MONITOR = 1001
        const val NOTIFICATION_ID_ALARM = 2002

        const val ACTION_START_MONITOR = "com.example.action.START_MONITOR"
        const val ACTION_STOP_ALARM = "com.example.action.STOP_ALARM"
        const val ACTION_ARM_SYSTEM = "com.example.action.ARM_SYSTEM"
        const val ACTION_DISARM_SYSTEM = "com.example.action.DISARM_SYSTEM"
        const val ACTION_SHUTDOWN_SERVICE = "com.example.action.SHUTDOWN_SERVICE"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun ensureServiceStarted(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, PirSecurityForegroundService::class.java).apply {
                action = ACTION_START_MONITOR
            }
            try {
                ContextCompat.startForegroundService(appContext, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start foreground service: ${e.message}")
            }
        }

        fun syncNotificationIfRunning(context: Context) {
            if (!isServiceRunning) return
            val appContext = context.applicationContext
            if (!canPostNotifications(appContext)) return
            try {
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val state = SecurityEngine.getInstance(appContext).systemState.value
                nm.notify(
                    NOTIFICATION_ID_MONITOR,
                    buildForegroundMonitorNotification(appContext, state)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update monitor notification: ${e.message}")
            }
        }

        fun createNotificationChannels(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_ID,
                "PIR Security Background Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status for HC-05 Bluetooth Classic connection and Armed state"
                setShowBadge(false)
            }

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                "Security Alert Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority emergency alerts when PIR motion is detected"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Sound is played continuously by AlarmAudioVibrationController so STOP ALARM stops it cleanly
                setSound(null, null)
            }

            nm.createNotificationChannel(monitorChannel)
            nm.createNotificationChannel(alarmChannel)
        }

        private fun buildForegroundMonitorNotification(
            context: Context,
            state: SecuritySystemState
        ): Notification {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                10,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val armedText = if (state.isSystemArmed) "Security Armed" else "Security Disarmed"
            val btText = when (state.bluetoothState) {
                BluetoothConnectionState.CONNECTED -> "Connected to ${state.connectedDeviceName}"
                BluetoothConnectionState.RECONNECTING -> "Reconnecting to ${state.connectedDeviceName}..."
                BluetoothConnectionState.CONNECTING -> "Connecting to ${state.connectedDeviceName}..."
                else -> "HC-05 Disconnected"
            }

            val title = if (state.isAlarmAudioLooping) {
                "WARNING! MOTION DETECTED"
            } else {
                "Smart PIR Security • $armedText"
            }

            val content = if (state.isAlarmAudioLooping) {
                "Continuous alarm active! Tap STOP ALARM to silence."
            } else {
                btText
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_MONITOR_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            if (state.isAlarmAudioLooping) {
                val stopIntent = Intent(context, AlarmActionReceiver::class.java).apply {
                    action = ACTION_STOP_ALARM
                }
                val stopPendingIntent = PendingIntent.getBroadcast(
                    context,
                    20,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    context.getString(R.string.btn_stop_alarm),
                    stopPendingIntent
                )
            } else if (state.isSystemArmed) {
                val disarmIntent = Intent(context, AlarmActionReceiver::class.java).apply {
                    action = ACTION_DISARM_SYSTEM
                }
                val disarmPending = PendingIntent.getBroadcast(
                    context,
                    21,
                    disarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    android.R.drawable.ic_lock_power_off,
                    context.getString(R.string.btn_disarm_system),
                    disarmPending
                )
            } else {
                val armIntent = Intent(context, AlarmActionReceiver::class.java).apply {
                    action = ACTION_ARM_SYSTEM
                }
                val armPending = PendingIntent.getBroadcast(
                    context,
                    22,
                    armIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    android.R.drawable.ic_lock_lock,
                    context.getString(R.string.btn_arm_system),
                    armPending
                )
            }

            return builder.build()
        }

        /**
         * Posts the high-priority Security Alert notification with:
         * - Title: "Security Alert"
         * - Text: "Motion detected by your PIR security sensor!"
         * - Action: "STOP ALARM"
         * - Full-screen intent to display the emergency alarm UI when permitted.
         */
        fun postHighPriorityAlarmNotification(
            context: Context,
            detectionTime: String,
            deviceName: String,
            enableFullScreen: Boolean
        ) {
            createNotificationChannels(context)
            if (!canPostNotifications(context)) return

            val openAlarmIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("EXTRA_OPEN_ALARM_SCREEN", true)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                30,
                openAlarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopAlarmIntent = Intent(context, AlarmActionReceiver::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            val stopAlarmPendingIntent = PendingIntent.getBroadcast(
                context,
                31,
                stopAlarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ALARM_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(context.getString(R.string.notification_alert_title))
                .setContentText(context.getString(R.string.notification_alert_body))
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "${context.getString(R.string.notification_alert_body)}\n" +
                            "Detected at $detectionTime via $deviceName. Alarm will continue until manually stopped."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPendingIntent)
                .addAction(
                    android.R.drawable.ic_delete,
                    context.getString(R.string.btn_stop_alarm),
                    stopAlarmPendingIntent
                )

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val allowFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                enableFullScreen && nm.canUseFullScreenIntent()
            } else {
                enableFullScreen
            }
            if (allowFullScreen) {
                builder.setFullScreenIntent(fullScreenPendingIntent, true)
            }

            try {
                nm.notify(NOTIFICATION_ID_ALARM, builder.build())
            } catch (e: Exception) {
                Log.w(TAG, "Unable to post high-priority alarm notification: ${e.message}")
            }
        }

        /**
         * Updates the notification state when the user presses STOP ALARM.
         */
        fun onAlarmStoppedNotificationUpdate(context: Context, stoppedAtTime: String) {
            if (!canPostNotifications(context)) return
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    32,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val stoppedNotification = NotificationCompat.Builder(context, CHANNEL_MONITOR_ID)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setContentTitle("Security Alert • ALARM STOPPED")
                    .setContentText("Alarm stopped manually at $stoppedAtTime. System remains Armed.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentIntent(openPendingIntent)
                    .build()

                nm.notify(NOTIFICATION_ID_ALARM, stoppedNotification)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to update stopped alarm notification: ${e.message}")
            }
        }

        fun cancelAlarmAlertNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIFICATION_ID_ALARM)
            } catch (_: Exception) {
            }
        }

        private fun hasBluetoothPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        private fun canPostNotifications(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }
}
