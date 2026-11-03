package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HawassaColorScheme = darkColorScheme(
    primary = SolarAmber,
    onPrimary = TextOnAmber,
    primaryContainer = SolarAmberDark,
    onPrimaryContainer = TextPrimary,
    secondary = SkyCyan,
    onSecondary = TextOnAmber,
    tertiary = EmeraldAvailable,
    onTertiary = TextOnAmber,
    background = DarkCharcoalBg,
    onBackground = TextPrimary,
    surface = SlateCardSurface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = SlateCardBorder,
    error = CoralError,
    onError = TextPrimary
)

@Composable
fun HawassaTransitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HawassaColorScheme,
        typography = AppTypography,
        content = content
    )
}
