package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.data.AlarmSoundOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Zero-latency continuous looping alarm audio & vibration controller.
 * Pre-warms and retains the prepared MediaPlayer instance so starting and stopping
 * the alarm happens instantaneously without disk/decoder preparation delay.
 */
class AlarmAudioVibrationController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var previewJob: Job? = null

    private var mediaPlayer: MediaPlayer? = null
    private var preparedSoundOption: AlarmSoundOption? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    var isPlayingAlarm: Boolean = false
        private set

    private val alarmAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    init {
        // Pre-warm default alarm sound in the background so the very first alarm has 0ms latency
        preWarmPlayer(AlarmSoundOption.TACTICAL_SIREN, 1.0f)
    }

    /**
     * Pre-loads and prepares the MediaPlayer for the selected sound option so that
     * when MOTION_DETECTED arrives, player.start() executes in < 2ms.
     */
    @Synchronized
    fun preWarmPlayer(soundOption: AlarmSoundOption, volume: Float) {
        if (mediaPlayer != null && preparedSoundOption == soundOption) {
            return
        }
        if (isPlayingAlarm) return

        try {
            releaseMediaPlayerOnly()
            val player = createAndPreparePlayer(soundOption, volume)
            if (player != null) {
                mediaPlayer = player
                preparedSoundOption = soundOption
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pre-warm MediaPlayer warning: ${e.message}")
        }
    }

    private fun createAndPreparePlayer(
        soundOption: AlarmSoundOption,
        volume: Float
    ): MediaPlayer? {
        val player = MediaPlayer()
        player.setAudioAttributes(alarmAudioAttributes)
        player.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)

        val rawRes = soundOption.rawResId
        var initialized = false
        if (rawRes != null) {
            appContext.resources.openRawResourceFd(rawRes)?.use { afd ->
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                initialized = true
            }
        }

        if (!initialized) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (alarmUri != null) {
                player.setDataSource(appContext, alarmUri)
                initialized = true
            }
        }

        if (!initialized) {
            player.release()
            return null
        }

        player.isLooping = true
        val clampedVol = volume.coerceIn(0.1f, 1.0f)
        player.setVolume(clampedVol, clampedVol)
        player.prepare()
        return player
    }

    /**
     * Immediately starts continuous looping alarm audio and vibration.
     * Prevents repeated MOTION_DETECTED messages from restarting or layering multiple sounds.
     */
    @Synchronized
    fun startContinuousAlarm(
        soundOption: AlarmSoundOption,
        volume: Float,
        enableVibration: Boolean
    ) {
        previewJob?.cancel()
        previewJob = null

        if (isPlayingAlarm && mediaPlayer?.isPlaying == true) {
            return
        }

        ensureAlarmStreamAudible()
        acquireWakeLock()
        requestAlarmAudioFocus()

        try {
            val clampedVol = volume.coerceIn(0.1f, 1.0f)
            var player = mediaPlayer
            if (player == null || preparedSoundOption != soundOption) {
                releaseMediaPlayerOnly()
                player = createAndPreparePlayer(soundOption, clampedVol)
                mediaPlayer = player
                preparedSoundOption = soundOption
            }

            if (player != null) {
                player.isLooping = true
                player.setVolume(clampedVol, clampedVol)
                player.seekTo(0)
                if (!player.isPlaying) {
                    player.start()
                }
                isPlayingAlarm = true
            } else {
                isPlayingAlarm = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback re-init MediaPlayer on start: ${e.message}")
            try {
                releaseMediaPlayerOnly()
                val fresh = createAndPreparePlayer(soundOption, volume)
                fresh?.start()
                mediaPlayer = fresh
                preparedSoundOption = soundOption
            } catch (inner: Exception) {
                Log.e(TAG, "Error starting looping alarm audio: ${inner.message}")
            }
            isPlayingAlarm = true
        }

        if (enableVibration) {
            startContinuousVibration()
        }
    }

    /**
     * Plays a brief ~1.4s preview of the selected alarm sound in Settings without triggering
     * emergency alarm state, leaving the player rewound and pre-warmed at 0ms afterwards.
     */
    @Synchronized
    fun playPreview(soundOption: AlarmSoundOption, volume: Float) {
        if (isPlayingAlarm) return

        previewJob?.cancel()
        ensureAlarmStreamAudible()

        try {
            val clampedVol = volume.coerceIn(0.1f, 1.0f)
            if (mediaPlayer == null || preparedSoundOption != soundOption) {
                releaseMediaPlayerOnly()
                val fresh = createAndPreparePlayer(soundOption, clampedVol)
                mediaPlayer = fresh
                preparedSoundOption = soundOption
            }
            val player = mediaPlayer ?: return
            player.isLooping = false
            player.setVolume(clampedVol, clampedVol)
            if (player.isPlaying) {
                player.pause()
            }
            player.seekTo(0)
            player.start()

            previewJob = previewScope.launch {
                delay(1400L)
                stopPreviewIfNotAlarming()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sound preview warning: ${e.message}")
        }
    }

    @Synchronized
    private fun stopPreviewIfNotAlarming() {
        if (isPlayingAlarm) return
        try {
            val player = mediaPlayer ?: return
            if (player.isPlaying) {
                player.pause()
            }
            player.seekTo(0)
            player.isLooping = true
        } catch (_: Exception) {
        }
    }

    private fun ensureAlarmStreamAudible() {
        try {
            val currentStreamVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentStreamVol == 0) {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                if (maxVol > 0) {
                    val safeAudible = (maxVol * 0.7f).toInt().coerceAtLeast(1)
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, safeAudible, 0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not inspect/adjust alarm stream volume: ${e.message}")
        }
    }

    /**
     * Updates volume live while alarm is playing or in settings.
     */
    @Synchronized
    fun updateVolume(volume: Float) {
        val clamped = volume.coerceIn(0.1f, 1.0f)
        try {
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update MediaPlayer volume: ${e.message}")
        }
    }

    /**
     * Immediately stops the looping alarm audio, rewinds the prepared player to 0ms
     * so it is instantaneously ready for the next motion detection, stops phone vibration,
     * abandons audio focus, and releases the wake lock.
     */
    @Synchronized
    fun stopAlarmImmediately() {
        previewJob?.cancel()
        previewJob = null
        isPlayingAlarm = false
        try {
            val player = mediaPlayer
            if (player != null) {
                if (player.isPlaying) {
                    player.pause()
                }
                player.seekTo(0)
                player.isLooping = true
            }
        } catch (e: Exception) {
            releaseMediaPlayerOnly()
        }
        stopVibration()
        abandonAlarmAudioFocus()
        releaseWakeLock()
    }

    fun startVibrationOnly() {
        startContinuousVibration()
    }

    private fun startContinuousVibration() {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return

            val pattern = longArrayOf(0L, 450L, 200L, 450L, 200L)
            val effect = VibrationEffect.createWaveform(pattern, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                vib.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(effect, alarmAudioAttributes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start vibration: ${e.message}")
        }
    }

    fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to cancel vibration: ${e.message}")
        }
    }

    private fun releaseMediaPlayerOnly() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer release warning: ${e.message}")
        } finally {
            mediaPlayer = null
            preparedSoundOption = null
        }
    }

    private fun requestAlarmAudioFocus() {
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(alarmAudioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { /* Keep alarm audible during emergency */ }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus: ${e.message}")
        }
    }

    private fun abandonAlarmAudioFocus() {
        try {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus: ${e.message}")
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SmartPIRSecurity::AlarmWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(30 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire warning: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release warning: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AlarmAudioController"
    }
}
