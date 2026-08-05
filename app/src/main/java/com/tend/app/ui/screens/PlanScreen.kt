package com.tend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.data.CalEvent
import com.tend.app.data.db.PlanBlock
import com.tend.app.domain.Time
import com.tend.app.domain.chat.EntityRef
import com.tend.app.pdf.PlanPdfExporter
import com.tend.app.ui.components.CheckCircle
import com.tend.app.ui.components.DialogInput
import com.tend.app.ui.components.DialogLabel
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ScreenTitle
import com.tend.app.ui.components.Stepper
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.TimeStepperRow
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.motion.LocalTendHaptics
import com.tend.app.ui.motion.TendHaptic
import com.tend.app.ui.motion.bouncyTap
import com.tend.app.ui.motion.shakeOnError
import com.tend.app.ui.motion.successPop
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Dashed
import com.tend.app.ui.theme.Disabled
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Teal
import com.tend.app.ui.theme.Terracotta
import com.tend.app.ui.theme.Violet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

private fun kindColor(kind: String): Color = when (kind) {
    "focus" -> Violet
    "event" -> Teal
    else -> Terracotta
}

private fun kindTag(kind: String, source: String): String? = when {
    source == "auto" -> "✦ AUTO"
    kind == "focus" -> "FOCUS BLOCK"
    kind == "event" -> "CALENDAR"
    kind == "habit" -> "HABIT"
    else -> null
}

private sealed class TimelineRow(val start: Int, val end: Int) {
    class Block(val block: PlanBlock) : TimelineRow(block.startMin, block.endMin)
    class Cal(val event: CalEvent) : TimelineRow(event.startMin, event.endMin)
}

