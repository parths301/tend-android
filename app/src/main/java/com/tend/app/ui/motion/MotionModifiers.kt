package com.tend.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/**
 * A tap that feels like pressing something physical: the control collapses
 * under the finger and springs back on release, with a haptic fired at the
 * moment of the click.
 *
 * The scale runs on `graphicsLayer`, so it never triggers layout — a bouncing
 * button can't push its neighbours around or cost a relayout on every frame,
 * which is what keeps this cheap enough to put on every control.
 *
 * Interaction is never blocked: the click fires immediately, and the spring
 * just plays out behind it.
 */
fun Modifier.bouncyTap(
    haptic: TendHaptic = TendHaptic.Tap,
    pressedScale: Float = TendMotion.PressScale,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val haptics = LocalTendHaptics.current
    val reduceMotion = LocalReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = if (pressed) TendMotion.PressDown else TendMotion.PressRelease,
        label = "bouncyTapScale",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
        ) {
            haptics.perform(haptic)
            onClick()
        }
}

/**
 * Pops once when [trigger] becomes true — the reward beat after an action
 * lands.
 *
 * Never fires on first composition. Without that guard, every already-checked
 * habit would pop as you scroll it into view, or on every tab switch, which
 * turns a reward into wallpaper.
 */
fun Modifier.successPop(trigger: Boolean): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(trigger) {
        if (isFirstComposition) {
            isFirstComposition = false
            return@LaunchedEffect
        }
        if (!trigger || reduceMotion) return@LaunchedEffect
        scale.snapTo(1f)
        scale.animateTo(TendMotion.PopScale, TendMotion.Pop)
        scale.animateTo(1f, TendMotion.Settle)
    }

    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * A short decaying shake, for "that didn't work".
 *
 * Each [trigger] change plays one pass. The amplitude decays across four steps
 * so it reads as a head-shake rather than a rattle — clear about the failure
 * without being punitive. Pair it with [TendHaptic.Reject] at the call site so
 * the sensation and the movement land together.
 */
fun Modifier.shakeOnError(trigger: Any?): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(trigger) {
        if (isFirstComposition) {
            isFirstComposition = false
            return@LaunchedEffect
        }
        if (trigger == null || reduceMotion) return@LaunchedEffect
        val distance = with(density) { TendMotion.ShakeDistance.toPx() }
        val step = tween<Float>(TendMotion.ShakeStepMs, easing = FastOutSlowInEasing)
        offsetX.snapTo(0f)
        listOf(distance, -distance * 0.72f, distance * 0.42f, -distance * 0.18f, 0f)
            .forEach { target -> offsetX.animateTo(target, step) }
    }

    graphicsLayer { translationX = offsetX.value }
}
