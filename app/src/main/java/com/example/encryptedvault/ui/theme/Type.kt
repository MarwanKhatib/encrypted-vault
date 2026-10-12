package com.example.encryptedvault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System fonts only: the app is offline and ships no font files. Sans for reading, monospace for the security "terminal" labels.
private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

/** Type scale: monospace for titles and labels gives the security-console feel; sans keeps body text readable. */
val AppTypography: Typography =
    Typography().let { base ->
        base.copy(
            headlineLarge = base.headlineLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 0.5.sp),
            headlineMedium = base.headlineMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 0.5.sp),
            headlineSmall = base.headlineSmall.copy(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.5.sp),
            titleLarge = base.titleLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.4.sp),
            titleMedium = base.titleMedium.copy(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            titleSmall = base.titleSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
            bodyLarge = base.bodyLarge.copy(fontFamily = Sans, fontSize = 16.sp, lineHeight = 22.sp),
            bodyMedium = base.bodyMedium.copy(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = base.bodySmall.copy(fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = base.labelLarge.copy(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.6.sp),
            labelMedium = base.labelMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
            labelSmall = base.labelSmall.copy(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.8.sp),
        )
    }

/** Monospace caption style for status lines such as "AES-256-GCM · OFFLINE". */
val SecurityLabel: TextStyle = AppTypography.labelSmall
