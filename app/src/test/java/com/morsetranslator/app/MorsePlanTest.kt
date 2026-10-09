package com.morsetranslator.app

import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlan
import com.morsetranslator.app.morse.MorsePlan.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorsePlanTest {

    private val unit20 = 60L // 1200/20

    @Test
    fun `sos plan has exact ITU timing at 20wpm`() {
        val events = MorsePlan.parse("... --- ...", 20)
        // 9 signals + 6 intra-character gaps + 2 letter gaps
        assertEquals(17, events.size)

        val expected: List<Event> = listOf(
            Event.Signal(unit20), Event.Gap(unit20), // S: dot
            Event.Signal(unit20), Event.Gap(unit20),
            Event.Signal(unit20),
            Event.Gap(unit20 * 3), // letter gap
            Event.Signal(unit20 * 3), Event.Gap(unit20), // O: dash
            Event.Signal(unit20 * 3), Event.Gap(unit20),
            Event.Signal(unit20 * 3),
            Event.Gap(unit20 * 3), // letter gap
            Event.Signal(unit20), Event.Gap(unit20), // S: dot
            Event.Signal(unit20), Event.Gap(unit20),
            Event.Signal(unit20) // no trailing gap
        )
        assertEquals(expected, events)
    }

    @Test
    fun `word gap is exactly 7 units and replaces letter gap`() {
        val events = MorsePlan.parse(".- / -...", 20)
        // A: dot, intra, dash, WORD GAP(7), B: dash, intra, dot, intra, dot, intra, dot
        val expected: List<Event> = listOf(
            Event.Signal(unit20), Event.Gap(unit20), Event.Signal(unit20 * 3),
            Event.Gap(unit20 * 7), // word gap replaces the letter gap
            Event.Signal(unit20 * 3), Event.Gap(unit20),
            Event.Signal(unit20), Event.Gap(unit20),
            Event.Signal(unit20), Event.Gap(unit20),
            Event.Signal(unit20)
        )
        assertEquals(expected, events)
    }

    @Test
    fun `duration is the exact sum of events`() {
        val events = MorsePlan.parse("... --- ...", 20)
        // 6 dots * 60 + 3 dashes * 180 + 6 intra * 60 + 2 letter gaps * 180
        assertEquals(360 + 540 + 360 + 360, MorsePlan.durationMs(events))
        assertEquals(1620L, MorsePlan.durationMs(events))
    }

    @Test
    fun `no trailing gap after the last signal`() {
        val events = MorsePlan.parse(".-", 20)
        assertTrue(events.isNotEmpty())
        assertTrue(events.last() is Event.Signal)
    }

    @Test
    fun `unknown tokens are skipped while validation reports them`() {
        val events = MorsePlan.parse("... ???", 20)
        // Only S contributes signals; "???" is skipped (validation reports it).
        assertEquals(5, events.size) // dot, gap, dot, gap, dot
        val validation = MorseCode.validateMorse("... ???")
        assertEquals(1, validation.unknownTokens.size)
    }

    @Test
    fun `empty and blank input produce no events`() {
        assertTrue(MorsePlan.parse("", 20).isEmpty())
        assertTrue(MorsePlan.parse("   ", 20).isEmpty())
        assertTrue(MorsePlan.parse(" / ", 20).isEmpty())
    }

    @Test
    fun `trailing word separator does not add a gap`() {
        val withTrailing = MorsePlan.parse("... / ", 20)
        val plain = MorsePlan.parse("...", 20)
        assertEquals(plain, withTrailing)
    }

    @Test
    fun `signal count matches playable symbols`() {
        val events = MorsePlan.parse("... --- ...", 20)
        assertEquals(9, MorsePlan.signalCount(events))
    }

    @Test
    fun `unicode morse variants are normalized before planning`() {
        val ascii = MorsePlan.parse("... --- ...", 20)
        val unicode = MorsePlan.parse("··· −−− ···", 20)
        assertEquals(ascii, unicode)
    }

    @Test
    fun `long plans stay linear and bounded`() {
        val longMorse = (".- ".repeat(500)).trim()
        val events = MorsePlan.parse(longMorse, 20)
        assertTrue(events.isNotEmpty())
        // A = dot, intra-gap, dash, letter-gap per token (no trailing gap)
        assertEquals(500 * 4 - 1, events.size)
        assertTrue(MorsePlan.durationMs(events) > 0)
    }

    @Test
    fun `timing scales correctly at slow and fast wpm`() {
        // 5 WPM -> 240ms unit; 40 WPM -> 30ms unit.
        val slow = MorsePlan.parse(".-", 5)
        assertEquals(
            listOf(
                Event.Signal(240), Event.Gap(240), Event.Signal(720)
            ),
            slow
        )
        val fast = MorsePlan.parse(".-", 40)
        assertEquals(
            listOf(
                Event.Signal(30), Event.Gap(30), Event.Signal(90)
            ),
            fast
        )
    }

    @Test
    fun `invalid input cannot produce a misleading valid duration`() {
        // Unplayable tokens are skipped: no signals -> zero duration,
        // and the UI disables playback for empty plans.
        assertEquals(0L, MorseCode.estimatedDurationMs("???", 20))
        assertEquals(0L, MorseCode.estimatedDurationMs("", 20))
        assertEquals("0s", MorseCode.formatDuration(0))
    }

    @Test
    fun `repeated separators collapse to a single word gap in the plan`() {
        // "//" is malformed (validation flags it), but for playback the plan
        // must not stack word gaps: repeated separators yield exactly one.
        val events = MorsePlan.parse("... /// ...", 20)
        val gaps = events.filterIsInstance<Event.Gap>()
        assertEquals(1, gaps.count { it.durationMs == unit20 * 7 })
        val validation = MorseCode.validateMorse("... /// ...")
        assertTrue(validation.malformedSeparators)
        assertFalse(validation.isOk)
    }

    @Test
    fun `persian plan uses the same timing rules`() {
        val events = MorsePlan.parse(
            "...", 20, com.morsetranslator.app.morse.PersianMorse.PROFILE
        )
        assertEquals(5, events.size) // dot, gap, dot, gap, dot
        assertEquals(3 * unit20 + 2 * unit20, MorsePlan.durationMs(events))
    }
}
