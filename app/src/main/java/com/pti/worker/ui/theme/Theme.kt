package com.pti.worker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PtiRed,
    onPrimary = PtiSurface,
    secondary = PtiGrey,
    onSecondary = PtiSurface,
    background = PtiBackground,
    surface = PtiSurface,
    onBackground = PtiOnSurface,
    onSurface = PtiOnSurface,
    error = PtiRedDark
)

@Composable
fun PtiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme, typography = Typography, content = content)
}
