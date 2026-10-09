package com.morsetranslator.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Calm, light-first palette (see Design.kt):
 * warm ivory surfaces, navy/slate text, muted turquoise primary,
 * restrained gold secondary, neutral grey borders.
 *
 * A designed palette is used instead of dynamic colors so contrast and
 * hierarchy stay predictable in both themes.
 */
private val CalmLightColors = lightColorScheme(
    primary = Color(0xFF2E9C8B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EFE9),
    onPrimaryContainer = Color(0xFF0F3D37),
    secondary = Color(0xFF9A7B1E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8C8),
    onSecondaryContainer = Color(0xFF4A3A0E),
    background = Color(0xFFFAF7F1),
    onBackground = Color(0xFF223041),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF223041),
    surfaceVariant = Color(0xFFF1EDE2),
    onSurfaceVariant = Color(0xFF5F6B7A),
    outline = Color(0xFFC9C2B2),
    outlineVariant = Color(0xFFE3DCCD),
    error = Color(0xFFBA1A1A)
)

private val CalmDarkColors = darkColorScheme(
    primary = Color(0xFF63BFAE),
    onPrimary = Color(0xFF0B2B26),
    primaryContainer = Color(0xFF1E4A43),
    onPrimaryContainer = Color(0xFFC9EDE4),
    secondary = Color(0xFFD3AC55),
    onSecondary = Color(0xFF3A2C07),
    secondaryContainer = Color(0xFF4A3A14),
    onSecondaryContainer = Color(0xFFF0DFAE),
    background = Color(0xFF10151C),
    onBackground = Color(0xFFECE7DA),
    surface = Color(0xFF1A222C),
    onSurface = Color(0xFFECE7DA),
    surfaceVariant = Color(0xFF232E3A),
    onSurfaceVariant = Color(0xFF9AA5B4),
    outline = Color(0xFF3A4654),
    outlineVariant = Color(0xFF2B3540),
    error = Color(0xFFFFB4AB)
)

@Composable
fun MorseTranslatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CalmDarkColors else CalmLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent system bars for the edge-to-edge layout.
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
