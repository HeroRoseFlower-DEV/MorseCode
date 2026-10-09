package com.morsetranslator.app.audio

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Offline microphone-to-Morse decoding pipeline.
 *
 * Everything here is pure and unit-testable: [StreamingDetector] turns PCM
 * into tone segments, [classify] turns segments into Morse, and the existing
 * [com.morsetranslator.app.morse.MorseCode.decodeDetailed] turns Morse into
 * text. No audio is uploaded, retained or logged — processing is local only.
 *
 * Honest limitations (also stated in the UI):
 * - Detection is energy-based with hysteresis; it is noise-tolerant but not
 *   perfect, especially with speech, music or heavy background noise.
 * - Timing classification assumes roughly consistent keying; it estimates
 *   the time unit from the observed tones and lets the user adjust
 *   sensitivity when needed.
 */
object AudioDecoder {

    /** One detected tone burst, in milliseconds from the session start. */
    data class ToneSegment(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    data class DecodeConfig(
        val sampleRate: Int = 16000,
        /** RMS window length. */
        val windowMs: Int = 20,
        /** Normalized RMS (0..1) that starts a tone. */
        val onThreshold: Float = 0.10f,
        /** Normalized RMS that ends a tone (hysteresis: lower than [onThreshold]). */
        val offThreshold: Float = 0.05f,
        /** Shorter bursts are treated as clicks, not tones. */
        val minToneMs: Int = 30,
        /** Shorter silences are merged into the surrounding tone. */
        val minSilenceMs: Int = 30
    ) {
        init {
            require(offThreshold < onThreshold) { "offThreshold must be below onThreshold (hysteresis)" }
        }
    }

    /**
     * Incremental energy detector. Feed 16-bit PCM chunks as they arrive;
     * read [segments] any time; call [flush] when the session ends.
     * Runs in O(n) time with O(1) state — no unbounded buffering.
     */
    class StreamingDetector(
        private val config: DecodeConfig,
        sampleRate: Int = config.sampleRate
    ) {
        private val windowSamples = (sampleRate * config.windowMs / 1000).coerceAtLeast(1)
        private val carry = ArrayList<Short>(windowSamples)
        private var windowIndex = 0L
        private var inTone = false
        private var toneStartWindow = 0L
        private var silenceStartWindow = -1L
        val segments = mutableListOf<ToneSegment>()

        fun feed(samples: ShortArray) {
            var pos = 0
            while (pos < samples.size) {
                val need = windowSamples - carry.size
                val take = min(need, samples.size - pos)
                for (i in 0 until take) carry.add(samples[pos + i])
                pos += take
                if (carry.size == windowSamples) {
                    processWindow(rms(carry), windowIndex)
                    carry.clear()
                    windowIndex++
                }
            }
        }

        fun flush() {
            if (inTone) {
                val startMs = toneStartWindow * config.windowMs
                val endMs = windowIndex * config.windowMs
                if (endMs - startMs >= config.minToneMs) {
                    segments.add(ToneSegment(startMs, endMs))
                }
                inTone = false
            }
        }

        private fun rms(window: List<Short>): Float {
            var sum = 0.0
            for (s in window) {
                val v = s / 32768.0
                sum += v * v
            }
            return sqrt(sum / window.size).toFloat()
        }

        private fun processWindow(r: Float, w: Long) {
            if (!inTone) {
                if (r >= config.onThreshold) {
                    inTone = true
                    toneStartWindow = w
                    silenceStartWindow = -1L
                }
                return
            }
            if (r <= config.offThreshold) {
                if (silenceStartWindow < 0) silenceStartWindow = w
                val silenceMs = (w - silenceStartWindow + 1) * config.windowMs
                if (silenceMs >= config.minSilenceMs) {
                    val startMs = toneStartWindow * config.windowMs
                    val endMs = silenceStartWindow * config.windowMs
                    if (endMs - startMs >= config.minToneMs) {
                        segments.add(ToneSegment(startMs, endMs))
                    }
                    inTone = false
                    silenceStartWindow = -1L
                }
            } else {
                silenceStartWindow = -1L // tone continues; brief dip ignored
            }
        }
    }

    /** Convenience wrapper for batch input (used by tests). */
    fun detectSegments(
        samples: ShortArray,
        config: DecodeConfig = DecodeConfig()
    ): List<ToneSegment> {
        val detector = StreamingDetector(config)
        detector.feed(samples)
        detector.flush()
        return detector.segments.toList()
    }

    data class Classification(
        /** Morse with ' ' letter gaps and ' / ' word gaps. */
        val morse: String,
        /** Time unit estimated from the observed tones, in ms. */
        val estimatedUnitMs: Long,
        /** WPM derived from the estimated unit (PARIS standard). */
        val estimatedWpm: Int
    )

    /**
     * Turns tone segments into Morse. The time unit is estimated from the
     * observed tones (median of the shorter half — dots dominate typical
     * text), then:
     * - tone < 2 units → dot, otherwise dash;
     * - gap < 2 units → intra-character (no separator);
     * - gap < 5 units → letter separator;
     * - otherwise → word separator.
     */
    fun classify(
        segments: List<ToneSegment>,
        config: DecodeConfig = DecodeConfig()
    ): Classification {
        if (segments.isEmpty()) {
            return Classification("", 0L, 0)
        }
        val durations = segments.map { it.durationMs }.sorted()
        // Unit estimate: median of the shorter half of tone durations.
        // Dots are shorter and usually more numerous; this resists a few
        // long dashes skewing the estimate.
        val half = durations.take((durations.size + 1) / 2)
        val unit = half[half.size / 2].coerceAtLeast(1L)

        val sb = StringBuilder()
        segments.forEachIndexed { i, seg ->
            if (i > 0) {
                val gap = seg.startMs - segments[i - 1].endMs
                when {
                    gap >= unit * 5 -> sb.append(" / ")
                    gap >= unit * 2 -> sb.append(' ')
                }
            }
            sb.append(if (seg.durationMs < unit * 2) '.' else '-')
        }
        val wpm = (1200 / unit).toInt().coerceIn(5, 60)
        return Classification(sb.toString(), unit, wpm)
    }

    // ------------------------------------------------------- test synthesis

    /**
     * Synthesizes 16-bit PCM for a Morse string — used by unit tests to
     * verify the detector/classifier without a microphone.
     *
     * @param toneHz sine frequency; [noiseLevel] adds uniform noise 0..1.
     */
    fun synthesize(
        morse: String,
        wpm: Int,
        toneHz: Int = 800,
        sampleRate: Int = 16000,
        noiseLevel: Double = 0.0,
        random: kotlin.random.Random = kotlin.random.Random(42)
    ): ShortArray {
        val unitMs = 1200 / wpm
        val out = ArrayList<Short>()
        fun tone(ms: Int) {
            val n = sampleRate * ms / 1000
            for (i in 0 until n) {
                val s = kotlin.math.sin(2 * kotlin.math.PI * toneHz * i / sampleRate)
                val noise = (random.nextDouble() * 2 - 1) * noiseLevel
                out.add(((s * 0.5 + noise) * Short.MAX_VALUE).toInt().toShort())
            }
        }
        fun silence(ms: Int) {
            val n = sampleRate * ms / 1000
            for (i in 0 until n) {
                val noise = (random.nextDouble() * 2 - 1) * noiseLevel
                out.add((noise * Short.MAX_VALUE).toInt().toShort())
            }
        }
        for (c in morse) {
            when (c) {
                '.' -> { tone(unitMs); silence(unitMs) }
                '-' -> { tone(unitMs * 3); silence(unitMs) }
                ' ' -> silence(unitMs * 2)
                '/' -> silence(unitMs * 6)
            }
        }
        return out.toShortArray()
    }
}
