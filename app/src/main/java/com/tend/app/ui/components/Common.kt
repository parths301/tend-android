package com.tend.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tend.app.data.SettingsRepository
import com.tend.app.domain.Time
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.CardBorder
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Dashed
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.RingTrack
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SpaceGrotesk

/** Clickable without the Material ripple, to match the flat prototype look. */
fun Modifier.tapNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

/** Standard cream card: #FFFDF7 with a 1px #E8E2D3 border. */
@Composable
fun TendCard(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .background(Card, RoundedCornerShape(corner))
            .border(1.dp, CardBorder, RoundedCornerShape(corner))
    ) {
        content()
    }
}

/** Tiny uppercase tracking label, e.g. "THURSDAY · JUL 9". */
@Composable
fun Kicker(text: String, color: Color = Faint) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text = text,
        fontFamily = SpaceGrotesk,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    )
}

/** Circular progress ring with a small label in the middle (week strip). */
@Composable
fun ProgressRing(
    progress: Float,
    ringColor: Color,
    label: String,
    labelColor: Color,
    size: Dp = 44.dp,
    stroke: Dp = 3.5.dp,
    track: Color = RingTrack,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx * 1.7f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            if (progress > 0f && ringColor != Color.Transparent) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = label,
            fontFamily = SpaceGrotesk,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor,
        )
    }
}

data class HeatCell(val color: Color, val outlined: Boolean = false)

/**
 * Pixel heatmap: columns are weeks (oldest → newest, right-aligned like the
 * prototype's `justify-content:end` + `overflow:hidden`), rows are days.
 */
@Composable
fun Heatmap(
    cells: List<HeatCell>,
    modifier: Modifier = Modifier,
    cellSize: Dp = 13.dp,
    gap: Dp = 4.dp,
    corner: Dp = 4.dp,
    outline: Color = Color(0xFF24201A),
    endAligned: Boolean = true,
) {
    val columns = cells.chunked(7)
    Row(
        modifier = modifier.clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(
            gap,
            if (endAligned) Alignment.End else Alignment.CenterHorizontally,
        ),
    ) {
        columns.forEach { column ->
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                column.forEach { cell ->
                    Box(
                        Modifier
                            .size(cellSize)
                            .background(cell.color, RoundedCornerShape(corner))
                            .then(
                                if (cell.outlined) Modifier.border(1.5.dp, outline, RoundedCornerShape(corner))
                                else Modifier
                            )
                    )
                }
            }
        }
    }
}

/** Dashed "+ Add …" affordance used at the bottom of list screens. */
@Composable
fun DashedAddBox(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().tapNoRipple(onClick)) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color = Dashed,
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                ),
            )
        }
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("+", fontSize = 17.sp, color = Muted)
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        }
    }
}

/** Small bold field label used inside dialogs. */
@Composable
fun DialogLabel(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Faint)
}

/** Bordered single-line text input used inside dialogs. */
@Composable
fun DialogInput(value: String, onChange: (String) -> Unit, placeholder: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = Faint)
    }
}

/** Square −/+ stepper button. */
@Composable
fun Stepper(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .background(SegBg, RoundedCornerShape(10.dp))
            .tapNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

/** "− 9:00 AM +" row stepping a minutes-since-midnight value in 15-min increments. */
@Composable
fun TimeStepperRow(valueMin: Int, onChange: (Int) -> Unit, hint: String = "15-min steps") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stepper("−") { onChange((valueMin - 15).coerceAtLeast(0)) }
        Text(
            Time.clockAmPm(valueMin), fontFamily = SpaceGrotesk,
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
        )
        Stepper("+") { onChange((valueMin + 15).coerceAtMost(23 * 60 + 45)) }
        Text(hint, fontSize = 11.sp, color = Faint)
    }
}

/** Category chips: presets + user customs + "+ New" inline creator. */
@Composable
fun CategoryPicker(
    selected: String,
    customs: List<String>,
    onSelect: (String) -> Unit,
    onAddCustom: (String) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val all = (SettingsRepository.PRESET_CATEGORIES + customs).distinct()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            all.forEach { cat ->
                val isSelected = selected == cat
                Box(
                    Modifier
                        .background(if (isSelected) Ink else Color.Transparent, RoundedCornerShape(99.dp))
                        .border(1.dp, if (isSelected) Ink else Border, RoundedCornerShape(99.dp))
                        .tapNoRipple { onSelect(cat) }
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text(
                        cat, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Cream else Muted,
                    )
                }
            }
            Box(
                Modifier
                    .border(1.dp, Dashed, RoundedCornerShape(99.dp))
                    .tapNoRipple { adding = !adding }
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Text("+ New", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Faint)
            }
        }
        if (adding) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    DialogInput(newName, { newName = it }, "Custom category…")
                }
                Box(
                    Modifier
                        .background(Ink, RoundedCornerShape(99.dp))
                        .tapNoRipple {
                            val formatted = newName.trim().replaceFirstChar { it.uppercaseChar() }
                            if (formatted.isNotEmpty()) {
                                onAddCustom(formatted)
                                onSelect(formatted)
                                newName = ""
                                adding = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
                }
            }
        }
    }
}

/** Round check button used on plan rows and task rows. */
@Composable
fun CheckCircle(
    done: Boolean,
    accent: Color,
    size: Dp,
    borderColor: Color,
    idleGlyphColor: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .then(
                if (done) Modifier.background(accent, RoundedCornerShape(50))
                else Modifier.border(1.5.dp, borderColor, RoundedCornerShape(50))
            )
            .tapNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✓",
            fontSize = (size.value * 0.4).sp,
            fontWeight = FontWeight.Bold,
            color = if (done) Card else idleGlyphColor,
        )
    }
}
