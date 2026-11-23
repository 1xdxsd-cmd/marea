package com.marea.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.marea.music.R

// Identidad visual de Marea (misma paleta que Marea web: fondo #0b1720, acento azul del logo).
val MareaBg = Color(0xFF0B1720)
val MareaSurface = Color(0xFF10202B)
val MareaCard = Color(0xFF16293A)
val MareaAccent = Color(0xFF1E90F0)
val MareaAccentDeep = Color(0xFF0378D4)
val MareaOnAccent = Color(0xFF0A1821)
val MareaText = Color(0xFFEAF2F8)
val MareaTextSecondary = Color(0xFF93A8B8)
val MareaError = Color(0xFFFF7A85)

// Bricolage Grotesque para títulos, Figtree para el texto (como en Marea web).
val BricolageGrotesque = FontFamily(
    Font(R.font.bricolage_grotesque_500, FontWeight.Medium),
    Font(R.font.bricolage_grotesque_700, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_800, FontWeight.ExtraBold)
)

val Figtree = FontFamily(
    Font(R.font.figtree_400, FontWeight.Normal),
    Font(R.font.figtree_500, FontWeight.Medium),
    Font(R.font.figtree_600, FontWeight.SemiBold),
    Font(R.font.figtree_700, FontWeight.Bold)
)

private val MareaColorScheme = darkColorScheme(
    primary = MareaAccent,
    onPrimary = MareaOnAccent,
    primaryContainer = MareaAccentDeep,
    onPrimaryContainer = MareaText,
    secondary = MareaAccent,
    onSecondary = MareaOnAccent,
    background = MareaBg,
    onBackground = MareaText,
    surface = MareaSurface,
    onSurface = MareaText,
    surfaceVariant = MareaCard,
    onSurfaceVariant = MareaTextSecondary,
    surfaceContainer = MareaSurface,
    surfaceContainerHigh = MareaCard,
    surfaceContainerHighest = MareaCard,
    surfaceContainerLow = MareaSurface,
    surfaceContainerLowest = MareaBg,
    outline = Color(0xFF2B4356),
    outlineVariant = Color(0xFF1E3344),
    error = MareaError,
    onError = MareaOnAccent
)

private val MareaTypography: Typography = Typography().let { base ->
    fun TextStyle.display(weight: FontWeight = FontWeight.ExtraBold) =
        copy(fontFamily = BricolageGrotesque, fontWeight = weight)
    fun TextStyle.body() = copy(fontFamily = Figtree)
    Typography(
        displayLarge = base.displayLarge.display(),
        displayMedium = base.displayMedium.display(),
        displaySmall = base.displaySmall.display(),
        headlineLarge = base.headlineLarge.display(),
        headlineMedium = base.headlineMedium.display(),
        headlineSmall = base.headlineSmall.display(FontWeight.Bold),
        titleLarge = base.titleLarge.display(FontWeight.Bold),
        titleMedium = base.titleMedium.body(),
        titleSmall = base.titleSmall.body(),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body(),
        labelMedium = base.labelMedium.body(),
        labelSmall = base.labelSmall.body()
    )
}

@Composable
fun MareaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MareaColorScheme,
        typography = MareaTypography,
        content = content
    )
}
