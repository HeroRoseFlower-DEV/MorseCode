package com.morsetranslator.app.morse

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** Playback settings shared by all signal types. */
data class PlaybackSettings(val wpm: Int, val toneHz: Int)

/**
 * Plays morse code as sound, camera flash, vibration — or all three at once.
 *
 * Only one playback runs at a time; starting a new one stops the previous.
 * All methods are safe to call from the main thread.
 */
class MorsePlayer(private val context: Context) {

    private var job: Job? = null
    val isPlaying: Boolean get() = job?.isActive == true

    /** Maximum morse symbols per playback, to avoid endless signals. */
    private val maxSymbols = 400

    fun stop() {
        job?.cancel()
        job = null
        setTorchOff()
        vibrator().cancel()
    }

    fun hasFlash(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    // ------------------------------------------------------------ public API

    fun playSound(
        scope: CoroutineScope,
        morse: String,
        settings: PlaybackSettings,
        onFinished: () -> Unit
    ) {
        stop()
        job = scope.launch(Dispatchers.Default) {
            try {
                runSound(morse, settings)
            } catch (_: CancellationException) {
                // stopped by user
            } finally {
                withContext(Dispatchers.Main) { onFinished() }
            }
        }
    }

    fun playFlash(
        scope: CoroutineScope,
        morse: String,
        settings: PlaybackSettings,
        onFinished: () -> Unit
    ) {
        stop()
        job = scope.launch(Dispatchers.Default) {
            try {
                runFlash(morse, settings)
            } catch (_: CancellationException) {
                // stopped by user
            } finally {
                withContext(Dispatchers.Main) { onFinished() }
            }
        }
    }

    fun playVibration(
        scope: CoroutineScope,
        morse: String,
        settings: PlaybackSettings,
        onFinished: () -> Unit
    ) {
        stop()
        job = scope.launch(Dispatchers.Default) {
            try {
                runVibration(morse, settings)
            } catch (_: CancellationException) {
                // stopped by user
            } finally {
                withContext(Dispatchers.Main) { onFinished() }
            }
        }
    }

    /** Play sound, flashlight and vibration simultaneously. */
    fun playCombined(
        scope: CoroutineScope,
        morse: String,
        settings: PlaybackSettings,
        onFinished: () -> Unit
    ) {
        stop()
        job = scope.launch(Dispatchers.Default) {
            try {
                coroutineScope {
                    launch { runSound(morse, settings) }
                    launch { runFlash(morse, settings) }
                    launch { runVibration(morse, settings) }
                }
            } catch (_: CancellationException) {
                // stopped by user
            } finally {
                setTorchOff()
                vibrator().cancel()
                withContext(Dispatchers.Main) { onFinished() }
            }
        }
    }

    // ------------------------------------------------------------------ sound

    private suspend fun runSound(morse: String, settings: PlaybackSettings) {
        val sampleRate = 22050
        val pcm = buildPcm(morse.take(maxSymbols), settings, sampleRate)
        if (pcm.isNotEmpty()) playPcm(pcm, sampleRate)
    }

    /** Render the whole message to 16-bit PCM: tone for dits/dahs, silence for gaps. */
    private fun buildPcm(morse: String, settings: PlaybackSettings, sampleRate: Int): ShortArray {
        val ditMs = MorseCode.ditDurationMs(settings.wpm)
        val out = ArrayList<Short>(sampleRate * 4)

        fun tone(ms: Int) {
            val n = sampleRate * ms / 1000
            val amp = Short.MAX_VALUE * 0.4
            for (i in 0 until n) {
                out.add((sin(2 * PI * settings.toneHz * i / sampleRate) * amp).toInt().toShort())
            }
        }

        fun silence(ms: Int) {
            repeat(sampleRate * ms / 1000) { out.add(0) }
        }

        for (c in morse) {
            when (c) {
                '.' -> { tone(ditMs); silence(ditMs) }
                '-' -> { tone(ditMs * 3); silence(ditMs) }
                ' ' -> silence(ditMs * 2) // +1 dit from the previous element = letter gap (3)
                '/' -> silence(ditMs * 6) // +1 dit from the previous element = word gap (7)
            }
        }
        return out.toShortArray()
    }

    private suspend fun playPcm(pcm: ShortArray, sampleRate: Int) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            delay(pcm.size * 1000L / sampleRate + 200)
        } finally {
            try { track.stop() } catch (_: Exception) { /* already stopped */ }
            track.release()
        }
    }

    // -------------------------------------------------------------- flashlight

    private suspend fun runFlash(morse: String, settings: PlaybackSettings) {
        val cameraId = findFlashCameraId() ?: return
        val ditMs = MorseCode.ditDurationMs(settings.wpm).toLong()
        try {
            for (c in morse.take(maxSymbols)) {
                when (c) {
                    '.' -> { setTorch(cameraId, true); delay(ditMs); setTorch(cameraId, false); delay(ditMs) }
                    '-' -> { setTorch(cameraId, true); delay(ditMs * 3); setTorch(cameraId, false); delay(ditMs) }
                    ' ' -> delay(ditMs * 2)
                    '/' -> delay(ditMs * 6)
                }
            }
        } finally {
            setTorch(cameraId, false)
        }
    }

    private fun findFlashCameraId(): String? {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun setTorch(cameraId: String, on: Boolean) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cameraId, on)
        } catch (_: Exception) {
            // no permission or camera in use — leave the torch alone
        }
    }

    private fun setTorchOff() {
        findFlashCameraId()?.let { setTorch(it, false) }
    }

    // -------------------------------------------------------------- vibration

    private suspend fun runVibration(morse: String, settings: PlaybackSettings) {
        val ditMs = MorseCode.ditDurationMs(settings.wpm).toLong()
        try {
            // Waveform pattern: [initial delay, on, off, on, off, ...]
            val timings = mutableListOf(0L)
            for (c in morse.take(maxSymbols)) {
                when (c) {
                    '.' -> { timings.add(ditMs); timings.add(ditMs) }
                    '-' -> { timings.add(ditMs * 3); timings.add(ditMs) }
                    ' ' -> timings[timings.lastIndex] = timings.last() + ditMs * 2
                    '/' -> timings[timings.lastIndex] = timings.last() + ditMs * 6
                }
            }
            val total = timings.sum()
            vibrator().vibrate(VibrationEffect.createWaveform(timings.toLongArray(), -1))
            delay(total + 300)
        } finally {
            vibrator().cancel()
        }
    }

    private fun vibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}
