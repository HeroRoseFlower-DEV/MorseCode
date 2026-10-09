package com.morsetranslator.app.learn

import kotlin.math.max
import kotlin.random.Random

/**
 * Simple, transparent local review scheduler (a simplified SM-2 style).
 *
 * This is deliberately NOT presented as a sophisticated scientific algorithm:
 * it tracks per-character attempts/correct answers, stretches the review
 * interval after consecutive successes and resets it after a mistake, and
 * prioritizes characters the user misses most. Everything is stored locally
 * and the model is small, bounded and recoverable.
 */
object ReviewScheduler {

    data class CharStats(
        val attempts: Int = 0,
        val correct: Int = 0,
        /** Consecutive correct answers. */
        val streak: Int = 0,
        /** Current review interval in days. */
        val intervalDays: Double = 1.0,
        /** Local epoch day when this character is next due for review. */
        val dueEpochDay: Long = 0L
    ) {
        /** 0..1; 0.5 when never attempted. */
        val accuracy: Double
            get() = if (attempts == 0) 0.5 else correct.toDouble() / attempts
    }

    /**
     * Pure state transition for one answered review. [todayEpochDay] is the
     * local calendar day (e.g. `LocalDate.now().toEpochDay()`).
     */
    fun update(stats: CharStats, correct: Boolean, todayEpochDay: Long): CharStats {
        val attempts = stats.attempts + 1
        return if (correct) {
            val streak = stats.streak + 1
            val interval = when {
                attempts <= 1 -> 1.0
                streak == 2 -> 3.0
                else -> (stats.intervalDays * 2.2).coerceAtMost(60.0)
            }
            CharStats(
                attempts = attempts,
                correct = stats.correct + 1,
                streak = streak,
                intervalDays = interval,
                dueEpochDay = max(todayEpochDay + 1, todayEpochDay + interval.toLong())
            )
        } else {
            CharStats(
                attempts = attempts,
                correct = stats.correct,
                streak = 0,
                intervalDays = 1.0,
                dueEpochDay = todayEpochDay + 1
            )
        }
    }

    /**
     * Picks the next review target from [candidates].
     * - Characters due for review come first; among them (and otherwise)
     *   weaker characters are weighted more heavily (weighted random).
     * - Pure and deterministic for a fixed [random] seed (unit-testable).
     */
    fun pickNext(
        all: Map<Char, CharStats>,
        candidates: List<Char>,
        todayEpochDay: Long,
        random: Random = Random.Default
    ): Char {
        require(candidates.isNotEmpty())
        val due = candidates.filter { (all[it]?.dueEpochDay ?: 0L) <= todayEpochDay }
        val pool = if (due.isNotEmpty()) due else candidates
        // Weight: miss-rate driven. Never-attempted chars get a medium weight
        // so new material still appears.
        val weights = pool.map { c ->
            val s = all[c]
            if (s == null || s.attempts == 0) 1.0
            else 0.4 + (1.0 - s.accuracy) * 3.0
        }
        val total = weights.sum()
        var r = random.nextDouble() * total
        for (i in pool.indices) {
            r -= weights[i]
            if (r <= 0) return pool[i]
        }
        return pool.last()
    }

    /** Characters sorted weakest-first, for the "needs work" summary. */
    fun weakest(
        all: Map<Char, CharStats>,
        candidates: List<Char>,
        limit: Int = 3
    ): List<Pair<Char, CharStats>> =
        candidates.mapNotNull { c -> all[c]?.let { c to it } }
            .filter { it.second.attempts > 0 }
            .sortedBy { it.second.accuracy }
            .take(limit)

    /** How many candidates are due for review today. */
    fun dueCount(
        all: Map<Char, CharStats>,
        candidates: List<Char>,
        todayEpochDay: Long
    ): Int = candidates.count { (all[it]?.dueEpochDay ?: 0L) <= todayEpochDay }
}
