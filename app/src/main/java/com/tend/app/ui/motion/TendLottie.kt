package com.tend.app.ui.motion

import androidx.annotation.RawRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.tend.app.ui.theme.Gold
import com.tend.app.ui.theme.Teal
import com.tend.app.ui.theme.Terracotta
import com.tend.app.ui.theme.TerracottaLight
import com.tend.app.ui.theme.Violet
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's one entry point for Lottie.
 *
 * Wrapping it buys three things worth having: playback is reported back through
 * [onFinished] so an overlay can dismiss itself on the animation rather than on
 * a guessed timeout; a failed or missing composition falls back to [fallback]
 * instead of rendering an empty hole; and reduced-motion skips straight to the
 * finished state, so a user who has turned animations off still gets the
 * outcome without the show.
 */
@Composable
fun TendLottie(
    @RawRes resId: Int,
    modifier: Modifier = Modifier,
    iterations: Int = 1,
    speed: Float = 1f,
    onFinished: (() -> Unit)? = null,
    fallback: @Composable () -> Unit = {},
) {
    val reduceMotion = LocalReduceMotion.current
    val result = rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    val composition by result

    if (reduceMotion) {
        LaunchedEffect(Unit) { onFinished?.invoke() }
        return
    }

    // A composition that failed to parse must not leave a silent blank.
    if (result.isFailure || (composition == null && result.isComplete)) {
        fallback()
        LaunchedEffect(Unit) { onFinished?.invoke() }
        return
    }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        speed = speed,
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier,
    )

    if (onFinished != null && progress >= 1f && composition != null) {
        LaunchedEffect(progress) { onFinished() }
    }
}

/**
 * Compose-drawn confetti, used when the Lottie asset can't be loaded.
 *
 * Deliberately simple — it exists so a celebration is never a blank screen on a
 * device where the asset failed, not to match the designed animation.
 */
@Composable
fun ConfettiFallback(modifier: Modifier = Modifier, progress: Float = 1f) {
    val palette = remember { listOf(Terracotta, Violet, Teal, Gold, TerracottaLight) }
    Canvas(modifier.fillMaxSize()) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val spread = size.minDimension * 0.42f * progress
        repeat(18) { i ->
            val angle = (i * 20f) * (Math.PI / 180f).toFloat()
            drawCircle(
                color = palette[i % palette.size].copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                radius = size.minDimension * 0.018f,
                center = Offset(
                    centre.x + cos(angle) * spread,
                    centre.y + sin(angle) * spread * 0.8f,
                ),
            )
        }
    }
}
