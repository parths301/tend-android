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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.data.db.TaskItem
import com.tend.app.domain.Time
import com.tend.app.ui.components.CheckCircle
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ScreenTitle
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.tapNoRipple
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
                            TaskRow(task) { vm.toggleTask(task) }
                            if (index < items.lastIndex) {
                                HorizontalDivider(color = RowDivider, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }

        // Dashed "add via AI" affordance
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .tapNoRipple { vm.openAi() }
        ) {
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
                Text("Add a task — or ask Tend", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit) {
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
        Text("›", fontSize = 16.sp, color = Dashed)
    }
}
