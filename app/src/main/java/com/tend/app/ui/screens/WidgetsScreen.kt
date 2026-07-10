package com.tend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.HabitUi
import com.tend.app.MainViewModel
import com.tend.app.Tab
import com.tend.app.data.Seed
import com.tend.app.domain.Time
import com.tend.app.ui.components.HeatCell
import com.tend.app.ui.components.Heatmap
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Gold
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.TealLight
import com.tend.app.ui.theme.Terracotta
import com.tend.app.ui.theme.VioletLight
import com.tend.app.ui.theme.WidgetBgBottom
import com.tend.app.ui.theme.WidgetBgMid
import com.tend.app.ui.theme.WidgetBgTop
import com.tend.app.ui.theme.WidgetCard
import com.tend.app.ui.theme.WidgetCardBorder

private val LabelDim = Color(0x80F5F1E8)   // rgba(245,241,232,.5)
private val TextDim = Color(0xA6F5F1E8)    // .65
private val TextDimmer = Color(0x8CF5F1E8) // .55
private val BarLabel = Color(0x73F5F1E8)   // .45

@Composable
fun WidgetsScreen(vm: MainViewModel) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val today = vm.today

    val read = habits.firstOrNull { it.habit.id == Seed.READ_ID }
    val stretch = habits.firstOrNull { it.habit.id == Seed.STRETCH_ID }
    val deep = habits.firstOrNull { it.habit.id == Seed.DEEP_WORK_ID }
    val noSugar = habits.firstOrNull { it.habit.id == Seed.NO_SUGAR_ID }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(WidgetBgTop, WidgetBgMid, WidgetBgBottom)))
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(Color(0x1AFFFFFF), RoundedCornerShape(50))
                    .tapNoRipple { vm.selectTab(Tab.Today) },
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", fontSize = 18.sp, color = Cream)
            }
            Column {
                Text(
                    "Home screen widgets", fontFamily = SpaceGrotesk,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Cream,
                )
                Text("Your habits, one glance away", fontSize = 11.5.sp, color = TextDimmer)
            }
        }

        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                read?.let { StreakWidget(Modifier.weight(1f), it, today) }
                noSugar?.let { QuickCheckWidget(Modifier.weight(1f), it) { vm.toggleHabit(it.habit.id) } }
            }
            stretch?.let { HeatmapWidget(it, today) { vm.toggleHabit(it.habit.id) } }
            deep?.let { DeepWorkWidget(it, today) }
        }
    }
}

@Composable
private fun WidgetCardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(WidgetCard, RoundedCornerShape(22.dp))
            .border(1.dp, WidgetCardBorder, RoundedCornerShape(22.dp))
    ) { content() }
}

@Composable
private fun WidgetLabel(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = LabelDim)
}

@Composable
private fun StreakWidget(modifier: Modifier, h: HabitUi, today: Long) {
    WidgetCardBox(modifier) {
        Column(Modifier.padding(14.dp)) {
            WidgetLabel(h.habit.name.uppercase())
            Spacer(Modifier.height(4.dp))
            Text(
                "${h.streak}", fontFamily = SpaceGrotesk, fontSize = 34.sp,
                fontWeight = FontWeight.Bold, color = TealLight,
            )
            Text("day streak", fontSize = 11.sp, color = TextDim)
            Spacer(Modifier.height(10.dp))
            val monday = today - (java.time.LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..6).forEach { i ->
                    val day = monday + i
                    val on = day <= today && day in h.doneDays
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(if (on) TealLight else Color(0x1FFFFFFF), RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCheckWidget(modifier: Modifier, h: HabitUi, onToggle: () -> Unit) {
    WidgetCardBox(modifier) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            WidgetLabel(h.habit.name.uppercase())
            Spacer(Modifier.height(4.dp))
            Text("${h.streak} days clean", fontSize = 11.sp, color = TextDim)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .align(Alignment.End)
                    .size(44.dp)
                    .then(
                        if (h.doneToday) Modifier.background(Gold, RoundedCornerShape(15.dp))
                        else Modifier.border(1.5.dp, Color(0x40FFFFFF), RoundedCornerShape(15.dp))
                    )
                    .tapNoRipple(onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✓", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = if (h.doneToday) Color(0xFF181818) else Color(0x80FFFFFF),
                )
            }
        }
    }
}

@Composable
private fun HeatmapWidget(h: HabitUi, today: Long, onToggle: () -> Unit) {
    val color = Color(h.habit.colorHex)
    WidgetCardBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(h.habit.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Cream)
                    Text("◆ ${h.streak}-day streak", fontSize = 11.sp, color = TextDimmer)
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .then(
                            if (h.doneToday) Modifier.background(Terracotta, RoundedCornerShape(13.dp))
                            else Modifier.border(1.5.dp, Color(0x40FFFFFF), RoundedCornerShape(13.dp))
                        )
                        .tapNoRipple(onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✓", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (h.doneToday) com.tend.app.ui.theme.Card else Color(0x80FFFFFF),
                    )
                }
            }
            val weeks = 16
            val cells = (0 until weeks * 7).map { i ->
                val day = today - (weeks * 7 - 1) + i
                val on = if (day == today) h.doneToday else day in h.doneDays
                HeatCell(color = if (on) color else Color(0x17FFFFFF), outlined = day == today)
            }
            Heatmap(
                cells,
                Modifier.fillMaxWidth(),
                cellSize = 11.dp,
                gap = 3.5.dp,
                corner = 3.5.dp,
                outline = Cream,
                endAligned = false,
            )
        }
    }
}

@Composable
private fun DeepWorkWidget(h: HabitUi, today: Long) {
    val monday = today - (java.time.LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
    val week = (0..6).map { h.minutesByDay[monday + it] ?: 0 }
    val total = week.sum()
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    WidgetCardBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(h.habit.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Cream)
                Text(
                    "${Time.hours(total)} this week", fontFamily = SpaceGrotesk,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VioletLight,
                )
            }
            val max = (week.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                week.forEachIndexed { i, minutes ->
                    val isToday = monday + i == today
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        val heightFrac = minutes.toFloat() / max
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((4 + 46 * heightFrac).dp)
                                .background(
                                    if (isToday) VioletLight else VioletLight.copy(alpha = 0.35f),
                                    RoundedCornerShape(6.dp),
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(labels[i], fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BarLabel)
                    }
                }
            }
        }
    }
}
