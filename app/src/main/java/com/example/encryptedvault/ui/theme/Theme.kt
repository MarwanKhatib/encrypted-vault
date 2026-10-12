package com.example.encryptedvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Cyber palette: near-black navy, electric cyan for actions, acid green for "secure" state, amber for warnings.
private val Dark =
    darkColorScheme(
        primary = Color(0xFF2EE6FF),
        onPrimary = Color(0xFF00212B),
        primaryContainer = Color(0xFF0A3A47),
        onPrimaryContainer = Color(0xFFBDF6FF),
        secondary = Color(0xFF7CFF8A),
        onSecondary = Color(0xFF00390F),
        secondaryContainer = Color(0xFF0F4A1C),
        onSecondaryContainer = Color(0xFFC2FFCB),
        tertiary = Color(0xFFFFB547),
        onTertiary = Color(0xFF3F2600),
        background = Color(0xFF060A12),
        onBackground = Color(0xFFE3EAF5),
        surface = Color(0xFF0C121C),
        onSurface = Color(0xFFE3EAF5),
        surfaceVariant = Color(0xFF131C2A),
        onSurfaceVariant = Color(0xFF8E9BB3),
        surfaceContainer = Color(0xFF0F1622),
        surfaceContainerHigh = Color(0xFF172133),
        outline = Color(0xFF2A3547),
        outlineVariant = Color(0xFF1C2638),
        error = Color(0xFFFF5C7A),
        errorContainer = Color(0xFF4A1420),
        onErrorContainer = Color(0xFFFFD9E0),
    )

private val Light =
    lightColorScheme(
        primary = Color(0xFF00758F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCCF3FA),
        onPrimaryContainer = Color(0xFF00212B),
        secondary = Color(0xFF1B7A3A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCFF5D8),
        onSecondaryContainer = Color(0xFF00390F),
        tertiary = Color(0xFFB26A00),
        onTertiary = Color.White,
        background = Color(0xFFF3F6FA),
        onBackground = Color(0xFF0C121C),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0C121C),
        surfaceVariant = Color(0xFFE6EDF5),
        onSurfaceVariant = Color(0xFF4A5669),
        surfaceContainer = Color(0xFFEDF2F8),
        surfaceContainerHigh = Color(0xFFE2E9F2),
        outline = Color(0xFFA9B6C8),
        outlineVariant = Color(0xFFD5DDE8),
        error = Color(0xFFC62840),
        errorContainer = Color(0xFFFFDADF),
        onErrorContainer = Color(0xFF40000B),
    )

private val AppShapes =
    Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** Applies the light or dark colour scheme chosen in settings (0 system, 1 light, 2 dark) together with the shapes and type scale. */
@Composable
fun VaultTheme(
    mode: Int,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isDarkTheme(mode)) Dark else Light,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

/** Resolves a theme mode to dark or light; mode 0 follows the system. */
@Composable
fun isDarkTheme(mode: Int) =
    when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
