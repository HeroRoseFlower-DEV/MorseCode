package com.morsetranslator.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.ui.Prefill
import com.morsetranslator.app.ui.screens.TranslateScreen
import com.morsetranslator.app.ui.theme.MorseTranslatorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose tests for the translation flow.
 *
 * These run on an emulator/device (`:app:connectedDebugAndroidTest`).
 * CI compiles them (`:app:assembleAndroidTest`) so regressions in the
 * screen's wiring fail the build even without an emulator.
 */
@RunWith(AndroidJUnit4::class)
class TranslateScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setTranslateScreen(prefill: Prefill? = null) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository.create(context)
        val player = MorsePlayer(context)
        composeTestRule.setContent {
            MorseTranslatorTheme {
                TranslateScreen(
                    repository = repository,
                    player = player,
                    prefill = prefill,
                    onPrefillConsumed = {}
                )
            }
        }
        player.stop()
    }

    @Test
    fun typingTextShowsMorseOutput() {
        setTranslateScreen()
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule
            .onNodeWithText(context.getString(R.string.input_hint_text))
            .performTextInput("SOS")

        composeTestRule
            .onNodeWithText("... --- ...", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun switchingDirectionChangesLabels() {
        setTranslateScreen()
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Default is text->morse; switch to morse->text.
        composeTestRule
            .onNodeWithTag("mode_option_1")
            .performClick()

        composeTestRule
            .onNodeWithText(context.getString(R.string.input_label_morse))
            .assertIsDisplayed()
    }

    @Test
    fun unsupportedCharactersShowAWarning() {
        setTranslateScreen()
        val context = ApplicationProvider.getApplicationContext<Context>()

        composeTestRule
            .onNodeWithText(context.getString(R.string.input_hint_text))
            .performTextInput("héllo")

        // The unsupported character must be surfaced, not silently dropped.
        composeTestRule
            .onNodeWithText(
                context.getString(R.string.validation_unsupported_title, 1).substringBefore(":"),
                substring = true
            )
            .assertIsDisplayed()
    }

    @Test
    fun prefillFromHistoryLoadsInput() {
        setTranslateScreen(Prefill("SOS", textToMorse = true))

        composeTestRule
            .onNodeWithText("... --- ...", substring = true)
            .assertIsDisplayed()
    }
}
