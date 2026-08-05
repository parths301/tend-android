package com.tend.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.tend.app.ui.motion.LocalReduceMotion
import com.tend.app.ui.motion.LocalTendHaptics
import com.tend.app.ui.motion.rememberSystemReduceMotion
import com.tend.app.ui.motion.rememberTendHaptics

private val TendColors = lightColorScheme(
    primary = Ink,
    onPrimary = Cream,
    background = Cream,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    outline = Border,
)

/**
 * Colours plus the motion environment. Providing haptics and the reduced-motion
 * flag here means any composable under the theme can use the motion modifiers
 * without threading either one through its parameters.
 */
@Composable
fun TendTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTendHaptics provides rememberTendHaptics(),
        LocalReduceMotion provides rememberSystemReduceMotion(),
    ) {
        MaterialTheme(colorScheme = TendColors, content = content)
    }
}
