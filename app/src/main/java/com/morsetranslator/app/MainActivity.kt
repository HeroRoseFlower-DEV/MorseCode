package com.morsetranslator.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.ui.AppNav
import com.morsetranslator.app.ui.theme.MorseTranslatorTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: SettingsRepository
    private lateinit var player: MorsePlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = SettingsRepository(applicationContext)
        player = MorsePlayer(applicationContext)

        setContent {
            val themeMode by repository.themeMode.collectAsState(initial = 0)
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            MorseTranslatorTheme(darkTheme = darkTheme) {
                AppNav(repository = repository, player = player)
            }
        }
    }

    override fun onDestroy() {
        player.stop()
        super.onDestroy()
    }
}
