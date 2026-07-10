package com.tend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.data.db.PlanBlock
import com.tend.app.domain.Time
import com.tend.app.ui.components.CheckCircle
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ScreenTitle
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Dashed
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Teal
import com.tend.app.ui.theme.Terracotta
import com.tend.app.ui.theme.Violet

private fun kindColor(kind: String): Color = when (kind) {
    "focus" -> Violet
    "event" -> Teal
    else -> Terracotta
}

private fun kindTag(kind: String): String? = when (kind) {
    "focus" -> "FOCUS BLOCK"
    "event" -> "CALENDAR"
    else -> null
}

@Composable
fun PlanScreen(vm: MainViewModel) {
    val plan by vm.plan.collectAsStateWithLifecycle()

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Kicker(Time.kicker(vm.todayDate))
                Spacer(Modifier.height(1.dp))
                ScreenTitle("Plan")
            }
            Box(
                Modifier
                    .background(Ink, RoundedCornerShape(99.dp))
                    .tapNoRipple { vm.openAi() }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text("+ Add block", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
            }
        }

        plan.forEachIndexed { index, block ->
            if (index > 0) {
                val gap = block.startMin - plan[index - 1].endMin
                if (gap in 1..45) GapRow(gap)
            }
            PlanRow(block) { vm.togglePlan(block) }
        }
    }
}

@Composable
private fun GapRow(gapMin: Int) {
    Row(
        Modifier.padding(start = 72.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(2.dp).height(22.dp).background(Dashed))
        Text("$gapMin min gap", fontSize = 11.sp, color = Faint)
    }
}

@Composable
private fun PlanRow(block: PlanBlock, onToggle: () -> Unit) {
    val color = kindColor(block.kind)
    val soft = color.copy(alpha = 0.13f)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            Time.clock(block.startMin),
            modifier = Modifier.width(44.dp).padding(top = 6.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Faint,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Box(
            Modifier
                .width(32.dp)
                .defaultMinSize(minHeight = 44.dp)
                .background(soft, RoundedCornerShape(16.dp))
                .padding(top = 11.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
        }
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(Time.range(block.startMin, block.endMin), fontSize = 11.sp, color = Faint)
            Spacer(Modifier.height(1.dp))
            Text(
                block.title,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (block.done) Faint else Ink,
                textDecoration = if (block.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            kindTag(block.kind)?.let { tag ->
                Spacer(Modifier.height(4.dp))
                Box(Modifier.background(soft, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                    Text(tag, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = color)
                }
            }
        }
        Box(Modifier.align(Alignment.CenterVertically)) {
            CheckCircle(
                done = block.done,
                accent = color,
                size = 34.dp,
                borderColor = Border,
                idleGlyphColor = com.tend.app.ui.theme.Disabled,
                onClick = onToggle,
            )
        }
    }
}
