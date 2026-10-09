package com.morsetranslator.app

import com.morsetranslator.app.learn.ReviewScheduler
import com.morsetranslator.app.learn.ReviewScheduler.CharStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReviewSchedulerTest {

    private val today = 20_000L

    @Test
    fun `first correct answer starts a one-day interval`() {
        val next = ReviewScheduler.update(CharStats(), correct = true, today)
        assertEquals(1, next.attempts)
        assertEquals(1, next.correct)
        assertEquals(1, next.streak)
        assertEquals(1.0, next.intervalDays, 0.001)
        assertEquals(today + 1, next.dueEpochDay)
    }

    @Test
    fun `second consecutive correct stretches to three days`() {
        var s = ReviewScheduler.update(CharStats(), true, today)
        s = ReviewScheduler.update(s, true, today)
        assertEquals(2, s.streak)
        assertEquals(3.0, s.intervalDays, 0.001)
    }

    @Test
    fun `later correct answers grow the interval multiplicatively`() {
        var s = CharStats()
        repeat(3) { s = ReviewScheduler.update(s, true, today) }
        assertEquals(3.0 * 2.2, s.intervalDays, 0.001)
    }

    @Test
    fun `interval is capped at 60 days`() {
        var s = CharStats()
        repeat(20) { s = ReviewScheduler.update(s, true, today) }
        assertTrue(s.intervalDays <= 60.0)
    }

    @Test
    fun `a mistake resets streak and interval`() {
        var s = CharStats()
        repeat(3) { s = ReviewScheduler.update(s, true, today) }
        s = ReviewScheduler.update(s, false, today)
        assertEquals(0, s.streak)
        assertEquals(1.0, s.intervalDays, 0.001)
        assertEquals(4, s.attempts)
        assertEquals(3, s.correct)
        assertEquals(today + 1, s.dueEpochDay)
    }

    @Test
    fun `accuracy defaults to one half before any attempt`() {
        assertEquals(0.5, CharStats().accuracy, 0.001)
        assertEquals(1.0, CharStats(attempts = 4, correct = 4).accuracy, 0.001)
        assertEquals(0.25, CharStats(attempts = 4, correct = 1).accuracy, 0.001)
    }

    @Test
    fun `pickNext prefers due characters`() {
        val stats = mapOf(
            'A' to CharStats(dueEpochDay = today + 10), // not due
            'B' to CharStats(dueEpochDay = today - 1) // due
        )
        // Deterministic seed; B must win because A is not in the pool.
        val pick = ReviewScheduler.pickNext(stats, listOf('A', 'B'), today, Random(0))
        assertEquals('B', pick)
    }

    @Test
    fun `pickNext weights weaker characters more heavily`() {
        val stats = mapOf(
            'A' to CharStats(attempts = 10, correct = 10, dueEpochDay = 0),
            'B' to CharStats(attempts = 10, correct = 0, dueEpochDay = 0)
        )
        // Over many seeded draws the weak character should dominate.
        val counts = mutableMapOf<Char, Int>()
        repeat(200) { i ->
            val pick = ReviewScheduler.pickNext(stats, listOf('A', 'B'), today, Random(i))
            counts[pick] = (counts[pick] ?: 0) + 1
        }
        assertTrue("weak char should be picked more often: $counts", (counts['B'] ?: 0) > 150)
    }

    @Test
    fun `pickNext includes never-attempted characters`() {
        val pick = ReviewScheduler.pickNext(emptyMap(), listOf('A', 'B', 'C'), today, Random(3))
        assertTrue(pick in listOf('A', 'B', 'C'))
    }

    @Test
    fun `weakest sorts by accuracy ascending`() {
        val stats = mapOf(
            'A' to CharStats(attempts = 10, correct = 9),
            'B' to CharStats(attempts = 10, correct = 2),
            'C' to CharStats(attempts = 10, correct = 5)
        )
        val weakest = ReviewScheduler.weakest(stats, listOf('A', 'B', 'C'), 3)
        assertEquals(listOf('B', 'C', 'A'), weakest.map { it.first })
    }

    @Test
    fun `dueCount counts characters due today or earlier`() {
        val stats = mapOf(
            'A' to CharStats(dueEpochDay = today),
            'B' to CharStats(dueEpochDay = today - 5),
            'C' to CharStats(dueEpochDay = today + 5)
        )
        assertEquals(2, ReviewScheduler.dueCount(stats, listOf('A', 'B', 'C'), today))
        // Never-attempted characters count as due.
        assertEquals(3, ReviewScheduler.dueCount(stats, listOf('A', 'B', 'C', 'D'), today))
    }
}
