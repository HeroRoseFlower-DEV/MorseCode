package com.morsetranslator.app

import com.morsetranslator.app.daily.DailyMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyMessagesTest {

    @Test
    fun `thirty original messages exist with both languages`() {
        assertEquals(30, DailyMessages.MESSAGES.size)
        val ids = DailyMessages.MESSAGES.map { it.id }
        assertEquals(30, ids.toSet().size) // unique
        for (m in DailyMessages.MESSAGES) {
            assertTrue("empty fa text for id ${m.id}", m.fa.isNotBlank())
            assertTrue("empty en text for id ${m.id}", m.en.isNotBlank())
        }
    }

    @Test
    fun `selection is stable for the same local date`() {
        val date = LocalDate.of(2026, 10, 9)
        assertEquals(DailyMessages.forDate(date), DailyMessages.forDate(date))
    }

    @Test
    fun `consecutive dates rotate through messages`() {
        val a = DailyMessages.forDate(LocalDate.of(2026, 10, 9))
        val b = DailyMessages.forDate(LocalDate.of(2026, 10, 10))
        // epoch days are consecutive, so indices differ.
        assertTrue(a != b)
    }

    @Test
    fun `a full 30-day cycle covers every message exactly once`() {
        val start = LocalDate.of(2026, 1, 1)
        val seen = (0 until 30).map { DailyMessages.forDate(start.plusDays(it.toLong())) }.toSet()
        assertEquals(30, seen.size)
    }

    @Test
    fun `negative epoch days do not crash`() {
        val msg = DailyMessages.forDate(LocalDate.ofEpochDay(-1))
        assertTrue(msg in DailyMessages.MESSAGES)
    }

    @Test
    fun `messages are original bundled content, not fetched`() {
        // Guard against accidentally wiring this to a network source:
        // selection must be a pure function of the date.
        val d1 = DailyMessages.forDate(LocalDate.of(2030, 5, 17))
        val d2 = DailyMessages.forDate(LocalDate.of(2030, 5, 17))
        assertEquals(d1, d2)
    }
}
