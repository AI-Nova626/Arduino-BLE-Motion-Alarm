package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles background notification actions (especially STOP ALARM when the app
 * is in the background or the phone screen is locked).
 */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val engine = SecurityEngine.getInstance(context)
        when (intent?.action) {
            PirSecurityForegroundService.ACTION_STOP_ALARM -> {
                engine.stopAlarmManually("Notification STOP ALARM")
            }
            PirSecurityForegroundService.ACTION_DISARM_SYSTEM -> {
                engine.disarmSystem()
            }
            PirSecurityForegroundService.ACTION_ARM_SYSTEM -> {
                engine.armSystem()
            }
        }
    }
}