@Composable
fun PlanScreen(vm: MainViewModel) {
    val plan by vm.plan.collectAsStateWithLifecycle()
    val shell by vm.shell.collectAsStateWithLifecycle()
    val calEvents by vm.calendarEvents.collectAsStateWithLifecycle()
    val autoPlanning by vm.autoPlanning.collectAsStateWithLifecycle()
    val autoMessage by vm.autoPlanMessage.collectAsStateWithLifecycle()

    val isToday = shell.planDay == vm.today
    val date = LocalDate.ofEpochDay(shell.planDay)
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlanBlock?>(null) }

    // Same contract as TasksScreen: a chat chip opens the block's own editor.
    LaunchedEffect(shell.focusRef, plan) {
        val ref = shell.focusRef ?: return@LaunchedEffect
        if (ref.type != EntityRef.Type.Plan) return@LaunchedEffect
        plan.firstOrNull { it.id == ref.id }?.let { editing = it }
        vm.consumeFocusRef()
    }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Kicker(Time.kicker(date))
                Spacer(Modifier.height(1.dp))
                ScreenTitle("Plan")
            }
            Box(
                Modifier
                    .background(Ink, RoundedCornerShape(99.dp))
                    .tapNoRipple { showAdd = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text("+ Add block", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
            }
        }

        // Day navigation + actions
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Stepper("‹") { vm.shiftPlanDay(-1) }
            Stepper("›") { vm.shiftPlanDay(1) }
            if (!isToday) {
                Box(
                    Modifier
                        .border(1.dp, Border, RoundedCornerShape(99.dp))
                        .tapNoRipple { vm.planToday() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text("Back to today", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Muted)
                }
            }
            Spacer(Modifier.weight(1f))
            if (isToday) {
                Box(
                    Modifier
                        .background(if (autoPlanning) Disabled else Terracotta, RoundedCornerShape(99.dp))
                        .bouncyTap { if (!autoPlanning) vm.autoPlan() }
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (autoPlanning) "Planning…" else "✦ Auto-plan",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Cream,
                    )
                }
            }
            Box(
                Modifier
                    .border(1.dp, Border, RoundedCornerShape(99.dp))
                    .tapNoRipple {
                        scope.launch(Dispatchers.IO) {
                            val file = PlanPdfExporter.export(context, date, plan, calEvents)
                            withContext(Dispatchers.Main) { PlanPdfExporter.share(context, file) }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("PDF ↑", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Muted)
            }
        }

        // Auto-plan is a slow, remote call, so its answer has to arrive with
        // some weight: a plan that landed pops and confirms, one that failed
        // shakes and rejects.
        autoMessage?.let { message ->
            val haptics = LocalTendHaptics.current
            LaunchedEffect(message) {
                haptics.perform(if (message.failed) TendHaptic.Reject else TendHaptic.Confirm)
            }
            TendCard(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .shakeOnError(if (message.failed) message else null)
                    .successPop(!message.failed),
                corner = 16.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("✦", fontSize = 13.sp, color = Terracotta)
                    Text(
                        message.text,
                        fontSize = 12.5.sp,
                        lineHeight = 17.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "✕", fontSize = 12.sp, color = Faint,
                        modifier = Modifier.tapNoRipple { vm.clearAutoPlanMessage() },
                    )
                }
            }
        }

        val rows: List<TimelineRow> =
            (plan.map { TimelineRow.Block(it) } + calEvents.map { TimelineRow.Cal(it) })
                .sortedBy { it.start }

        if (rows.isEmpty()) {
            Text(
                if (isToday) "Nothing planned today — add a block, or let ✦ Auto-plan lay out your day."
                else "Nothing planned for this day.",
                fontSize = 12.5.sp, color = Muted,
            )
        }

        val nowMin = if (isToday) LocalTime.now().let { it.hour * 60 + it.minute } else null
        var nowLineDrawn = nowMin == null

        rows.forEachIndexed { index, row ->
            if (!nowLineDrawn && row.start > nowMin!!) {
                NowLine(nowMin)
                nowLineDrawn = true
            }
            if (index > 0) {
                val gap = row.start - rows[index - 1].end
                if (gap in 1..45) GapRow(gap)
            }
            when (row) {
                is TimelineRow.Block -> PlanRow(
                    row.block,
                    onToggle = { vm.togglePlan(row.block) },
                    onEdit = { editing = row.block },
                )
                is TimelineRow.Cal -> CalendarRow(row.event)
            }
        }
        if (!nowLineDrawn && rows.isNotEmpty()) {
            NowLine(nowMin!!)
        }
    }

    if (showAdd) {
        BlockDialog(
            title = "Add block",
            onSave = { blockTitle, start, duration, kind ->
                vm.addPlanBlock(blockTitle, start, duration, kind)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    editing?.let { block ->
        BlockDialog(
            title = "Edit block",
            initialTitle = block.title,
            initialStart = block.startMin,
            initialDuration = (block.endMin - block.startMin).coerceAtLeast(15),
            initialKind = block.kind,
            saveLabel = "Save",
            onSave = { blockTitle, start, duration, kind ->
                vm.updatePlanBlock(
                    block.copy(
                        title = blockTitle,
                        startMin = start,
                        endMin = start + duration,
                        kind = kind,
                    )
                )
                editing = null
            },
            onDelete = {
                vm.deletePlanBlock(block)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun NowLine(nowMin: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            Time.clock(nowMin),
            modifier = Modifier.width(44.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Terracotta,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Box(Modifier.size(7.dp).background(Terracotta, CircleShape))
        Box(Modifier.weight(1f).height(2.dp).background(Terracotta.copy(alpha = 0.55f), RoundedCornerShape(1.dp)))
    }
}

@Composable
private fun BlockDialog(
    title: String,
    onSave: (title: String, startMin: Int, durationMin: Int, kind: String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    initialTitle: String = "",
    initialStart: Int = 9 * 60,
    initialDuration: Int = 30,
    initialKind: String = "focus",
    saveLabel: String = "Add to plan",
) {
    var blockTitle by rememberSaveable { mutableStateOf(initialTitle) }
    var startMin by rememberSaveable { mutableStateOf(initialStart) }
    var duration by rememberSaveable { mutableStateOf(initialDuration) }
    var kind by rememberSaveable { mutableStateOf(initialKind) }

    Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontFamily = SpaceGrotesk, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                DialogLabel("Title")
                DialogInput(blockTitle, { blockTitle = it }, "e.g. Deep work")

                DialogLabel("Starts at")
                TimeStepperRow(startMin, { startMin = it })

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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val canSave = blockTitle.trim().isNotEmpty()
                    Box(
                        Modifier
                            .background(if (canSave) Ink else Disabled, RoundedCornerShape(99.dp))
                            .tapNoRipple { if (canSave) onSave(blockTitle, startMin, duration, kind) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(saveLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Cream)
                    }
                    Box(
                        Modifier
                            .border(1.dp, Border, RoundedCornerShape(99.dp))
                            .tapNoRipple(onDismiss)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Cancel", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Muted)
                    }
                    if (onDelete != null) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Delete", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                            color = Terracotta,
                            modifier = Modifier.tapNoRipple(onDelete).padding(horizontal = 4.dp, vertical = 10.dp),
                        )
                    }
                }
            }
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
private fun CalendarRow(event: CalEvent) {
    val soft = Teal.copy(alpha = 0.13f)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            Time.clock(event.startMin),
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
            Box(Modifier.size(10.dp).border(2.dp, Teal, CircleShape))
        }
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(Time.range(event.startMin, event.endMin), fontSize = 11.sp, color = Faint)
            Spacer(Modifier.height(1.dp))
            Text(event.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.background(soft, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text(
                    "CALENDAR", fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp, color = Teal,
                )
            }
        }
    }
}

@Composable
private fun PlanRow(block: PlanBlock, onToggle: () -> Unit, onEdit: () -> Unit) {
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
        Column(Modifier.weight(1f).padding(top = 2.dp).tapNoRipple(onEdit)) {
            Text(Time.range(block.startMin, block.endMin), fontSize = 11.sp, color = Faint)
            Spacer(Modifier.height(1.dp))
            Text(
                block.title,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (block.done) Faint else Ink,
                textDecoration = if (block.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            kindTag(block.kind, block.source)?.let { tag ->
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
                idleGlyphColor = Disabled,
                onClick = onToggle,
            )
        }
    }
}
