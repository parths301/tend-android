package com.tend.app.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/**
 * The vocabulary of things Tend can say through the vibrator. Deliberately
 * small: a haptic is only meaningful if it maps to one kind of event, and a
 * device only has so many distinguishable sensations.
 */
enum class TendHaptic {
    /** No feedback — for taps that only navigate or scroll. */
    None,

    /** Primary press. The lightest thing the device can do. */
    Tap,

    /** Moving between choices: chips, segmented controls, steppers. */
    Select,

    /** Switching something on / off, where direction is worth feeling. */
    ToggleOn,
    ToggleOff,

    /** An action landed: habit checked, backup written, task completed. */
    Confirm,

    /** An action was refused or failed. */
    Reject,
}

/**
 * Haptics for Tend, over [View.performHapticFeedback].
 *
 * Two reasons this goes through the View API rather than [android.os.Vibrator]:
 * it needs **no `VIBRATE` permission**, and the constants are remapped per
 * device by the OEM's haptic profile — so `CONFIRM` feels like *that phone's*
 * confirm, rather than a waveform tuned on one handset and wrong everywhere
 * else. It also respects the system "touch feedback" setting for free, so a
 * user who turns haptics off in Settings is obeyed without any code here.
 *
 * The richer constants arrived after this app's `minSdk` of 26, so each one is
 * gated and degrades to the nearest older sensation rather than going silent.
 *
 * Note: Compose's own `HapticFeedbackType` only exposes `LongPress` and
 * `TextHandleMove` on Compose UI 1.7 (this project's version). The full set —
 * `Confirm`, `Reject`, `ToggleOn`, `SegmentTick` — landed in 1.8. When this
 * project moves to 1.8+, [constantFor] is the only thing that needs to change.
 */
class TendHaptics(private val view: View?) {

    fun perform(haptic: TendHaptic) {
        val target = view ?: return
        if (!target.isHapticFeedbackEnabled) return
        val constant = constantFor(haptic) ?: return
        target.performHapticFeedback(constant)
    }

    /**
     * A two-beat "ta-da" for celebrations: one confirm, then a quick pair of
     * ticks. Three pulses inside ~170ms read as a single flourish rather than
     * as three separate events — long enough to feel deliberate, short enough
     * that it never becomes buzzing.
     */
    suspend fun celebrate() {
        perform(TendHaptic.Confirm)
        delay(95)
        perform(TendHaptic.Select)
        delay(75)
        perform(TendHaptic.Select)
    }

    private fun constantFor(haptic: TendHaptic): Int? = when (haptic) {
        TendHaptic.None -> null

        // Available well below minSdk 26.
        TendHaptic.Tap -> HapticFeedbackConstants.KEYBOARD_TAP
        TendHaptic.Select -> HapticFeedbackConstants.CLOCK_TICK

        // CONFIRM / REJECT are API 30. Below that, the closest honest stand-ins
        // are a crisp key press and a long press — different enough from each
        // other that the two still don't feel alike.
        TendHaptic.Confirm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY

        TendHaptic.Reject ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS

        // TOGGLE_ON / TOGGLE_OFF are API 34; a tick carries the event, just
        // without the directionality, on older devices.
        TendHaptic.ToggleOn ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.TOGGLE_ON
            else HapticFeedbackConstants.CLOCK_TICK

        TendHaptic.ToggleOff ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.TOGGLE_OFF
            else HapticFeedbackConstants.CLOCK_TICK
    }
}

/**
 * Defaults to a no-op instance rather than throwing, so a composable rendered
 * outside [com.tend.app.ui.theme.TendTheme] — a preview, a test — still works.
 */
val LocalTendHaptics = staticCompositionLocalOf { TendHaptics(null) }

@Composable
fun rememberTendHaptics(): TendHaptics {
    val view = LocalView.current
    return remember(view) { TendHaptics(view) }
}
