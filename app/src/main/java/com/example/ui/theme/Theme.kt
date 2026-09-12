package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GreenPrimary = Color(0xFF1DB954)
val GreenLight = Color(0xFF1ED760)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF181818)
val DarkSurfaceVariant = Color(0xFF242424)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB3B3B3)
val CardBackground = Color(0xFF282828)

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1B4D2E),
    onPrimaryContainer = Color.White,
    secondary = GreenLight,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = Color(0xFFEF4444)
)

@Composable
fun AlreadyMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
