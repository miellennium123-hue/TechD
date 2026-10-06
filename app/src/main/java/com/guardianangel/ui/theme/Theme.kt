package com.guardianangel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Halo = Color(0xFFF6D58E)

private val Colors = darkColorScheme(
    primary = Color(0xFFF48FB1),
    onPrimary = Color(0xFF3B0A24),
    primaryContainer = Color(0xFF6A1B4D),
    onPrimaryContainer = Color(0xFFFFD8E8),
    secondary = Color(0xFFD1B3FF),
    onSecondary = Color(0xFF2E1452),
    secondaryContainer = Color(0xFF45286E),
    onSecondaryContainer = Color(0xFFEBDCFF),
    tertiary = Halo,
    onTertiary = Color(0xFF3A2A00),
    background = Color(0xFF140B1A),
    onBackground = Color(0xFFF1E4F0),
    surface = Color(0xFF1A0F21),
    onSurface = Color(0xFFF1E4F0),
    surfaceVariant = Color(0xFF33223D),
    onSurfaceVariant = Color(0xFFD5C2D6),
    surfaceContainerLowest = Color(0xFF10081A),
    surfaceContainerLow = Color(0xFF1E1226),
    surfaceContainer = Color(0xFF24172D),
    surfaceContainerHigh = Color(0xFF2B1C35),
    surfaceContainerHighest = Color(0xFF33223D),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF7A1C1C),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9E8AA0),
)

@Composable
fun GuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
