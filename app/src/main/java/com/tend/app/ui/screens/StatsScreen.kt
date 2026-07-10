package com.tend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.tend.app.MainViewModel
import com.tend.app.domain.Time
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ScreenTitle
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.InsightSub
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Teal
import com.tend.app.ui.theme.TealLight
import com.tend.app.ui.theme.Terracotta
import com.tend.app.ui.theme.TerracottaLight
import com.tend.app.ui.theme.Track
import com.tend.app.ui.theme.Violet
import com.tend.app.ui.theme.VioletLight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(vm: MainViewModel) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val today = vm.today

    // Derived stats — a habit only counts on days it already existed
    val bestHabit = habits.maxByOrNull { it.best }
    val weekDays = (today - 6)..today
    val possible = weekDays.sumOf { day -> habits.count { it.habit.createdDay <= day } }
    val weekDone = habits.sumOf { h -> weekDays.count { it in h.doneDays } }
    val weekPct = if (possible > 0) weekDone * 100 / possible else 0
    val timeHabits = habits.filter { it.habit.type == "time" }
    val focusWeekMin = timeHabits.sumOf { h -> weekDays.sumOf { d -> h.minutesByDay[d] ?: 0 } }

    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Kicker("LAST 30 DAYS")
            Spacer(Modifier.height(1.dp))
            ScreenTitle("Analytics")
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), Teal, "${bestHabit?.best ?: 0}", "best streak", bestHabit?.habit?.name ?: "—")
            StatCard(Modifier.weight(1f), Terracotta, "$weekPct%", "week completion", "$weekDone of $possible check-ins")
            StatCard(Modifier.weight(1f), Violet, Time.hours(focusWeekMin), "focus logged", "Deep Work · 7 days")
        }

        InsightsCard(habits, today, weekDone, possible, weekPct)
        FocusTrendCard(timeHabits, today)
        CompletionCard(habits)
    }
}

@Composable
private fun StatCard(modifier: Modifier, color: Color, big: String, label: String, sub: String) {
    TendCard(modifier, corner = 18.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(3.dp)))
            Spacer(Modifier.height(4.dp))
            Text(big, fontFamily = SpaceGrotesk, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Text(sub, fontSize = 10.5.sp, color = Faint, maxLines = 1)
        }
    }
}

@Composable
private fun InsightsCard(habits: List<HabitUi>, today: Long, weekDone: Int, possible: Int, weekPct: Int) {
    Box(Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(20.dp))) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
            Text(
                "INSIGHTS", fontSize = 10.5.sp, letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold, color = Muted,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val star = habits.maxByOrNull { it.streak }
                if (star != null) {
                    val personalBest = star.streak >= star.best
                    InsightRow(
                        glyph = "▲", accent = TerracottaLight,
                        title = if (personalBest) "Personal best on ${star.habit.name}"
                        else "Longest run: ${star.habit.name}",
                        sub = "${star.streak}-day streak" +
                            (if (personalBest) " — your longest ever. Keep it alive tonight."
                            else " — best is ${star.best}. Keep going."),
                    )
                }

                val focusInsight = focusDayInsight(habits, today)
                if (focusInsight != null) {
                    InsightRow(glyph = "◆", accent = VioletLight, title = focusInsight.first, sub = focusInsight.second)
                }

                InsightRow(
                    glyph = "●", accent = TealLight,
                    title = "$weekPct% week completion",
                    sub = "$weekDone of $possible check-ins done.",
                )
            }
        }
    }
}

/** Finds the weekday with the most Deep Work minutes over the last 30 days. */
private fun focusDayInsight(habits: List<HabitUi>, today: Long): Pair<String, String>? {
    val timeHabits = habits.filter { it.habit.type == "time" }
    if (timeHabits.isEmpty()) return null
    val byWeekday = mutableMapOf<DayOfWeek, Int>()
    for (day in (today - 29)..today) {
        val dow = LocalDate.ofEpochDay(day).dayOfWeek
        val minutes = timeHabits.sumOf { it.minutesByDay[day] ?: 0 }
        byWeekday[dow] = (byWeekday[dow] ?: 0) + minutes
    }
    val bestDay = byWeekday.maxByOrNull { it.value } ?: return null
    if (bestDay.value == 0) return null
    val othersAvg = byWeekday.filterKeys { it != bestDay.key }.values.average()
    val name = bestDay.key.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val pct = if (othersAvg > 0) (((bestDay.value - othersAvg) / othersAvg) * 100).toInt() else 100
    return "${name}s are your focus day" to "You log $pct% more Deep Work hours on ${name}s."
}

@Composable
private fun InsightRow(glyph: String, accent: Color, title: String, sub: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).background(accent.copy(alpha = 0.22f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Cream)
            Spacer(Modifier.height(1.dp))
            Text(sub, fontSize = 12.sp, color = InsightSub)
        }
    }
}

@Composable
private fun FocusTrendCard(timeHabits: List<HabitUi>, today: Long) {
    TendCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Focus hours", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("last 30 days", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Faint)
            }
            Spacer(Modifier.height(12.dp))
            val days = ((today - 29)..today).toList()
            val minutes = days.map { d -> timeHabits.sumOf { it.minutesByDay[d] ?: 0 } }
            val max = (minutes.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                minutes.forEachIndexed { i, m ->
                    val frac = m.toFloat() / max
                    Box(
                        Modifier
                            .weight(1f)
                            .height((4 + 92 * frac).dp)
                            .background(
                                if (i == minutes.lastIndex) Violet else Violet.copy(alpha = 0.4f),
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                            )
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Time.shortDay(LocalDate.ofEpochDay(today - 29)), fontSize = 10.sp, color = Faint)
                Text(Time.shortDay(LocalDate.ofEpochDay(today - 15)), fontSize = 10.sp, color = Faint)
                Text(Time.shortDay(LocalDate.ofEpochDay(today)), fontSize = 10.sp, color = Faint)
            }
        }
    }
}

@Composable
private fun CompletionCard(habits: List<HabitUi>) {
    TendCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("Completion by habit", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            habits.forEach { h ->
                val color = Color(h.habit.colorHex)
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(h.habit.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${h.rate30}%", fontFamily = SpaceGrotesk, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = color,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Track, RoundedCornerShape(3.dp))) {
                        Box(
                            Modifier
                                .fillMaxWidth(h.rate30 / 100f)
                                .fillMaxHeight()
                                .background(color, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}
