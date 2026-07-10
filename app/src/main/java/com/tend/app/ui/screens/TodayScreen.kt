package com.tend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.HabitUi
import com.tend.app.HabitView
import com.tend.app.MainViewModel
import com.tend.app.Tab
import com.tend.app.domain.Time
import com.tend.app.ui.components.CheckCircle
import com.tend.app.ui.components.HeatCell
import com.tend.app.ui.components.Heatmap
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ProgressRing
import com.tend.app.ui.components.ScreenTitle
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.Disabled
import com.tend.app.ui.theme.EmptyWeekCell
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.RingPast
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Terracotta
import com.tend.app.ui.theme.Track
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(vm: MainViewModel) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val shell by vm.shell.collectAsStateWithLifecycle()
    val weeks by vm.heatmapWeeks.collectAsStateWithLifecycle()

    val doneCount = habits.count { it.doneToday }
    val tasksLeft = tasks.count { !it.done }

    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Kicker(Time.kicker(vm.todayDate))
                Spacer(Modifier.height(1.dp))
                ScreenTitle("Today")
                Spacer(Modifier.height(2.dp))
                Text(
                    "$doneCount of ${habits.size} habits done · $tasksLeft tasks left",
                    fontSize = 12.5.sp, color = Muted,
                )
            }
            SettingsButton { vm.selectTab(Tab.Settings) }
        }

        WeekStrip(habits, vm.today)

        // Category chips + Grid/Week toggle
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("All", "Fitness", "Mind", "Work", "Health").forEach { cat ->
                    val selected = shell.filter == cat
                    Box(
                        Modifier
                            .background(if (selected) Ink else Color.Transparent, RoundedCornerShape(99.dp))
                            .border(1.dp, if (selected) Ink else Border, RoundedCornerShape(99.dp))
                            .tapNoRipple { vm.setFilter(cat) }
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Text(
                            cat, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (selected) com.tend.app.ui.theme.Cream else Muted,
                        )
                    }
                }
            }
            Row(Modifier.background(SegBg, RoundedCornerShape(10.dp)).padding(3.dp)) {
                SegButton("Grid", shell.view == HabitView.Grid) { vm.setView(HabitView.Grid) }
                SegButton("Week", shell.view == HabitView.Week) { vm.setView(HabitView.Week) }
            }
        }

        // Habit cards
        val shown = habits.filter { shell.filter == "All" || it.habit.category == shell.filter }
        shown.forEach { h ->
            HabitCard(
                h = h,
                gridView = shell.view == HabitView.Grid,
                weeks = weeks,
                today = vm.today,
                onOpen = { vm.openDetail(h.habit.id) },
                onToggle = { vm.toggleHabit(h.habit.id) },
            )
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(38.dp)
            .background(Card, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .tapNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("⚙︎", fontSize = 17.sp, color = Ink)
    }
}

@Composable
private fun WeekStrip(habits: List<HabitUi>, today: Long) {
    val monday = today - (java.time.LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
    val total = habits.size.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).forEach { offset ->
            val day = monday + offset
            val date = java.time.LocalDate.ofEpochDay(day)
            val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
            val isToday = day == today
            val isFuture = day > today
            val count = habits.count { day in it.doneDays }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    label, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                    color = if (isToday) Ink else Faint,
                )
                ProgressRing(
                    progress = if (isFuture) 0f else count.toFloat() / total,
                    ringColor = when {
                        isFuture -> Color.Transparent
                        isToday -> Terracotta
                        else -> RingPast
                    },
                    label = if (isFuture) "·" else "$count/$total",
                    labelColor = if (isFuture) Disabled else Ink,
                )
            }
        }
    }
}

@Composable
private fun SegButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) Card else Color.Transparent, RoundedCornerShape(8.dp))
            .tapNoRipple(onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Ink else Faint,
        )
    }
}

@Composable
private fun HabitCard(
    h: HabitUi,
    gridView: Boolean,
    weeks: Int,
    today: Long,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val color = Color(h.habit.colorHex)
    val soft = color.copy(alpha = 0.14f)
    TendCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(soft, RoundedCornerShape(13.dp))
                        .tapNoRipple(onOpen),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        h.habit.glyph, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, letterSpacing = 0.5.sp, color = color,
                    )
                }
                Column(Modifier.weight(1f).tapNoRipple(onOpen)) {
                    Text(h.habit.name, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Row {
                        Text(
                            "◆ ${h.streak}-day streak", fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = color,
                        )
                        Text(" · ${h.habit.goal}", fontSize = 12.sp, color = Muted)
                    }
                }
                HabitCheckButton(h, color, size = 46.dp, corner = 15.dp, onToggle = onToggle)
            }

            if (gridView) {
                val cells = (0 until weeks * 7).map { i ->
                    val day = today - (weeks * 7 - 1) + i
                    val on = if (day == today) h.doneToday else day in h.doneDays
                    HeatCell(color = if (on) color else Track, outlined = day == today)
                }
                Heatmap(cells, Modifier.fillMaxWidth())
            } else {
                WeekRow(h, color, today)
            }
        }
    }
}

@Composable
fun HabitCheckButton(
    h: HabitUi,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
) {
    val isTime = h.habit.type == "time"
    Box(
        Modifier
            .size(size)
            .then(
                if (h.doneToday) Modifier.background(color, RoundedCornerShape(corner))
                else Modifier.border(1.5.dp, Border, RoundedCornerShape(corner))
            )
            .tapNoRipple(onToggle),
        contentAlignment = Alignment.Center,
    ) {
        when {
            h.doneToday -> Text("✓", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Card)
            isTime -> Text("+30m", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = color)
            else -> Text("✓", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Faint)
        }
    }
}

@Composable
private fun WeekRow(h: HabitUi, color: Color, today: Long) {
    val monday = today - (java.time.LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..6).forEach { i ->
            val day = monday + i
            val isFuture = day > today
            val isToday = day == today
            val on = if (isToday) h.doneToday else day in h.doneDays
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(labels[i], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Faint)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(
                            when {
                                isFuture -> EmptyWeekCell
                                on -> color
                                else -> Track
                            },
                            RoundedCornerShape(11.dp),
                        )
                        .then(
                            when {
                                isToday -> Modifier.border(1.5.dp, Ink, RoundedCornerShape(11.dp))
                                isFuture -> Modifier.border(1.dp, SegBg, RoundedCornerShape(11.dp))
                                else -> Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!isFuture) {
                        Text(
                            if (on) "✓" else "·",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (on) Card else Disabled,
                        )
                    }
                }
            }
        }
    }
}
