package com.morsetranslator.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * DataStore-backed repository tests on the JVM (no Android framework needed:
 * the repository takes a DataStore directly; production wires the real one
 * via [SettingsRepository.create]).
 */
class SettingsRepositoryTest {

    private lateinit var scope: CoroutineScope
    private lateinit var repo: SettingsRepository
    private lateinit var file: File

    @Before
    fun setup() {
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        file = File.createTempFile("test_prefs", ".preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repo = SettingsRepository(dataStore)
    }

    @After
    fun teardown() {
        scope.cancel()
        file.delete()
    }

    // ------------------------------------------------------------- settings

    @Test
    fun `defaults are sane`() = runTest {
        assertEquals(18, repo.wpm.first())
        assertEquals(700, repo.toneHz.first())
        assertEquals(0, repo.themeMode.first())
        assertEquals(MorseCode.INTERNATIONAL.id, repo.profileId.first())
        assertEquals(0, repo.practiceBest.first())
        assertEquals(0.10, repo.decoderThreshold.first(), 0.0001)
        assertTrue(repo.history.first().isEmpty())
        assertNull(repo.dailyDismissedDate.first())
    }

    @Test
    fun `wpm is clamped on write`() = runTest {
        repo.setWpm(100)
        assertEquals(40, repo.wpm.first())
        repo.setWpm(1)
        assertEquals(5, repo.wpm.first())
        repo.setWpm(20)
        assertEquals(20, repo.wpm.first())
    }

    @Test
    fun `tone is clamped on write`() = runTest {
        repo.setToneHz(5000)
        assertEquals(1200, repo.toneHz.first())
        repo.setToneHz(10)
        assertEquals(300, repo.toneHz.first())
    }

    @Test
    fun `unknown theme and profile ids fall back to defaults`() = runTest {
        repo.setThemeMode(7)
        assertEquals(0, repo.themeMode.first())
        repo.setProfileId("klingon")
        assertEquals(MorseCode.INTERNATIONAL.id, repo.profileId.first())
        repo.setProfileId("persian")
        assertEquals("persian", repo.profileId.first())
    }

    @Test
    fun `practice best only moves upward`() = runTest {
        repo.setPracticeBest(5)
        assertEquals(5, repo.practiceBest.first())
        repo.setPracticeBest(3)
        assertEquals(5, repo.practiceBest.first())
        repo.setPracticeBest(9)
        assertEquals(9, repo.practiceBest.first())
    }

    @Test
    fun `decoder threshold is clamped`() = runTest {
        repo.setDecoderThreshold(0.99)
        assertEquals(0.30, repo.decoderThreshold.first(), 0.0001)
        repo.setDecoderThreshold(0.001)
        assertEquals(0.03, repo.decoderThreshold.first(), 0.0001)
    }

    @Test
    fun `daily dismissed date round-trips`() = runTest {
        repo.setDailyDismissed("2026-10-09")
        assertEquals("2026-10-09", repo.dailyDismissedDate.first())
        repo.setDailyDismissed(null)
        assertNull(repo.dailyDismissedDate.first())
    }

    // -------------------------------------------------------------- history

    @Test
    fun `history is newest-first and deduplicates full operations`() = runTest {
        repo.addHistory("HI", ".... ..", true)
        repo.addHistory("HI", ".... ..", true) // consecutive duplicate
        var history = repo.history.first()
        assertEquals(1, history.size)

        // Same text, other direction = a different operation, kept.
        repo.addHistory(".... ..", "HI", false)
        history = repo.history.first()
        assertEquals(2, history.size)
        assertEquals(".... ..", history[0].input)
        assertFalse(history[0].textToMorse)
    }

    @Test
    fun `history entries carry their alphabet profile`() = runTest {
        repo.addHistory("سلام", "... .-.. .- --", true, "persian")
        val history = repo.history.first()
        assertEquals("persian", history[0].profileId)
    }

    @Test
    fun `blank inputs are not stored`() = runTest {
        repo.addHistory("", "", true)
        repo.addHistory("  ", "....", true)
        assertTrue(repo.history.first().isEmpty())
    }

    @Test
    fun `history is capped at the limit`() = runTest {
        repeat(SettingsRepository.HISTORY_LIMIT + 5) { i ->
            repo.addHistory("msg $i", "morse $i", true)
        }
        assertEquals(SettingsRepository.HISTORY_LIMIT, repo.history.first().size)
    }

    @Test
    fun `remove toggleFavorite and clear work`() = runTest {
        repo.addHistory("A", ".-", true)
        repo.addHistory("B", "-...", true)
        var history = repo.history.first()
        val id = history[0].id

        repo.toggleFavorite(id)
        assertTrue(repo.history.first().first { it.id == id }.isFavorite)
        repo.toggleFavorite(id)
        assertFalse(repo.history.first().first { it.id == id }.isFavorite)

        repo.removeHistory(id)
        assertEquals(1, repo.history.first().size)

        repo.clearHistory()
        assertTrue(repo.history.first().isEmpty())
    }

    @Test
    fun `export text contains entries`() = runTest {
        repo.addHistory("SOS", "... --- ...", true)
        val text = repo.exportHistoryText(repo.history.first())
        assertTrue(text.contains("SOS"))
        assertTrue(text.contains("... --- ..."))
    }

    // ---------------------------------------------------------- review stats

    @Test
    fun `review answers accumulate and reset`() = runTest {
        val today = 20_000L
        repo.recordReviewAnswer('A', true, today)
        repo.recordReviewAnswer('A', true, today)
        repo.recordReviewAnswer('B', false, today)

        val stats = repo.reviewStats.first()
        assertEquals(2, stats['A']?.attempts)
        assertEquals(2, stats['A']?.streak)
        assertEquals(1, stats['B']?.attempts)
        assertEquals(0, stats['B']?.streak)
        assertEquals(1.0, stats['B']?.intervalDays ?: -1.0, 0.001)

        repo.clearReviewStats()
        assertTrue(repo.reviewStats.first().isEmpty())
    }
}
