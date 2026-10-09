package com.morsetranslator.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.morsetranslator.app.ui.screens.HistoryScreen
import com.morsetranslator.app.ui.screens.LearnScreen
import com.morsetranslator.app.ui.screens.PracticeScreen
import com.morsetranslator.app.ui.screens.SettingsScreen
import com.morsetranslator.app.ui.screens.TranslateScreen
import com.morsetranslator.app.ui.theme.AuroraBackground
import com.morsetranslator.app.ui.theme.GlassIconButton
import com.morsetranslator.app.ui.theme.glassBorder
import com.morsetranslator.app.ui.theme.glassContainer

data class Prefill(val input: String, val textToMorse: Boolean)

private data class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

@Composable
fun AppNav(repository: SettingsRepository, player: MorsePlayer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "translate"
    var prefill by remember { mutableStateOf<Prefill?>(null) }

    val destinations = listOf(
        Destination("translate", R.string.nav_translate, Icons.Filled.Translate),
        Destination("practice", R.string.nav_practice, Icons.Filled.Quiz),
        Destination("learn", R.string.nav_learn, Icons.Filled.MenuBook),
        Destination("history", R.string.nav_history, Icons.Filled.History)
    )

    val titleRes = when (currentRoute) {
        "practice" -> R.string.nav_practice
        "learn" -> R.string.nav_learn
        "history" -> R.string.nav_history
        "settings" -> R.string.title_settings
        else -> R.string.nav_translate
    }

    AuroraBackground {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Glass top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                GlassIconButton(
                    icon = Icons.Filled.Settings,
                    description = stringResource(R.string.title_settings),
                    onClick = { navController.navigate("settings") },
                    size = 44.dp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
            NavHost(
                navController = navController,
                startDestination = "translate",
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() }
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

            // Floating glass bottom navigation
            if (currentRoute != "settings") {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(glassContainer())
                        .border(1.dp, glassBorder(), RoundedCornerShape(30.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEach { d ->
                        val selected = currentRoute == d.route
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    navController.navigate(d.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                d.icon,
                                contentDescription = stringResource(d.labelRes),
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                stringResource(d.labelRes),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
}
