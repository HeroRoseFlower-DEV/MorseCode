package com.morsetranslator.app.morse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Playback settings shared by all signal types. */
data class PlaybackSettings(val wpm: Int, val toneHz: Int)

/** Selectable playback outputs. */
enum class Output { SOUND, FLASH, VIBRATION }

/** Why an output could not run. Reported to the UI, never swallowed. */
enum class IssueReason {
    /** The Morse produced no playable signals. */
    NO_SIGNALS,

    /** Device has no camera flash. */
    NO_FLASH_HARDWARE,

    /** Camera permission not granted for flashlight playback. */
    FLASH_PERMISSION_DENIED,

    /** Torch could not be controlled (in use, error). */
    FLASH_ERROR,

    /** AudioTrack could not be created or failed during playback. */
    AUDIO_ERROR,

    /** Device has no vibrator. */
    NO_VIBRATOR,

    /** Plan exceeds the vibration length limit (explicit, not silent). */
    VIBRATION_TOO_LONG
}

data class OutputIssue(val output: Output, val reason: IssueReason)

/** Lifecycle events of one playback session, delivered on the main thread. */
sealed interface PlaybackEvent {
    data object Started : PlaybackEvent

    /** Fraction 0..1 plus elapsed/total ms of the shared plan. */
    data class Progress(val fraction: Float, val elapsedMs: Long, val totalMs: Long) : PlaybackEvent

    /** Playback ran to completion; [issues] lists outputs that could not run. */
    data class Finished(val issues: List<OutputIssue>) : PlaybackEvent

    /** Nothing could be played at all. */
    data class Failed(val issue: OutputIssue) : PlaybackEvent
}

/**
 * Plays a [MorsePlan] timeline as sound, camera torch and/or vibration.
 *
 * All outputs consume the same parsed event plan, so timing, duration and
 * progress can never disagree. Only one session runs at a time; starting a
 * new one stops the previous. All listener callbacks run on the main thread.
 *
 * Resource handling: AudioTrack is streamed in small chunks (bounded memory,
 * no silent truncation of long messages), the torch is forced off and the
 * vibrator cancelled in every success/failure/cancellation path.
 */
class MorsePlayer(private val context: Context) {

    /** Vibration is capped at this plan length; longer plans skip vibration explicitly. */
    val maxVibrationMs: Long = 120_000L // 2 minutes

    private var job: Job? = null

    /** Non-null while a session is active. Volatile for cross-thread stop(). */
    @Volatile
    private var activeTrack: AudioTrack? = null

    val isPlaying: Boolean get() = job?.isActive == true

