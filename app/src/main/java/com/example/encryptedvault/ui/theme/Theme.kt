package com.example.encryptedvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Deep ink background, indigo primary, mint secondary, rose tertiary.
private val Dark = darkColorScheme(
    primary = Color(0xFF8FA8FF), onPrimary = Color(0xFF0B1B5C),
    primaryContainer = Color(0xFF26337A), onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF5EE0C8), onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF145246), onSecondaryContainer = Color(0xFFB4F5E6),
    tertiary = Color(0xFFFF8FB1), onTertiary = Color(0xFF5B0F2D),
    background = Color(0xFF0C0F14), onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF12161D), onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1C222C), onSurfaceVariant = Color(0xFFA9B0BF),
    surfaceContainer = Color(0xFF171C24), surfaceContainerHigh = Color(0xFF1F2530),
    outline = Color(0xFF3A4252), outlineVariant = Color(0xFF2A303C),
    error = Color(0xFFFF8A80), errorContainer = Color(0xFF5C1F1B), onErrorContainer = Color(0xFFFFDAD6),
)

private val Light = lightColorScheme(
    primary = Color(0xFF4260F0), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E6FF), onPrimaryContainer = Color(0xFF0B1B5C),
    secondary = Color(0xFF00907A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F4EA), onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFD6336C), onTertiary = Color.White,
    background = Color(0xFFF5F7FC), onBackground = Color(0xFF12161D),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF12161D),
    surfaceVariant = Color(0xFFE9ECF5), onSurfaceVariant = Color(0xFF515A6B),
    surfaceContainer = Color(0xFFEEF1F9), surfaceContainerHigh = Color(0xFFE6EAF4),
    outline = Color(0xFFB4BBCB), outlineVariant = Color(0xFFD9DEEA),
    error = Color(0xFFD32F2F), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)

/** Applies the light or dark colour scheme chosen in settings (0 system, 1 light, 2 dark) together with the app shapes. */
@Composable
fun VaultTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = AppShapes, content = content)
}

/** Resolves a theme mode to dark or light; mode 0 follows the system. */
@Composable
fun isDarkTheme(mode: Int) = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
