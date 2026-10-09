package com.morsetranslator.app

import com.morsetranslator.app.audio.AudioDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioDecoderTest {

    @Test
    fun `synthetic sos decodes exactly`() {
        val pcm = AudioDecoder.synthesize("... --- ...", wpm = 20)
        val segments = AudioDecoder.detectSegments(pcm)
        assertEquals(9, segments.size)

        val classification = AudioDecoder.classify(segments)
        assertEquals("... --- ...", classification.morse)
        assertEquals(60L, classification.estimatedUnitMs)
        assertEquals(20, classification.estimatedWpm)
    }

    @Test
    fun `word gaps survive the pipeline`() {
        val pcm = AudioDecoder.synthesize("... / ...", wpm = 20)
        val classification = AudioDecoder.classify(AudioDecoder.detectSegments(pcm))
        assertEquals("... / ...", classification.morse)
    }

    @Test
    fun `silence produces no segments`() {
        val pcm = ShortArray(16000) // 1s of digital silence
        assertTrue(AudioDecoder.detectSegments(pcm).isEmpty())
        val classification = AudioDecoder.classify(emptyList())
        assertEquals("", classification.morse)
        assertEquals(0, classification.estimatedWpm)
    }

    @Test
    fun `low background noise produces no segments`() {
        // 2 seconds of quiet uniform noise; deterministic seed.
        val rnd = kotlin.random.Random(7)
        val noise = ShortArray(32000) {
            ((rnd.nextDouble() * 2 - 1) * 0.05 * Short.MAX_VALUE).toInt().toShort()
        }
        assertTrue(AudioDecoder.detectSegments(noise).isEmpty())
    }

    @Test
    fun `moderate noise does not break decoding`() {
        val pcm = AudioDecoder.synthesize("... --- ...", wpm = 20, noiseLevel = 0.03)
        val classification = AudioDecoder.classify(AudioDecoder.detectSegments(pcm))
        assertEquals("... --- ...", classification.morse)
    }

    @Test
    fun `streaming detector matches batch detection`() {
        val pcm = AudioDecoder.synthesize("... --- ...", wpm = 20)
        val config = AudioDecoder.DecodeConfig()
        val streaming = AudioDecoder.StreamingDetector(config)
        // Feed in odd-sized chunks like a real microphone callback would.
        var pos = 0
        while (pos < pcm.size) {
            val end = minOf(pos + 997, pcm.size)
            streaming.feed(pcm.copyOfRange(pos, end))
            pos = end
        }
        streaming.flush()
        val batch = AudioDecoder.detectSegments(pcm, config)
        assertEquals(batch, streaming.segments)
    }

    @Test
    fun `flush emits a trailing tone without trailing silence`() {
        val config = AudioDecoder.DecodeConfig()
        val detector = AudioDecoder.StreamingDetector(config)
        // 100 ms of tone at 800 Hz, no trailing silence.
        val n = 1600
        val tone = ShortArray(n) { i ->
            (kotlin.math.sin(2 * kotlin.math.PI * 800 * i / 16000) * 0.5 * Short.MAX_VALUE)
                .toInt().toShort()
        }
        detector.feed(tone)
        assertTrue(detector.segments.isEmpty()) // not yet flushed
        detector.flush()
        assertEquals(1, detector.segments.size)
        assertTrue(detector.segments[0].durationMs >= 80)
    }

    @Test
    fun `short clicks below min tone are ignored`() {
        val config = AudioDecoder.DecodeConfig()
        val detector = AudioDecoder.StreamingDetector(config)
        // 10 ms click — below minToneMs (30 ms).
        val n = 160
        val click = ShortArray(n) { i ->
            (kotlin.math.sin(2 * kotlin.math.PI * 800 * i / 16000) * 0.5 * Short.MAX_VALUE)
                .toInt().toShort()
        }
        val silence = ShortArray(3200)
        detector.feed(click)
        detector.feed(silence)
        detector.flush()
        assertTrue(detector.segments.isEmpty())
    }

    @Test
    fun `config rejects non-hysteresis thresholds`() {
        try {
            AudioDecoder.DecodeConfig(onThreshold = 0.05f, offThreshold = 0.10f)
            assertTrue("expected IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `unit estimate is robust to a few long dashes`() {
        // Mostly dots with two dashes: unit must stay near the dot length.
        val segments = listOf(
            AudioDecoder.ToneSegment(0, 60),
            AudioDecoder.ToneSegment(120, 180),
            AudioDecoder.ToneSegment(360, 540), // dash
            AudioDecoder.ToneSegment(600, 660),
            AudioDecoder.ToneSegment(720, 900) // dash
        )
        val classification = AudioDecoder.classify(segments)
        assertTrue(
            "unit=${classification.estimatedUnitMs}",
            abs(classification.estimatedUnitMs - 60) <= 20
        )
        // ".." then letter gap (180ms), then "-.-"
        assertEquals(".. -.-", classification.morse)
    }
}
