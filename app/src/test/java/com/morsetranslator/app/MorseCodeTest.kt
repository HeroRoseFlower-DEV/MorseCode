package com.morsetranslator.app

import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.PersianMorse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseCodeTest {

    // ------------------------------------------------------- international

    @Test
    fun `all international letters round-trip`() {
        val profile = MorseCode.INTERNATIONAL
        for (c in 'A'..'Z') {
            val code = profile.charToMorse[c]
            assertTrue("missing code for $c", code != null)
            assertEquals(c.toString(), MorseCode.decodeDetailed(code!!, profile).text)
        }
    }

    @Test
    fun `all international digits round-trip`() {
        val profile = MorseCode.INTERNATIONAL
        for (c in '0'..'9') {
            val code = profile.charToMorse[c]
            assertTrue("missing code for $c", code != null)
            assertEquals(c.toString(), MorseCode.decodeDetailed(code!!, profile).text)
        }
    }

    @Test
    fun `all international punctuation round-trips`() {
        val profile = MorseCode.INTERNATIONAL
        val punctuation = profile.charToMorse.keys.filter { it !in 'A'..'Z' && it !in '0'..'9' }
        assertTrue("expected punctuation in table", punctuation.size >= 10)
        for (c in punctuation) {
            val code = profile.charToMorse[c]!!
            assertEquals(c.toString(), MorseCode.decodeDetailed(code, profile).text)
        }
    }

    @Test
    fun `international table has no ambiguous codes`() {
        val codes = MorseCode.INTERNATIONAL.charToMorse.values.toList()
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `hello world encodes with word separator`() {
        assertEquals(
            ".... . .-.. .-.. --- / .-- --- .-. .-.. -..",
            MorseCode.encode("HELLO WORLD")
        )
    }

    @Test
    fun `sos round-trips`() {
        val morse = MorseCode.encode("SOS")
        assertEquals("... --- ...", morse)
        assertEquals("SOS", MorseCode.decode(morse))
    }

    @Test
    fun `text is uppercased with ROOT locale`() {
        assertEquals("... --- ...", MorseCode.encode("sos"))
    }

    // ------------------------------------------------------- normalization

    @Test
    fun `morse unicode variants normalize to ascii`() {
        // · • ‧ dots; − – — ― ‐ dashes
        assertEquals(
            "... --- ...",
            MorseCode.normalizeMorse("··· −−− ···")
        )
    }

    @Test
    fun `morse whitespace collapses and slashes canonicalize`() {
        assertEquals(
            "... / ---",
            MorseCode.normalizeMorse("  ...   //   ---  ")
        )
    }

    @Test
    fun `isValidMorse accepts only morse symbols`() {
        assertTrue(MorseCode.isValidMorse("... --- ... / .-"))
        assertFalse(MorseCode.isValidMorse("... ABC ..."))
        assertFalse(MorseCode.isValidMorse("hello"))
    }

    // ------------------------------------------------------- validation

    @Test
    fun `unsupported characters are reported with positions`() {
        val result = MorseCode.encodeDetailed("Aé")
        assertEquals(".-", result.morse)
        assertFalse(result.isComplete)
        assertEquals(1, result.unsupported.size)
        assertEquals('É', result.unsupported[0].char)
        assertEquals(1, result.unsupported[0].position)
    }

    @Test
    fun `blank input yields empty result`() {
        val result = MorseCode.encodeDetailed("   ")
        assertEquals("", result.morse)
        assertTrue(result.isComplete)
    }

    @Test
    fun `unknown morse tokens are reported and decoded as replacement`() {
        val result = MorseCode.decodeDetailed("... ......")
        assertEquals("S�", result.text)
        assertFalse(result.isComplete)
        assertEquals(1, result.unknownTokens.size)
        assertEquals("......", result.unknownTokens[0].token)
        assertEquals(1, result.unknownTokens[0].index)
    }

    @Test
    fun `malformed separators are detected`() {
        val result = MorseCode.decodeDetailed("... // ---")
        assertTrue(result.malformedSeparators)
        assertFalse(result.isComplete)
        // Valid tokens still decode.
        assertEquals("S O", result.text)
    }

    @Test
    fun `validateText and validateMorse agree with detailed results`() {
        assertTrue(MorseCode.validateText("HELLO 123").isOk)
        assertFalse(MorseCode.validateText("héllo").isOk)
        assertTrue(MorseCode.validateMorse("... --- ...").isOk)
        assertFalse(MorseCode.validateMorse("... ???").isOk)
    }

    // ------------------------------------------------------- playback input

    @Test
    fun `mixed scripts report unsupported characters explicitly`() {
        // Persian letters have no mapping in the international profile:
        // they are reported, never silently dropped or mis-encoded.
        val result = MorseCode.encodeDetailed("HI سلام", MorseCode.INTERNATIONAL)
        assertEquals(".... ..", result.morse)
        assertFalse(result.isComplete)
        assertTrue(result.unsupported.isNotEmpty())
        // ...but the same text encodes completely in the Persian profile.
        val faResult = MorseCode.encodeDetailed("HI سلام", PersianMorse.PROFILE)
        // Latin letters are unsupported in the Persian profile (explicit policy).
        assertFalse(faResult.isComplete)
        val faOnly = MorseCode.encodeDetailed("سلام", PersianMorse.PROFILE)
        assertTrue(faOnly.isComplete)
    }

    @Test
    fun `newlines become word-friendly spacing`() {
        val result = MorseCode.encodeDetailed("HELLO\nWORLD")
        assertEquals(".... . .-.. .-.. --- / .-- --- .-. .-.. -..", result.morse)
    }

    @Test
    fun `playbackMorse uses encoded output in text-to-morse mode`() {
        assertEquals(
            "... --- ...",
            MorseCode.playbackMorse(true, "... --- ...", "... --- ...")
        )
    }

    @Test
    fun `playbackMorse normalizes raw morse input in morse-to-text mode`() {
        // Never the decoded *text* — that would produce silence.
        assertEquals(
            "... --- ...",
            MorseCode.playbackMorse(false, "  ··· −−− ···  ", "")
        )
    }

    // ------------------------------------------------------- timing helpers

    @Test
    fun `unit duration follows PARIS standard and clamps`() {
        assertEquals(60, MorseCode.unitDurationMs(20)) // 1200/20
        assertEquals(240, MorseCode.unitDurationMs(5))
        assertEquals(20, MorseCode.unitDurationMs(1000)) // clamped to 60 wpm -> 20ms
    }

    @Test
    fun `estimated duration equals plan duration`() {
        val viaHelper = MorseCode.estimatedDurationMs("... --- ...", 20)
        val viaPlan = com.morsetranslator.app.morse.MorsePlan.durationMs(
            com.morsetranslator.app.morse.MorsePlan.parse("... --- ...", 20)
        )
        assertEquals(viaPlan, viaHelper)
        assertEquals(1620L, viaHelper)
    }

    @Test
    fun `formatDuration always includes seconds`() {
        assertEquals("0s", MorseCode.formatDuration(0))
        assertEquals("8s", MorseCode.formatDuration(8000))
        assertEquals("1m 05s", MorseCode.formatDuration(65000))
        assertEquals("2h 03m 07s", MorseCode.formatDuration(2 * 3600_000L + 3 * 60_000L + 7000L))
    }

    // ------------------------------------------------------- persian profile

    @Test
    fun `all 32 persian letters round-trip`() {
        assertEquals(32, PersianMorse.LETTERS.size)
        for (c in PersianMorse.LETTERS) {
            val code = PersianMorse.PROFILE.charToMorse[c]
            assertTrue("missing code for $c", code != null)
            assertEquals(
                c.toString(),
                MorseCode.decodeDetailed(code!!, PersianMorse.PROFILE).text
            )
        }
    }

    @Test
    fun `persian profile has no ambiguous codes`() {
        val codes = PersianMorse.PROFILE.charToMorse.values.toList()
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `persian and international profiles stay separate`() {
        assertEquals("international", MorseCode.INTERNATIONAL.id)
        assertEquals("persian", PersianMorse.PROFILE.id)
        // 'س' (...) collides with international 'S' — profiles must not mix.
        assertEquals("...", PersianMorse.PROFILE.charToMorse['س'])
        assertEquals("...", MorseCode.INTERNATIONAL.charToMorse['S'])
    }

    @Test
    fun `persian variant characters fold to canonical forms`() {
        // ي U+064A -> ی, ك U+0643 -> ک, ١٢٣ -> ۱۲۳
        assertEquals("ی", PersianMorse.normalizePersianText("ي"))
        assertEquals("ک", PersianMorse.normalizePersianText("ك"))
        assertEquals("۱۲۳", PersianMorse.normalizePersianText("١٢٣"))
        assertEquals("۱۲۳", PersianMorse.normalizePersianText("۱۲۳"))
    }

    @Test
    fun `persian combining marks and ZWNJ are ignored not reported`() {
        // نسخهٔ with U+0654 hamza above; نیم‌فاصله U+200C
        val normalized = PersianMorse.normalizePersianText("نسخهٔ می‌شود")
        assertFalse(normalized.contains('ٔ'))
        assertFalse(normalized.contains('‌'))
        val validation = MorseCode.validateText("نسخهٔ می‌شود", PersianMorse.PROFILE)
        assertTrue(validation.isOk)
    }

    @Test
    fun `persian digits encode with international digit sequences`() {
        for (d in '۰'..'۹') {
            val code = PersianMorse.PROFILE.charToMorse[d]
            assertTrue("missing code for $d", code != null)
        }
        assertEquals(".----", PersianMorse.PROFILE.charToMorse['۱'])
    }

    @Test
    fun `persian hello encodes`() {
        val result = MorseCode.encodeDetailed("سلام", PersianMorse.PROFILE)
        assertTrue(result.isComplete)
        // س ل ا م
        assertEquals("... .-.. .- --", result.morse)
    }
}
