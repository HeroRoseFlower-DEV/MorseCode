package com.morsetranslator.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Microphone capture feeding [AudioDecoder.StreamingDetector] incrementally.
 *
 * - Audio is processed locally in small chunks; it is never uploaded,
 *   retained after the session, or logged.
 * - [stop]/[release] promptly releases the microphone on screen exit,
 *   permission failure or lifecycle shutdown.
 * - Audio *input* does not take audio focus; concurrent media playback may
 *   bleed into the microphone — the sensitivity control and the honest
 *   "uncertain" note in the UI cover this instead of pretending otherwise.
 */
class MicRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var job: Job? = null

    val isRecording: Boolean get() = job?.isActive == true

    /**
     * Starts capture. Returns false when the microphone cannot be opened.
     * [onUpdate] is invoked on the main thread with the segments and
     * classification observed so far.
     */
    // The RECORD_AUDIO permission is requested at runtime (with rationale)
    // by DecodeScreen before start() is ever called.
    @android.annotation.SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        config: AudioDecoder.DecodeConfig,
        onUpdate: (List<AudioDecoder.ToneSegment>, AudioDecoder.Classification) -> Unit
    ): Boolean {
        if (isRecording) return true
        val sampleRate = config.sampleRate
        val minBuf = try {
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        } catch (_: Exception) {
            return false
        }
        if (minBuf <= 0) return false
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .build()
        } catch (_: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        audioRecord = record
        job = scope.launch(Dispatchers.IO) {
            val detector = AudioDecoder.StreamingDetector(config)
            val buf = ShortArray(sampleRate / 5) // 200 ms chunks
            try {
                record.startRecording()
                while (isActive) {
                    val n = record.read(buf, 0, buf.size)
                    if (n < 0) break // error
                    if (n > 0) {
                        detector.feed(buf.copyOf(n))
                        val segments = detector.segments.toList()
                        val classification = AudioDecoder.classify(segments, config)
                        withContext(Dispatchers.Main) {
                            onUpdate(segments, classification)
                        }
                    }
                }
            } catch (_: Exception) {
                // read/start failures end the session; UI shows stopped state.
            } finally {
                detector.flush()
                try {
                    record.stop()
                } catch (_: Exception) { /* already stopped */ }
                record.release()
                audioRecord = null
            }
        }
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
        // The recording coroutine releases the AudioRecord in its finally block.
    }

    fun release() {
        stop()
    }
}
