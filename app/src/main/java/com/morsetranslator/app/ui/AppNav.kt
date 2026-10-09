package com.morsetranslator.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.morsetranslator.app.R
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.ui.screens.DecodeScreen
import com.morsetranslator.app.ui.screens.HistoryScreen
import com.morsetranslator.app.ui.screens.LearnScreen
import com.morsetranslator.app.ui.screens.PracticeScreen
import com.morsetranslator.app.ui.screens.SettingsScreen
import com.morsetranslator.app.ui.screens.TranslateScreen

data class Prefill(val input: String, val textToMorse: Boolean)

private data class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(repository: SettingsRepository, player: MorsePlayer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "translate"
    var prefill by remember { mutableStateOf<Prefill?>(null) }

    val destinations = listOf(
        Destination("translate", R.string.nav_translate, Icons.Filled.Translate),
        Destination("practice", R.string.nav_practice, Icons.Filled.Quiz),
        Destination("decode", R.string.nav_decode, Icons.Filled.Mic),
        Destination("learn", R.string.nav_learn, Icons.Filled.MenuBook),
        Destination("history", R.string.nav_history, Icons.Filled.History)
    )

    val titleRes = when (currentRoute) {
        "practice" -> R.string.nav_practice
        "decode" -> R.string.nav_decode
        "learn" -> R.string.nav_learn
        "history" -> R.string.nav_history
        "settings" -> R.string.title_settings
        else -> R.string.nav_translate
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(titleRes),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    if (currentRoute == "settings") {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    }
                },
                actions = {
                    if (currentRoute != "settings") {
                        IconButton(
                            onClick = { navController.navigate("settings") }
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.title_settings)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (currentRoute != "settings") {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    destinations.forEach { d ->
                        val selected = currentRoute == d.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(d.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(d.icon, contentDescription = stringResource(d.labelRes))
                            },
                            label = { Text(stringResource(d.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
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
                    prefill = prefill,
                    onPrefillConsumed = { prefill = null }
                )
            }
            composable("practice") {
                PracticeScreen(repository = repository, player = player)
            }
            composable("decode") {
                DecodeScreen(repository = repository)
            }
            composable("learn") {
                LearnScreen(repository = repository, player = player)
            }
            composable("history") {
                HistoryScreen(
                    repository = repository,
                    onSelect = { input, textToMorse ->
                        prefill = Prefill(input, textToMorse)
                        navController.navigate("translate") {
                            popUpTo("translate") { inclusive = false }
                            launchSingleTop = true
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
