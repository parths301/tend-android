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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.data.db.PlanBlock
import com.tend.app.domain.Time
import com.tend.app.ui.components.CheckCircle
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ScreenTitle
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Dashed
import com.tend.app.ui.theme.Disabled
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SpaceGrotesk
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
            var showAdd by rememberSaveable { mutableStateOf(false) }
            Box(
                Modifier
                    .background(Ink, RoundedCornerShape(99.dp))
                    .tapNoRipple { showAdd = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text("+ Add block", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
            }
            if (showAdd) {
                AddBlockDialog(
                    onCreate = { title, startMin, durationMin, kind ->
                        vm.addPlanBlock(title, startMin, durationMin, kind)
                        showAdd = false
                    },
                    onDismiss = { showAdd = false },
                )
            }
        }

        if (plan.isEmpty()) {
            Text(
                "Nothing planned today — add a block, or ask Tend to plan your day.",
                fontSize = 12.5.sp, color = Muted,
            )
        }

        plan.forEachIndexed { index, block ->
            if (index > 0) {
                val gap = block.startMin - plan[index - 1].endMin
                if (gap in 1..45) GapRow(gap)
            }
            PlanRow(block, onToggle = { vm.togglePlan(block) }, onDelete = { vm.deletePlanBlock(block) })
        }
    }
}

@Composable
private fun AddBlockDialog(
    onCreate: (title: String, startMin: Int, durationMin: Int, kind: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var startMin by rememberSaveable { mutableStateOf(9 * 60) }
    var duration by rememberSaveable { mutableStateOf(30) }
    var kind by rememberSaveable { mutableStateOf("focus") }

    Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add block", fontFamily = SpaceGrotesk, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                DialogLabel("Title")
                DialogInput(title, { title = it }, "e.g. Deep work")

                DialogLabel("Starts at")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stepper("−") { startMin = (startMin - 15).coerceAtLeast(0) }
                    Text(
                        Time.clockAmPm(startMin), fontFamily = SpaceGrotesk,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                    Stepper("+") { startMin = (startMin + 15).coerceAtMost(23 * 60 + 45) }
                    Text("15-min steps", fontSize = 11.sp, color = Faint)
                }

                DialogLabel("Duration")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15 to "15m", 30 to "30m", 45 to "45m", 60 to "1h", 90 to "1.5h", 120 to "2h").forEach { (min, label) ->
                        val selected = duration == min
                        Box(
                            Modifier
                                .background(if (selected) Ink else Color.Transparent, RoundedCornerShape(99.dp))
                                .border(1.dp, if (selected) Ink else Border, RoundedCornerShape(99.dp))
                                .tapNoRipple { duration = min }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                color = if (selected) Cream else Muted,
                            )
                        }
                    }
                }

                DialogLabel("Kind")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("focus" to "Focus", "event" to "Event", "habit" to "Habit").forEach { (value, label) ->
                        val selected = kind == value
                        val color = kindColor(value)
                        Row(
                            Modifier
                                .background(if (selected) color.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(99.dp))
                                .border(1.dp, if (selected) color else Border, RoundedCornerShape(99.dp))
                                .tapNoRipple { kind = value }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(7.dp).background(color, CircleShape))
                            Text(
                                label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                                color = if (selected) Ink else Muted,
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val canAdd = title.trim().isNotEmpty()
                    Box(
                        Modifier
                            .background(if (canAdd) Ink else Disabled, RoundedCornerShape(99.dp))
                            .tapNoRipple { if (canAdd) onCreate(title, startMin, duration, kind) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Add to plan", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Cream)
                    }
                    Box(
                        Modifier
                            .border(1.dp, Border, RoundedCornerShape(99.dp))
                            .tapNoRipple(onDismiss)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Cancel", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(glyph: String, onClick: () -> Unit) {
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
private fun PlanRow(block: PlanBlock, onToggle: () -> Unit, onDelete: () -> Unit) {
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
        Column(
            Modifier.align(Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CheckCircle(
                done = block.done,
                accent = color,
                size = 34.dp,
                borderColor = Border,
                idleGlyphColor = Disabled,
                onClick = onToggle,
            )
            Text(
                "✕", fontSize = 11.sp, color = Dashed,
                modifier = Modifier.tapNoRipple(onDelete),
            )
        }
    }
}