    fun hasFlash(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    fun hasVibrator(): Boolean = try {
        vibrator().hasVibrator()
    } catch (_: Exception) {
        false
    }

    fun hasFlashPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    fun stop() {
        job?.cancel()
        job = null
        activeTrack?.let {
            try { it.stop() } catch (_: Exception) { /* already stopped */ }
        }
        setTorchOff()
        try { vibrator().cancel() } catch (_: Exception) { /* no vibrator */ }
    }

    /**
     * Starts playback of [morse] on [outputs]. Runs per-output preflight
     * first: outputs that cannot run are reported in [PlaybackEvent.Finished]
     * (or [PlaybackEvent.Failed] when nothing can run) instead of being
     * silently skipped.
     */
    fun play(
        scope: CoroutineScope,
        morse: String,
        settings: PlaybackSettings,
        outputs: Set<Output>,
        profile: MorseCode.AlphabetProfile = MorseCode.INTERNATIONAL,
        listener: (PlaybackEvent) -> Unit
    ) {
        stop()
        val mainListener: (PlaybackEvent) -> Unit = { event ->
            // Always deliver on the main thread; the session runs on Default.
            if (isMainThread()) listener(event)
            else scope.launch(Dispatchers.Main) { listener(event) }
        }
        job = scope.launch(Dispatchers.Default) {
            val events = MorsePlan.parse(morse, settings.wpm, profile)
            if (events.none { it is MorsePlan.Event.Signal }) {
                mainListener(
                    PlaybackEvent.Failed(
                        OutputIssue(outputs.firstOrNull() ?: Output.SOUND, IssueReason.NO_SIGNALS)
                    )
                )
                return@launch
            }
            val totalMs = MorsePlan.durationMs(events)
            val issues = mutableListOf<OutputIssue>()
            val runnable = outputs.toMutableSet()

            // ---- preflight: identical checks for standalone and combined use
            if (Output.FLASH in runnable) {
                when {
                    !hasFlash() -> {
                        issues.add(OutputIssue(Output.FLASH, IssueReason.NO_FLASH_HARDWARE))
                        runnable.remove(Output.FLASH)
                    }
                    !hasFlashPermission() -> {
                        issues.add(OutputIssue(Output.FLASH, IssueReason.FLASH_PERMISSION_DENIED))
                        runnable.remove(Output.FLASH)
                    }
                    findFlashCameraId() == null -> {
                        issues.add(OutputIssue(Output.FLASH, IssueReason.FLASH_ERROR))
                        runnable.remove(Output.FLASH)
                    }
                }
            }
            if (Output.VIBRATION in runnable) {
                when {
                    !hasVibrator() -> {
                        issues.add(OutputIssue(Output.VIBRATION, IssueReason.NO_VIBRATOR))
                        runnable.remove(Output.VIBRATION)
                    }
                    totalMs > maxVibrationMs -> {
                        issues.add(OutputIssue(Output.VIBRATION, IssueReason.VIBRATION_TOO_LONG))
                        runnable.remove(Output.VIBRATION)
                    }
                }
            }
            if (runnable.isEmpty()) {
                mainListener(PlaybackEvent.Failed(issues.first()))
                return@launch
            }

            mainListener(PlaybackEvent.Started)
            // Called from several output coroutines; guard the throttle state.
            val progressLock = Any()
            var lastFraction = 0f
            fun reportProgress(elapsedMs: Long) {
                val fraction = (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
                val shouldReport = synchronized(progressLock) {
                    if (fraction - lastFraction >= 0.01f || fraction >= 1f) {
                        lastFraction = fraction
                        true
                    } else false
                }
                if (shouldReport) {
                    mainListener(PlaybackEvent.Progress(fraction, elapsedMs, totalMs))
                }
            }

            try {
                coroutineScope {
                    if (Output.SOUND in runnable) {
                        launch { runSound(events, settings, ::reportProgress) }
                    }
                    if (Output.FLASH in runnable) {
                        launch { runTorch(events, ::reportProgress) }
                    }
                    if (Output.VIBRATION in runnable) {
                        launch { runVibration(events, ::reportProgress) }
                    }
                }
                reportProgress(totalMs)
                mainListener(PlaybackEvent.Finished(issues))
            } catch (e: CancellationException) {
                throw e // user stop: no event, UI already reset its state
            } catch (e: OutputException) {
                issues.add(e.issue)
                // If every runnable output failed, report failure; otherwise
                // finish with the collected issues.
                mainListener(PlaybackEvent.Finished(issues))
            } finally {
                setTorchOff()
                try { vibrator().cancel() } catch (_: Exception) { /* ignore */ }
                activeTrack = null
            }
        }
    }

    private class OutputException(val issue: OutputIssue) : Exception()

    private fun isMainThread(): Boolean =
        android.os.Looper.myLooper() == android.os.Looper.getMainLooper()

    // ------------------------------------------------------------------ sound

    /**
     * Streams PCM in ~1s chunks (bounded memory, no truncation of long
     * messages). Each tone has a short fade envelope to avoid clicks.
     */
    private suspend fun runSound(
        events: List<MorsePlan.Event>,
        settings: PlaybackSettings,
        reportProgress: (Long) -> Unit
    ) {
        val sampleRate = 22050
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) throw OutputException(OutputIssue(Output.SOUND, IssueReason.AUDIO_ERROR))
        val track = try {
            AudioTrack.Builder()
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
                .setBufferSizeInBytes(minBuf * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            throw OutputException(OutputIssue(Output.SOUND, IssueReason.AUDIO_ERROR))
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw OutputException(OutputIssue(Output.SOUND, IssueReason.AUDIO_ERROR))
        }
        activeTrack = track
        try {
            track.play()
            val chunk = ShortArray(sampleRate) // ~1 second
            var chunkPos = 0
            var samplesWritten = 0L
            var phase = 0.0
            val phaseInc = 2 * PI * settings.toneHz / sampleRate
            val amplitude = (Short.MAX_VALUE * 0.4).toInt()
            val fadeSamples = (sampleRate * 3 / 1000).coerceAtLeast(1) // 3 ms envelope

            suspend fun flush() {
                if (chunkPos == 0) return
                val written = track.write(chunk, 0, chunkPos)
                if (written < 0) throw OutputException(
                    OutputIssue(Output.SOUND, IssueReason.AUDIO_ERROR)
                )
                samplesWritten += chunkPos
                chunkPos = 0
            }

            suspend fun writeTone(durationMs: Long) {
                val totalSamples = (sampleRate * durationMs / 1000).toInt()
                var remaining = totalSamples
                var tonePos = 0
                while (remaining > 0) {
                    coroutineContext.ensureActive()
                    if (chunkPos == chunk.size) flush()
                    val n = min(remaining, chunk.size - chunkPos)
                    for (i in 0 until n) {
                        val fadeIn = (tonePos + i).coerceAtMost(fadeSamples).toFloat() / fadeSamples
                        val fadeOut = (totalSamples - (tonePos + i)).coerceAtMost(fadeSamples).toFloat() / fadeSamples
                        val env = min(fadeIn, fadeOut).coerceIn(0f, 1f)
                        chunk[chunkPos++] =
                            (sin(phase) * amplitude * env).toInt().toShort()
                        phase += phaseInc
                    }
                    tonePos += n
                    remaining -= n
                }
            }

            suspend fun writeSilence(durationMs: Long) {
                var remaining = (sampleRate * durationMs / 1000).toInt()
                while (remaining > 0) {
                    coroutineContext.ensureActive()
                    if (chunkPos == chunk.size) flush()
                    val n = min(remaining, chunk.size - chunkPos)
                    repeat(n) { chunk[chunkPos++] = 0 }
                    remaining -= n
                }
            }

            var elapsedMs = 0L
            for (event in events) {
                coroutineContext.ensureActive()
                val ms = when (event) {
                    is MorsePlan.Event.Signal -> {
                        writeTone(event.durationMs)
                        event.durationMs
                    }
                    is MorsePlan.Event.Gap -> {
                        writeSilence(event.durationMs)
                        event.durationMs
                    }
                }
                elapsedMs += ms
                reportProgress(elapsedMs)
            }
            flush()
            // Wait for the streamed audio to drain (monotonic, cancellable).
            val expectedSamples = samplesWritten
            while (track.playbackHeadPosition < expectedSamples) {
                coroutineContext.ensureActive()
                delay(50)
            }
        } finally {
            activeTrack = null
            try { track.stop() } catch (_: Exception) { /* ignore */ }
            track.release()
        }
    }

    // -------------------------------------------------------------- flashlight

    /**
     * Drives the torch from the shared plan using monotonic deadlines, so
     * repeated relative sleeps cannot accumulate drift.
     */
    private suspend fun runTorch(
        events: List<MorsePlan.Event>,
        reportProgress: (Long) -> Unit
    ) {
        val cameraId = findFlashCameraId()
            ?: throw OutputException(OutputIssue(Output.FLASH, IssueReason.FLASH_ERROR))
        var elapsedMs = 0L
        val totalMs = MorsePlan.durationMs(events)
        var nextDeadline = SystemClock.elapsedRealtime()
        try {
            for (event in events) {
                coroutineContext.ensureActive()
                val ms = when (event) {
                    is MorsePlan.Event.Signal -> {
                        setTorch(cameraId, true)
                        event.durationMs
                    }
                    is MorsePlan.Event.Gap -> {
                        setTorch(cameraId, false)
                        event.durationMs
                    }
                }
                nextDeadline += ms
                val wait = nextDeadline - SystemClock.elapsedRealtime()
                if (wait > 0) delay(wait)
                elapsedMs += ms
                reportProgress(elapsedMs)
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

    // The CAMERA permission is checked at runtime in play()'s preflight
    // (hasFlashPermission()) before any torch output is started.
    @android.annotation.SuppressLint("MissingPermission")
    private fun setTorch(cameraId: String, on: Boolean) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cameraId, on)
        } catch (_: Exception) {
            // No permission, camera in use, etc. — never pretend it worked;
            // the caller treats exceptions as FLASH_ERROR.
            throw OutputException(OutputIssue(Output.FLASH, IssueReason.FLASH_ERROR))
        }
    }

    private fun setTorchOff() {
        try {
            findFlashCameraId()?.let { id ->
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cm.setTorchMode(id, false)
            }
        } catch (_: Exception) {
            // Best effort on the cleanup path.
        }
    }

    // -------------------------------------------------------------- vibration

    private suspend fun runVibration(
        events: List<MorsePlan.Event>,
        reportProgress: (Long) -> Unit
    ) {
        // Waveform pattern: [initial delay, on, off, on, off, ...]
        val timings = mutableListOf(0L)
        for (event in events) {
            when (event) {
                is MorsePlan.Event.Signal -> {
                    timings.add(event.durationMs)
                    timings.add(0L) // extended by the following gap, if any
                }
                is MorsePlan.Event.Gap ->
                    timings[timings.lastIndex] = timings.last() + event.durationMs
            }
        }
        if (timings.last() == 0L) timings.removeLast() // no trailing silence
        val total = timings.sum()
        try {
            vibrator().vibrate(VibrationEffect.createWaveform(timings.toLongArray(), -1))
        } catch (_: Exception) {
            throw OutputException(OutputIssue(Output.VIBRATION, IssueReason.NO_VIBRATOR))
        }
        try {
            // Cancellable wait; Android gives no completion callback, and we
            // do not claim millisecond-exact sync with the other outputs.
            val deadline = SystemClock.elapsedRealtime() + total
            var lastReport = 0L
            while (true) {
                coroutineContext.ensureActive()
                val now = SystemClock.elapsedRealtime()
                if (now >= deadline) break
                delay(100)
                val elapsed = (now - (deadline - total)).coerceIn(0, total)
                if (elapsed - lastReport >= total / 50 || elapsed >= total) {
                    lastReport = elapsed
                    reportProgress(elapsed)
                }
            }
        } finally {
            try { vibrator().cancel() } catch (_: Exception) { /* ignore */ }
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
