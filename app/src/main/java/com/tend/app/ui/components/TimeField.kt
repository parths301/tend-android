package com.tend.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tend.app.domain.Time
import com.tend.app.ui.motion.LocalTendHaptics
import com.tend.app.ui.motion.TendHaptic
import com.tend.app.ui.motion.TendMotion
import com.tend.app.ui.motion.bouncyTap
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.Dashed
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Teal
import com.tend.app.ui.theme.Terracotta
import kotlin.math.roundToInt

/**
 * Sets a minutes-since-midnight time.
 *
 * Keeps the name and signature of the old stepper so all five call sites —
 * check-in, backup, task due, plan start, habit reminder — upgrade together,
 * and no persisted value changes meaning.
 *
 * Three ways in, because +/- alone made setting 9:30pm about fifty taps:
 *
 * - **Type it.** Tap the time and enter `9:30`, `930`, `9:30 pm` or `21:30`.
 * - **Scrub it.** Drag the track for coarse movement, with a haptic tick per
 *   step so the value can be felt rather than watched.
 * - **Step it.** The +/− buttons are still there; they are the accessible path
 *   and the only one that works without fine motor control.
 */
@Composable
fun TimeStepperRow(
    valueMin: Int,
    onChange: (Int) -> Unit,
    hint: String = "drag or tap the time",
) {
    val haptics = LocalTendHaptics.current
    var editing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun commit(text: String) {
        parseClock(text)?.let { onChange(it.coerceIn(0, MAX_MIN)) }
        editing = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Stepper("−") { onChange((valueMin - STEP).coerceAtLeast(0)) }

            if (editing) {
                LaunchedEffect(Unit) { focus.requestFocus() }
                Box(
                    Modifier
                        .width(96.dp)
                        .background(Card, RoundedCornerShape(9.dp))
                        .border(1.dp, Teal, RoundedCornerShape(9.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    BasicTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = SpaceGrotesk, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold, color = Ink,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { commit(typed) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    if (typed.isEmpty()) {
                        Text("9:30 pm", fontSize = 14.sp, color = Faint)
                    }
                }
                Text(
                    "Set",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Teal,
                    modifier = Modifier.tapNoRipple(TendHaptic.Confirm) { commit(typed) },
                )
            } else {
                Text(
                    Time.clockAmPm(valueMin),
                    fontFamily = SpaceGrotesk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .semantics { contentDescription = "Time ${Time.clockAmPm(valueMin)}. Tap to type a time." }
                        .tapNoRipple(TendHaptic.Select) {
                            typed = ""
                            editing = true
                        },
                )
                Stepper("+") { onChange((valueMin + STEP).coerceAtMost(MAX_MIN)) }
                Text(hint, fontSize = 11.sp, color = Faint)
            }
        }

        if (!editing) {
            ScrubTrack(valueMin) { next ->
                if (next != valueMin) {
                    // One tick per step, not per pixel — otherwise a drag across
                    // the screen fires hundreds of buzzes and reads as a rattle.
                    haptics.perform(TendHaptic.Select)
                    onChange(next)
                }
            }
        }
    }
}

/**
 * A drag strip covering the whole day.
 *
 * Absolute rather than relative: where you put your finger is the time, so a
 * long jump is one gesture instead of a long swipe. Fine adjustment is what the
 * +/− buttons and typing are for.
 */
@Composable
private fun ScrubTrack(valueMin: Int, onChange: (Int) -> Unit) {
    var widthPx by remember { mutableStateOf(1f) }

    fun minutesAt(x: Float): Int {
        val fraction = (x / widthPx).coerceIn(0f, 1f)
        return ((fraction * MAX_MIN) / SCRUB_STEP).roundToInt() * SCRUB_STEP
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clipToBounds()
            .background(SegBg, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                widthPx = size.width.toFloat()
                detectHorizontalDragGestures { change, _ ->
                    onChange(minutesAt(change.position.x))
                }
            }
            .semantics { contentDescription = "Drag to set the time" },
    ) {
        // Hour marks, so the strip reads as a day rather than a blank bar.
        (0..24 step 6).forEach { hour ->
            Box(
                Modifier
                    .padding(start = 0.dp)
                    .fillMaxWidth(hour / 24f)
                    .height(26.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.width(1.dp).height(8.dp).background(Dashed))
            }
        }
        Box(
            Modifier
                .fillMaxWidth((valueMin.toFloat() / MAX_MIN).coerceIn(0f, 1f))
                .height(26.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(Terracotta, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** A small square +/− control. */
@Composable
fun Stepper(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .background(SegBg, RoundedCornerShape(10.dp))
            .bouncyTap(haptic = TendHaptic.Select, onClick = onClick)
            .semantics { contentDescription = if (glyph == "+") "Later" else "Earlier" },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

internal const val STEP = 15
internal const val MAX_MIN = 23 * 60 + 45
private const val SCRUB_STEP = 5

/**
 * Reads the times people actually type.
 *
 * Accepts `9`, `930`, `9:30`, `9:30 pm`, `21:30`, `9 pm`. Returns null when it
 * cannot tell, so a half-typed value never silently becomes midnight.
 *
 * Bare hours 1–7 are read as afternoon, matching the offline parser's rule that
 * "at 5" almost always means 5pm.
 */
internal fun parseClock(raw: String): Int? {
    val text = raw.trim().lowercase()
    if (text.isEmpty()) return null

    val pm = text.contains("pm")
    val am = text.contains("am")
    val digits = text.filter { it.isDigit() || it == ':' }
    if (digits.isEmpty()) return null

    var hour: Int
    var minute: Int

    if (digits.contains(':')) {
        val parts = digits.split(':')
        hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        minute = parts.getOrNull(1)?.take(2)?.padEnd(2, '0')?.toIntOrNull() ?: 0
    } else {
        val n = digits.toIntOrNull() ?: return null
        when (digits.length) {
            1, 2 -> {
                hour = n
                minute = 0
            }
            3 -> {
                hour = n / 100
                minute = n % 100
            }
            4 -> {
                hour = n / 100
                minute = n % 100
            }
            else -> return null
        }
    }

    if (minute !in 0..59) return null
    when {
        pm && hour < 12 -> hour += 12
        am && hour == 12 -> hour = 0
        !pm && !am && hour in 1..7 -> hour += 12 // "at 5" means 5pm
    }
    if (hour !in 0..23) return null
    return hour * 60 + minute
}
