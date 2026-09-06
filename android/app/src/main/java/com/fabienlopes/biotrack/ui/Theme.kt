package com.fabienlopes.biotrack.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BioPrimary = Color(0xFF0FB5AE)
private val BioPrimaryDark = Color(0xFF22D5CD)
private val BioBackground = Color(0xFFF8FAF9)
private val BioDarkBackground = Color(0xFF0B0F0E)
private val BioSurface = Color(0xFFFFFFFF)
private val BioDarkSurface = Color(0xFF1C1A1B)

private val LightColors = lightColorScheme(
    primary = BioPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFF5E5CFF),
    background = BioBackground,
    surface = BioSurface,
    surfaceVariant = Color(0xFFE8EEEC),
    outline = Color(0xFFD1DAD7)
)

private val DarkColors = darkColorScheme(
    primary = BioPrimaryDark,
    onPrimary = Color(0xFF003735),
    secondary = Color(0xFF9D9AFF),
    background = BioDarkBackground,
    surface = BioDarkSurface,
    surfaceVariant = Color(0xFF302F30),
    outline = Color(0xFF4C514F)
)

@Composable
fun BioTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
