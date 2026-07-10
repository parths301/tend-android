package com.tend.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.CardBorder
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.RingTrack
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
