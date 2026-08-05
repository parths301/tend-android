package com.tend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.tend.app.data.db.TaskItem
import com.tend.app.domain.Time
import com.tend.app.domain.chat.EntityRef
import com.tend.app.ui.components.CheckCircle
import com.tend.app.ui.components.DashedAddBox
import com.tend.app.ui.components.DialogInput
import com.tend.app.ui.components.DialogLabel
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ScreenTitle
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.TimeStepperRow
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Dashed
import com.tend.app.ui.theme.Disabled
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.RowDivider
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Teal
import com.tend.app.ui.theme.Terracotta
import java.time.LocalDate

private val GROUP_ORDER = listOf("PERSONAL", "WORK", "HEALTH")

@Composable
fun TasksScreen(vm: MainViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val done = tasks.count { it.done }
    val left = tasks.size - done

    var editing by remember { mutableStateOf<TaskItem?>(null) }

    // A chat chip pointing at a task lands here. Opening the existing editor is
    // what makes the chip lead to the real thing rather than just the right tab,
    // and it means no parallel task-detail screen has to exist.
    val shell by vm.shell.collectAsStateWithLifecycle()
    LaunchedEffect(shell.focusRef, tasks) {
        val ref = shell.focusRef ?: return@LaunchedEffect
        if (ref.type != EntityRef.Type.Task) return@LaunchedEffect
        tasks.firstOrNull { it.id == ref.id }?.let { editing = it }
        vm.consumeFocusRef()
    }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
        Column(Modifier.padding(bottom = 14.dp)) {
            Kicker(Time.kicker(vm.todayDate))
            Spacer(Modifier.height(1.dp))
            ScreenTitle("Tasks")
            Spacer(Modifier.height(2.dp))
            Text("$done done · $left remaining", fontSize = 12.5.sp, color = Muted)
        }

        val groups = tasks.groupBy { it.groupName }
        val ordered = GROUP_ORDER.filter { it in groups } + groups.keys.filter { it !in GROUP_ORDER }.sorted()
        ordered.forEach { name ->
            val items = groups[name].orEmpty()
            Column(Modifier.padding(bottom = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Faint)
                    Text(
                        "${items.count { it.done }}/${items.size}",
                        fontFamily = SpaceGrotesk, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Faint,
                    )
                }
                TendCard(Modifier.fillMaxWidth(), corner = 18.dp) {
                    Column {
                        items.forEachIndexed { index, task ->
                            TaskRow(
                                task,
                                today = vm.today,
                                onToggle = { vm.toggleTask(task) },
                                onEdit = { editing = task },
                            )
                            if (index < items.lastIndex) {
                                HorizontalDivider(color = RowDivider, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            Text(
                "Nothing here yet — add your first task below.",
                fontSize = 12.5.sp, color = Muted,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        // Task composer
        var composing by rememberSaveable { mutableStateOf(false) }
        var title by rememberSaveable { mutableStateOf("") }
        var group by rememberSaveable { mutableStateOf("PERSONAL") }
        Box(Modifier.padding(top = 10.dp)) {
            if (!composing) {
                DashedAddBox("Add a task") { composing = true }
            } else {
                TendCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogInput(title, { title = it }, "Task title…")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GROUP_ORDER.forEach { g ->
                                val selected = group == g
                                Box(
                                    Modifier
                                        .background(if (selected) Ink else Color.Transparent, RoundedCornerShape(99.dp))
                                        .border(1.dp, if (selected) Ink else Border, RoundedCornerShape(99.dp))
                                        .tapNoRipple { group = g }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        g, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                                        color = if (selected) Cream else Muted,
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val canAdd = title.trim().isNotEmpty()
                            Box(
                                Modifier
                                    .background(if (canAdd) Ink else Dashed, RoundedCornerShape(99.dp))
                                    .tapNoRipple {
                                        if (canAdd) {
                                            vm.addTask(title, group)
                                            title = ""
                                            composing = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
                            }
                            Box(
                                Modifier
                                    .border(1.dp, Border, RoundedCornerShape(99.dp))
                                    .tapNoRipple { composing = false; title = "" }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
                            }
                        }
                        Text(
                            "Tip: tap any task to edit it, set a due time, or move groups.",
                            fontSize = 11.sp, color = Faint,
                        )
                    }
                }
            }
        }
    }

    editing?.let { task ->
        EditTaskDialog(
            task = task,
            today = vm.today,
            onSave = { vm.updateTask(it); editing = null },
            onDelete = { vm.deleteTask(task); editing = null },
            onDismiss = { editing = null },
        )
    }
}

private fun dueLabel(task: TaskItem, today: Long): Pair<String, Boolean>? {
    val day = task.dueDay ?: return null
    val time = task.dueMin?.let { " · ${Time.clockAmPm(it)}" } ?: ""
    val label = when (day) {
        today -> "Today$time"
        today + 1 -> "Tomorrow$time"
        else -> Time.shortDay(LocalDate.ofEpochDay(day)) + time
    }
    return label to (day < today && !task.done)
}

@Composable
private fun TaskRow(task: TaskItem, today: Long, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckCircle(
            done = task.done,
            accent = Teal,
            size = 24.dp,
            borderColor = Dashed,
            onClick = onToggle,
        )
        Column(Modifier.weight(1f).tapNoRipple(onEdit)) {
            Text(
                task.title,
                fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
                color = if (task.done) Faint else Ink,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            dueLabel(task, today)?.let { (label, overdue) ->
                Spacer(Modifier.height(2.dp))
                Text(
                    "⏰ $label",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (overdue) Terracotta else Faint,
                )
            }
        }
        Text(
            "›", fontSize = 16.sp, color = Dashed,
            modifier = Modifier.tapNoRipple(onEdit),
        )
    }
}

@Composable
private fun EditTaskDialog(
    task: TaskItem,
    today: Long,
    onSave: (TaskItem) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(task.title) }
    var group by rememberSaveable { mutableStateOf(task.groupName) }
    // 0 = none, 1 = today, 2 = tomorrow
    var dueChoice by rememberSaveable {
        mutableStateOf(
            when (task.dueDay) {
                null -> 0
                today -> 1
                today + 1 -> 2
                else -> 1
            }
        )
    }
    var dueMin by rememberSaveable { mutableStateOf(task.dueMin ?: 17 * 60) }

    Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Edit task", fontFamily = SpaceGrotesk, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                DialogLabel("Title")
                DialogInput(title, { title = it }, "Task title…")

                DialogLabel("Group")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GROUP_ORDER.forEach { g ->
                        val selected = group == g
                        Box(
                            Modifier
                                .background(if (selected) Ink else Color.Transparent, RoundedCornerShape(99.dp))
                                .border(1.dp, if (selected) Ink else Border, RoundedCornerShape(99.dp))
                                .tapNoRipple { group = g }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                g, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                                color = if (selected) Cream else Muted,
                            )
                        }
                    }
                }

                DialogLabel("Due & reminder")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "None", 1 to "Today", 2 to "Tomorrow").forEach { (value, label) ->
                        val selected = dueChoice == value
                        Box(
                            Modifier
                                .background(if (selected) Ink else Color.Transparent, RoundedCornerShape(99.dp))
                                .border(1.dp, if (selected) Ink else Border, RoundedCornerShape(99.dp))
                                .tapNoRipple { dueChoice = value }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                                color = if (selected) Cream else Muted,
                            )
                        }
                    }
                }
                if (dueChoice != 0) {
                    TimeStepperRow(dueMin, { dueMin = it })
                    Text("You'll get a notification at this time.", fontSize = 11.sp, color = Faint)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val canSave = title.trim().isNotEmpty()
                    Box(
                        Modifier
                            .background(if (canSave) Ink else Disabled, RoundedCornerShape(99.dp))
                            .tapNoRipple {
                                if (canSave) {
                                    onSave(
                                        task.copy(
                                            title = title.trim(),
                                            groupName = group,
                                            dueDay = when (dueChoice) {
                                                1 -> today
                                                2 -> today + 1
                                                else -> null
                                            },
                                            dueMin = if (dueChoice != 0) dueMin else null,
                                        )
                                    )
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Save", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Cream)
                    }
                    Box(
                        Modifier
                            .border(1.dp, Border, RoundedCornerShape(99.dp))
                            .tapNoRipple(onDismiss)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Cancel", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Muted)
                    }
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
