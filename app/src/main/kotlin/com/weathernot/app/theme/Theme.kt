package com.weathernot.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.weathernot.kit.models.GoNoGo

// The web app's design tokens (frontend/src/index.css). The web app is
// light only; the dark palette keeps the same hues, lifted for contrast.

@Immutable
data class Palette(
    val go: Color,
    val caution: Color,
    val hold: Color,
    val border: Color,
    val muted: Color,
    val mutedForeground: Color,
    val headerBrush: Brush,
) {
    /** A verdict's color; "unknown" reads as muted. */
    fun verdict(verdict: GoNoGo): Color = when (verdict) {
        GoNoGo.GO -> go
        GoNoGo.WATCH -> caution
        GoNoGo.NO_GO -> hold
        GoNoGo.UNKNOWN -> mutedForeground
    }
}

private val Accent = Color(0xFF2F5BFF)
private val Primary = Color(0xFF1C3FD6)

private val lightPalette = Palette(
    go = Color(0xFF12946B),
    caution = Color(0xFFB45309),
    hold = Color(0xFFD93A3A),
    border = Color(0xFFDDE6F5),
    muted = Color(0xFFF2F6FD),
    mutedForeground = Color(0xFF5B6B8C),
    headerBrush = Brush.horizontalGradient(listOf(Color(0xFF142A72), Primary, Accent)),
)

private val darkPalette = Palette(
    go = Color(0xFF3CC795),
    caution = Color(0xFFE9A04A),
    hold = Color(0xFFF26B6B),
    border = Color(0xFF26324F),
    muted = Color(0xFF18223E),
    mutedForeground = Color(0xFF97A5C6),
    headerBrush = Brush.horizontalGradient(listOf(Color(0xFF0E1D52), Color(0xFF1733A8), Color(0xFF2A4FD9))),
)

private val lightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E8FF),
    onPrimaryContainer = Color(0xFF0E2178),
    secondary = Primary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E8FF),
    onSecondaryContainer = Color(0xFF0E2178),
    background = Color(0xFFEEF3FC),
    onBackground = Color(0xFF111A33),
    surface = Color(0xFFEEF3FC),
    onSurface = Color(0xFF111A33),
    surfaceVariant = Color(0xFFF2F6FD),
    onSurfaceVariant = Color(0xFF5B6B8C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF7F9FE),
    surfaceContainerHighest = Color(0xFFF2F6FD),
    outline = Color(0xFFB9C6E0),
    outlineVariant = Color(0xFFDDE6F5),
    error = Color(0xFFD93A3A),
    onError = Color.White,
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF7B97FF),
    onPrimary = Color(0xFF051452),
    primaryContainer = Color(0xFF1F3697),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Color(0xFF9DB0FF),
    onSecondary = Color(0xFF051452),
    secondaryContainer = Color(0xFF1F3697),
    onSecondaryContainer = Color(0xFFDDE4FF),
    background = Color(0xFF0B1226),
    onBackground = Color(0xFFE6ECFA),
    surface = Color(0xFF0B1226),
    onSurface = Color(0xFFE6ECFA),
    surfaceVariant = Color(0xFF18223E),
    onSurfaceVariant = Color(0xFF97A5C6),
    surfaceContainerLowest = Color(0xFF0E162D),
    surfaceContainerLow = Color(0xFF131C36),
    surfaceContainer = Color(0xFF131C36),
    surfaceContainerHigh = Color(0xFF18223E),
    surfaceContainerHighest = Color(0xFF1E2946),
    outline = Color(0xFF3A4768),
    outlineVariant = Color(0xFF26324F),
    error = Color(0xFFF26B6B),
    onError = Color(0xFF3A0707),
)

val LocalPalette = staticCompositionLocalOf { lightPalette }

@Composable
fun WeatherNotTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (dark) darkPalette else lightPalette) {
        MaterialTheme(colorScheme = if (dark) darkColors else lightColors, content = content)
    }
}
