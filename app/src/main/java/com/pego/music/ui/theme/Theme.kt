package com.pego.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PegoBlack = Color(0xFF121212)
val PegoSurface = Color(0xFF181818)
val PegoCard = Color(0xFF242424)
val PegoGreen = Color(0xFF1DB954)
val PegoTextPrimary = Color(0xFFFFFFFF)
val PegoTextSecondary = Color(0xFFB3B3B3)
val PegoError = Color(0xFFCF6679)

private val PegoColorScheme = darkColorScheme(
    primary = PegoGreen,
    onPrimary = Color.Black,
    secondary = PegoGreen,
    onSecondary = Color.Black,
    background = PegoBlack,
    onBackground = PegoTextPrimary,
    surface = PegoSurface,
    onSurface = PegoTextPrimary,
    surfaceVariant = PegoCard,
    onSurfaceVariant = PegoTextSecondary,
    error = PegoError,
    onError = Color.Black
)

@Composable
fun PegoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PegoColorScheme,
        content = content
    )
}
