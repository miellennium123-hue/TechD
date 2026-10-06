package com.guardianangel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Halo = Color(0xFFF6D58E)

/** Black, white and gold (round 45). Red stays for Quit for now and errors. */
private val Colors = darkColorScheme(
    primary = Color(0xFFD4AF37),
    onPrimary = Color(0xFF1A1400),
    primaryContainer = Color(0xFF4A3B0F),
    onPrimaryContainer = Color(0xFFFFF1C2),
    secondary = Color(0xFFEDE3C6),
    onSecondary = Color(0xFF2A2210),
    secondaryContainer = Color(0xFF38321F),
    onSecondaryContainer = Color(0xFFF7EFD8),
    tertiary = Halo,
    onTertiary = Color(0xFF3A2A00),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF7F5EF),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFF7F5EF),
    surfaceVariant = Color(0xFF2A2721),
    onSurfaceVariant = Color(0xFFD6CFBF),
    surfaceContainerLowest = Color(0xFF050505),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF181816),
    surfaceContainerHigh = Color(0xFF201F1C),
    surfaceContainerHighest = Color(0xFF2A2721),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF7A1C1C),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8C826B),
)

@Composable
fun GuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
