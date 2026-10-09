package com.morsetranslator.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.ui.screens.HistoryScreen
import com.morsetranslator.app.ui.screens.LearnScreen
import com.morsetranslator.app.ui.screens.SettingsScreen
import com.morsetranslator.app.ui.screens.TranslateScreen

/** Input pre-filled from the history screen. */
data class Prefill(val input: String, val textToMorse: Boolean)

private data class Destination(val route: String, val labelRes: Int, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(repository: SettingsRepository, player: MorsePlayer) {
    val navController = rememberNavController()
    val destinations = listOf(
        Destination("translate", R.string.nav_translate, Icons.Filled.Translate),
        Destination("learn", R.string.nav_learn, Icons.Filled.MenuBook),
        Destination("history", R.string.nav_history, Icons.Filled.History)
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var pendingPrefill by remember { mutableStateOf<Prefill?>(null) }

    val titleRes = when (currentRoute) {
        "learn" -> R.string.nav_learn
        "history" -> R.string.nav_history
        "settings" -> R.string.title_settings
        else -> R.string.nav_translate
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                actions = {
                    if (currentRoute != "settings") {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.title_settings)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentRoute in destinations.map { it.route }) {
                NavigationBar {
                    destinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo("translate") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    dest.icon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "translate",
            modifier = Modifier.padding(padding)
        ) {
            composable("translate") {
                TranslateScreen(
                    repository = repository,
                    player = player,
                    prefill = pendingPrefill,
                    onPrefillConsumed = { pendingPrefill = null }
                )
            }
            composable("learn") {
                LearnScreen(repository = repository, player = player)
            }
            composable("history") {
                HistoryScreen(
                    repository = repository,
                    onSelect = { input, textToMorse ->
                        pendingPrefill = Prefill(input, textToMorse)
                        navController.navigate("translate") {
                            popUpTo("translate") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("settings") {
                SettingsScreen(repository = repository)
            }
        }
    }
}
