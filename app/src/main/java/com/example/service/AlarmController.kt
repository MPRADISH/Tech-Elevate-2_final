package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * High-priority emergency alarm controller providing loud alternating frequency siren
 * and vibration pattern matching CampusGuard protocol.
 */
class AlarmController(private val context: Context) {
    private val tag = "AlarmController"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var alarmJob: Job? = null
    private var vibratorJob: Job? = null

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    @Volatile
    var isAlarming: Boolean = false
        private set

    @Synchronized
    fun startAlarm(severity: Int = 3, allowSound: Boolean = true, allowVibration: Boolean = true) {
        stopAlarm()
        isAlarming = true
        Log.d(tag, "Starting emergency alarm (severity=$severity, sound=$allowSound, vib=$allowVibration)")

        if (allowVibration) {
            startVibration()
        }

        if (allowSound) {
            startSirenSound(severity)
        }
    }

    @Synchronized
    fun stopAlarm() {
        isAlarming = false
        alarmJob?.cancel()
        alarmJob = null
        vibratorJob?.cancel()
        vibratorJob = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(tag, "Error cancelling vibrator: ${e.message}")
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 400, 200, 400, 200, 700)
        vibratorJob = scope.launch {
            while (isActive && isAlarming) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, -1)
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Vibration failed: ${e.message}")
                }
                delay(2000L)
            }
        }
    }

    private fun startSirenSound(severity: Int) {
        alarmJob = scope.launch {
            val sampleRate = 22050
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = if (minBuf > 0) minBuf.coerceAtLeast(4096) else 4096

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormatBuilder = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .build()

            var audioTrack: AudioTrack? = null
            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormatBuilder)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.play()

                val volume = if (severity == 3) 0.9f else 0.65f
                audioTrack.setVolume(volume)

                val sweepDurationSamples = (sampleRate * 0.45).toInt()
                val audioBuffer = ShortArray(sweepDurationSamples)

                var high = false
                while (isActive && isAlarming) {
                    high = !high
                    val startFreq = if (high) 480.0 else 1000.0
                    val endFreq = if (high) 1000.0 else 480.0

                    var currentPhase = 0.0
                    for (i in 0 until sweepDurationSamples) {
                        val progress = i.toDouble() / sweepDurationSamples
                        val freq = startFreq + (endFreq - startFreq) * progress
                        currentPhase += 2.0 * Math.PI * freq / sampleRate
                        val sample = (sin(currentPhase) * 30000).toInt().toShort()
                        audioBuffer[i] = sample
                    }

                    audioTrack.write(audioBuffer, 0, sweepDurationSamples)
                }
            } catch (e: Exception) {
                Log.w(tag, "AudioTrack synthesis error: ${e.message}")
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (ignored: Exception) {
                }
            }
        }
    }
}
