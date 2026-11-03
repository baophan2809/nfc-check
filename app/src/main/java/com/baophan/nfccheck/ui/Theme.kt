package com.baophan.nfccheck.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF12B886)
private val AccentDim = Color(0xFF0CA678)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF042016),
    primaryContainer = AccentDim,
    onPrimaryContainer = Color(0xFFEAFBF4),
    secondary = Color(0xFF4DABF7),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E9EE),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6E9EE),
    surfaceVariant = Color(0xFF1E2530),
    onSurfaceVariant = Color(0xFFAEB7C2),
    outline = Color(0xFF2B3340),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0A0A)
)

@Composable
fun NfcCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content
    )
}
