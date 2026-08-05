package com.tend.app.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Motion tokens for Tend.
 *
 * The playful feel comes from **asymmetric damping**, not from long durations:
 * a control collapses under the finger with a critically damped spring — instant,
 * no wobble, so the press reads as a physical contact — and springs back with a
 * visibly underdamped one, so the release reads as a rebound. Duration-based
 * easing can't express that difference; a spring pair can, in under 250ms.
 *
 * Every value here is deliberately short. Motion should confirm what happened
 * and get out of the way, never gate the next tap.
 */
object TendMotion {

    // ── scale targets ───────────────────────────────────────────
    /** Small controls: pills, chips, check buttons. */
    const val PressScale = 0.93f

    /** Cards and full-width rows, where a deep squash would look rubbery. */
    const val PressScaleLarge = 0.975f

    /** Overshoot peak of a success pop. */
    const val PopScale = 1.18f

    // ── springs ─────────────────────────────────────────────────
    /** Finger down: fast and dead-stop. No overshoot going *into* a press. */
    val PressDown: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 1800f)

    /** Finger up: the rebound. This single value carries most of the "alive" feel. */
    val PressRelease: SpringSpec<Float> = spring(dampingRatio = 0.42f, stiffness = 560f)

    /** Success pop — bouncier than a press, and still done in ~300ms. */
    val Pop: SpringSpec<Float> = spring(dampingRatio = 0.34f, stiffness = 700f)

    /** Return-to-rest after a pop: settles without a second bounce. */
    val Settle: SpringSpec<Float> = spring(dampingRatio = 0.75f, stiffness = 380f)

    /** Progress values (rings, bars) ease toward their target with a slight settle. */
    val Progress: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 170f)

    /** Content that springs into place — celebration cards, banners. */
    val EnterSpring: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 500f)

    // ── error ───────────────────────────────────────────────────
    val ShakeDistance = 7.dp
    const val ShakeStepMs = 55

    // ── celebration ─────────────────────────────────────────────
    /** Hard cap on any celebration overlay. Long enough to land, short enough to forgive. */
    const val CelebrationMs = 1_700L
}

/**
 * True when the user has asked the system to stop animating.
 *
 * Read from the platform's animator duration scale, which is what the
 * accessibility "Remove animations" toggle and developer options both drive.
 * Honouring it here means one check at the theme level disables every bounce,
 * pop, shake and celebration in the app.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
