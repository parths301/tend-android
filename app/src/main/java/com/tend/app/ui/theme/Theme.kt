package com.tend.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val TendColors = lightColorScheme(
    primary = Ink,
    onPrimary = Cream,
    background = Cream,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    outline = Border,
)

@Composable
fun TendTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TendColors, content = content)
}
