package com.inkframe.studio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val InkCyan = Color(0xFF00E5FF)
private val InkDeep = Color(0xFF0A0A0C)
private val InkGlass = Color(0xFF1A1A20)

private val DarkColors = darkColorScheme(
    primary = InkCyan,
    secondary = Color(0xFF7C4DFF),
    background = InkDeep,
    surface = InkGlass,
    surfaceVariant = Color(0xFF25252B)
)

private val LightColors = lightColorScheme(
    primary = Accent,
    secondary = Color(0xFF8E6CEF),
)

@Composable
fun InkFrameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
