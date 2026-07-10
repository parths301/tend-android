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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.data.db.TaskItem
import com.tend.app.domain.Time
import com.tend.app.ui.components.CheckCircle
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ScreenTitle
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Dashed
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.RowDivider
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Teal

private val GROUP_ORDER = listOf("PERSONAL", "WORK", "HEALTH")

@Composable
fun TasksScreen(vm: MainViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val done = tasks.count { it.done }
    val left = tasks.size - done

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
                            TaskRow(task, onToggle = { vm.toggleTask(task) }, onDelete = { vm.deleteTask(task) })
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
                            listOf("PERSONAL", "WORK", "HEALTH").forEach { g ->
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
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
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
        Text(
            task.title,
            modifier = Modifier.weight(1f),
            fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
            color = if (task.done) Faint else Ink,
            textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
        )
        Text(
            "✕", fontSize = 13.sp, color = Dashed,
            modifier = Modifier.padding(horizontal = 2.dp).tapNoRipple(onDelete),
        )
    }
}
