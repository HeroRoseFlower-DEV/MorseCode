package com.morsetranslator.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.morsetranslator.app.learn.ReviewScheduler
import com.morsetranslator.app.morse.MorseCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val id: Long,
    val input: String,
    val output: String,
    val textToMorse: Boolean,
    /** Alphabet profile id used for this entry (default for legacy entries). */
    val profileId: String = MorseCode.INTERNATIONAL.id,
    val timestamp: Long,
    val isFavorite: Boolean = false
)

/**
 * App settings, translation history, practice scores and review statistics,
 * persisted with DataStore Preferences.
 *
 * Robustness rules:
 * - Every value is validated when *loaded* (not only when written), so
 *   corrupt, missing or out-of-range values fall back to safe defaults
 *   instead of crashing or misbehaving.
 * - History and review-stats JSON are parsed defensively: one malformed
 *   entry never destroys the rest.
 * - The UI should keep a responsive local value while a slider is dragged
 *   and call the setters once on release (see slider usage in Settings);
 *   setters themselves skip no-change writes.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val WPM = intPreferencesKey("wpm")
        val TONE_HZ = intPreferencesKey("tone_hz")
        val THEME_MODE = intPreferencesKey("theme_mode") // 0 = system, 1 = light, 2 = dark
        val PROFILE_ID = stringPreferencesKey("alphabet_profile")
        val HISTORY = stringPreferencesKey("history_json")
        val PRACTICE_BEST = intPreferencesKey("practice_best")
        val REVIEW_STATS = stringPreferencesKey("review_stats_json")
        val DECODER_THRESHOLD = doublePreferencesKey("decoder_threshold")
        val DAILY_DISMISSED = stringPreferencesKey("daily_dismissed_date")
    }

    companion object {
        private val Context.dataStore by preferencesDataStore(name = "morse_prefs")

        /** Production entry point. Tests inject a DataStore directly. */
        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.dataStore)

        const val DEFAULT_WPM = 18
        const val DEFAULT_TONE_HZ = 700
        const val MIN_WPM = 5
        const val MAX_WPM = 40
        const val MIN_TONE_HZ = 300
        const val MAX_TONE_HZ = 1200
        const val DEFAULT_DECODER_THRESHOLD = 0.10
        const val HISTORY_LIMIT = 100
    }

    // ------------------------------------------------------------- settings

    /** Loaded value is always clamped to the valid range. */
    val wpm: Flow<Int> = dataStore.data.map {
        (it[Keys.WPM] ?: DEFAULT_WPM).coerceIn(MIN_WPM, MAX_WPM)
    }

    val toneHz: Flow<Int> = dataStore.data.map {
        (it[Keys.TONE_HZ] ?: DEFAULT_TONE_HZ).coerceIn(MIN_TONE_HZ, MAX_TONE_HZ)
    }

    /** Unknown stored values fall back to system (0). */
    val themeMode: Flow<Int> = dataStore.data.map {
        (it[Keys.THEME_MODE] ?: 0).let { v -> if (v in 0..2) v else 0 }
    }

    /** Unknown profile ids fall back to the international profile. */
    val profileId: Flow<String> = dataStore.data.map { prefs ->
        val id = prefs[Keys.PROFILE_ID] ?: MorseCode.INTERNATIONAL.id
        if (MorseCode.PROFILES.any { it.id == id }) id
        else MorseCode.INTERNATIONAL.id
    }

    val practiceBest: Flow<Int> = dataStore.data.map {
        (it[Keys.PRACTICE_BEST] ?: 0).coerceAtLeast(0)
    }

    /** Decoder energy threshold, 0.03..0.30. */
    val decoderThreshold: Flow<Double> = dataStore.data.map {
        (it[Keys.DECODER_THRESHOLD] ?: DEFAULT_DECODER_THRESHOLD).coerceIn(0.03, 0.30)
    }

    /** Local date (ISO) for which the daily message card was dismissed. */
    val dailyDismissedDate: Flow<String?> = dataStore.data.map {
        it[Keys.DAILY_DISMISSED]
    }

    suspend fun setWpm(value: Int) {
        val v = value.coerceIn(MIN_WPM, MAX_WPM)
        dataStore.edit { if (it[Keys.WPM] != v) it[Keys.WPM] = v }
    }

    suspend fun setToneHz(value: Int) {
        val v = value.coerceIn(MIN_TONE_HZ, MAX_TONE_HZ)
        dataStore.edit { if (it[Keys.TONE_HZ] != v) it[Keys.TONE_HZ] = v }
    }

    suspend fun setThemeMode(value: Int) {
        val v = if (value in 0..2) value else 0
        dataStore.edit { if (it[Keys.THEME_MODE] != v) it[Keys.THEME_MODE] = v }
    }

    suspend fun setProfileId(id: String) {
        val v = if (MorseCode.PROFILES.any { it.id == id }) id
        else MorseCode.INTERNATIONAL.id
        dataStore.edit { if (it[Keys.PROFILE_ID] != v) it[Keys.PROFILE_ID] = v }
    }

    suspend fun setPracticeBest(value: Int) {
        dataStore.edit { prefs ->
            val current = (prefs[Keys.PRACTICE_BEST] ?: 0).coerceAtLeast(0)
            if (value > current) prefs[Keys.PRACTICE_BEST] = value
        }
    }

    suspend fun setDecoderThreshold(value: Double) {
        val v = value.coerceIn(0.03, 0.30)
        dataStore.edit { if (it[Keys.DECODER_THRESHOLD] != v) it[Keys.DECODER_THRESHOLD] = v }
    }

    suspend fun setDailyDismissed(dateIso: String?) {
        dataStore.edit { prefs ->
            if (dateIso == null) prefs.remove(Keys.DAILY_DISMISSED)
            else prefs[Keys.DAILY_DISMISSED] = dateIso
        }
    }

    // --------------------------------------------------------------- history

    val history: Flow<List<HistoryItem>> =
        dataStore.data.map { prefs -> parseHistory(prefs[Keys.HISTORY].orEmpty()) }

    /**
     * Deduplication is based on the full meaningful operation
     * (input + direction + profile + result), not just raw input, so
     * translating the same text in the other direction (or another alphabet)
     * is kept as its own entry.
     */
    suspend fun addHistory(
        input: String,
        output: String,
        textToMorse: Boolean,
        profileId: String = MorseCode.INTERNATIONAL.id
    ) {
        if (input.isBlank() || output.isBlank()) return
        dataStore.edit { prefs ->
            val current = parseHistory(prefs[Keys.HISTORY].orEmpty()).toMutableList()
            val first = current.firstOrNull()
            if (first != null && first.input == input &&
                first.output == output && first.textToMorse == textToMorse &&
                first.profileId == profileId
            ) {
                return@edit // consecutive duplicate of the same operation
            }
            current.add(
                0,
                HistoryItem(
                    id = System.currentTimeMillis(),
                    input = input,
                    output = output,
                    textToMorse = textToMorse,
                    profileId = profileId,
                    timestamp = System.currentTimeMillis(),
                    isFavorite = false
                )
            )
            // removeAt(lastIndex): List.removeLast() is a Java 21 API and
            // does not exist on the Java 17 runtime used for unit tests.
            while (current.size > HISTORY_LIMIT) current.removeAt(current.lastIndex)
            prefs[Keys.HISTORY] = historyToJson(current)
        }
    }

    suspend fun removeHistory(id: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.HISTORY] = historyToJson(
                parseHistory(prefs[Keys.HISTORY].orEmpty()).filterNot { it.id == id }
            )
        }
    }

    suspend fun toggleFavorite(id: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.HISTORY] = historyToJson(
                parseHistory(prefs[Keys.HISTORY].orEmpty()).map {
                    if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it
                }
            )
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { it[Keys.HISTORY] = "[]" }
    }

    /** Plain-text export for sharing; contains no hidden formatting. */
    fun exportHistoryText(items: List<HistoryItem>): String {
        val sb = StringBuilder()
        sb.appendLine("Morse Translator — history export")
        for (h in items) {
            val dir = if (h.textToMorse) "text→morse" else "morse→text"
            sb.appendLine("—".repeat(24))
            sb.appendLine("[$dir / ${h.profileId}]")
            sb.appendLine("in:  ${h.input}")
            sb.appendLine("out: ${h.output}")
        }
        return sb.toString()
    }

    private fun parseHistory(json: String): List<HistoryItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    try {
                        val o: JSONObject = arr.getJSONObject(i)
                        add(
                            HistoryItem(
                                id = o.getLong("id"),
                                input = o.getString("input"),
                                output = o.getString("output"),
                                textToMorse = o.getBoolean("t2m"),
                                profileId = o.optString("profile", MorseCode.INTERNATIONAL.id),
                                timestamp = o.getLong("ts"),
                                isFavorite = o.optBoolean("fav", false)
                            )
                        )
                    } catch (_: Exception) {
                        // One malformed entry must not destroy the rest.
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun historyToJson(items: List<HistoryItem>): String {
        val arr = JSONArray()
        for (h in items) {
            arr.put(
                JSONObject().apply {
                    put("id", h.id)
                    put("input", h.input)
                    put("output", h.output)
                    put("t2m", h.textToMorse)
                    put("profile", h.profileId)
                    put("ts", h.timestamp)
                    put("fav", h.isFavorite)
                }
            )
        }
        return arr.toString()
    }

    // ---------------------------------------------------------- review stats

    val reviewStats: Flow<Map<Char, ReviewScheduler.CharStats>> =
        dataStore.data.map { prefs -> parseReviewStats(prefs[Keys.REVIEW_STATS].orEmpty()) }

    suspend fun recordReviewAnswer(char: Char, correct: Boolean, todayEpochDay: Long) {
        dataStore.edit { prefs ->
            val current = parseReviewStats(prefs[Keys.REVIEW_STATS].orEmpty()).toMutableMap()
            // Bounded: keep at most 200 entries (more than any alphabet).
            if (current.size >= 200 && char !in current) {
                current.remove(current.keys.first())
            }
            current[char] = ReviewScheduler.update(
                current[char] ?: ReviewScheduler.CharStats(),
                correct,
                todayEpochDay
            )
            prefs[Keys.REVIEW_STATS] = reviewStatsToJson(current)
        }
    }

    suspend fun clearReviewStats() {
        dataStore.edit { it[Keys.REVIEW_STATS] = "{}" }
    }

    private fun parseReviewStats(json: String): Map<Char, ReviewScheduler.CharStats> {
        if (json.isBlank()) return emptyMap()
        return try {
            val o = JSONObject(json)
            buildMap {
                for (key in o.keys()) {
                    try {
                        if (key.length != 1) continue
                        val s = o.getJSONObject(key)
                        put(
                            key[0],
                            ReviewScheduler.CharStats(
                                attempts = s.optInt("a", 0).coerceAtLeast(0),
                                correct = s.optInt("c", 0).coerceAtLeast(0),
                                streak = s.optInt("s", 0).coerceAtLeast(0),
                                intervalDays = s.optDouble("i", 1.0).coerceIn(0.5, 90.0),
                                dueEpochDay = s.optLong("d", 0L).coerceAtLeast(0L)
                            )
                        )
                    } catch (_: Exception) {
                        // Skip malformed entries, keep the rest.
                    }
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun reviewStatsToJson(stats: Map<Char, ReviewScheduler.CharStats>): String {
        return JSONObject().apply {
            for ((char, s) in stats) {
                put(
                    char.toString(),
                    JSONObject().apply {
                        put("a", s.attempts)
                        put("c", s.correct)
                        put("s", s.streak)
                        put("i", s.intervalDays)
                        put("d", s.dueEpochDay)
                    }
                )
            }
        }.toString()
    }
}
