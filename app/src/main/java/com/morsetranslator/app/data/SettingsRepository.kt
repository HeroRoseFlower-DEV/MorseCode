package com.morsetranslator.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "morse_prefs")

data class HistoryItem(
    val id: Long,
    val input: String,
    val output: String,
    val textToMorse: Boolean,
    val timestamp: Long,
    val isFavorite: Boolean = false
)

/** App settings, translation history and practice stats, persisted with DataStore Preferences. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val WPM = intPreferencesKey("wpm")
        val TONE_HZ = intPreferencesKey("tone_hz")
        val THEME_MODE = intPreferencesKey("theme_mode") // 0 = system, 1 = light, 2 = dark
        val HISTORY = stringPreferencesKey("history_json")
        val PRACTICE_BEST = intPreferencesKey("practice_best")
    }

    val wpm: Flow<Int> = context.dataStore.data.map { it[Keys.WPM] ?: 18 }
    val toneHz: Flow<Int> = context.dataStore.data.map { it[Keys.TONE_HZ] ?: 700 }
    val themeMode: Flow<Int> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: 0 }
    val practiceBest: Flow<Int> = context.dataStore.data.map { it[Keys.PRACTICE_BEST] ?: 0 }

    suspend fun setWpm(value: Int) {
        context.dataStore.edit { it[Keys.WPM] = value.coerceIn(5, 40) }
    }

    suspend fun setToneHz(value: Int) {
        context.dataStore.edit { it[Keys.TONE_HZ] = value.coerceIn(300, 1200) }
    }

    suspend fun setThemeMode(value: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = value.coerceIn(0, 2) }
    }

    suspend fun setPracticeBest(value: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PRACTICE_BEST] ?: 0
            if (value > current) prefs[Keys.PRACTICE_BEST] = value
        }
    }

    // --------------------------------------------------------------- history

    val history: Flow<List<HistoryItem>> =
        context.dataStore.data.map { prefs -> parseHistory(prefs[Keys.HISTORY].orEmpty()) }

    suspend fun addHistory(input: String, output: String, textToMorse: Boolean) {
        if (input.isBlank() || output.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = parseHistory(prefs[Keys.HISTORY].orEmpty()).toMutableList()
            if (current.firstOrNull()?.input == input) return@edit // skip consecutive duplicates
            current.add(
                0,
                HistoryItem(
                    id = System.currentTimeMillis(),
                    input = input,
                    output = output,
                    textToMorse = textToMorse,
                    timestamp = System.currentTimeMillis(),
                    isFavorite = false
                )
            )
            while (current.size > 100) current.removeLast()
            prefs[Keys.HISTORY] = toJson(current)
        }
    }

    suspend fun removeHistory(id: Long) {
        context.dataStore.edit { prefs ->
            val current = parseHistory(prefs[Keys.HISTORY].orEmpty()).filterNot { it.id == id }
            prefs[Keys.HISTORY] = toJson(current)
        }
    }

    suspend fun toggleFavorite(id: Long) {
        context.dataStore.edit { prefs ->
            val current = parseHistory(prefs[Keys.HISTORY].orEmpty()).map {
                if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it
            }
            prefs[Keys.HISTORY] = toJson(current)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it[Keys.HISTORY] = "[]" }
    }

    private fun parseHistory(json: String): List<HistoryItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o: JSONObject = arr.getJSONObject(i)
                HistoryItem(
                    id = o.getLong("id"),
                    input = o.getString("input"),
                    output = o.getString("output"),
                    textToMorse = o.getBoolean("t2m"),
                    timestamp = o.getLong("ts"),
                    isFavorite = o.optBoolean("fav", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toJson(items: List<HistoryItem>): String {
        val arr = JSONArray()
        for (h in items) {
            arr.put(
                JSONObject().apply {
                    put("id", h.id)
                    put("input", h.input)
                    put("output", h.output)
                    put("t2m", h.textToMorse)
                    put("ts", h.timestamp)
                    put("fav", h.isFavorite)
                }
            )
        }
        return arr.toString()
    }
}
