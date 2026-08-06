package com.tend.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.Tab
import com.tend.app.data.TendRepository
import com.tend.app.ui.components.AttachmentsSection
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.screens.HabitCheckButton
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Dashed
import com.tend.app.ui.theme.Disabled
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Terracotta
import com.tend.app.ui.theme.Track
import com.tend.app.domain.Time
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DetailScreen(vm: MainViewModel) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val shell by vm.shell.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val customs by vm.customCategories.collectAsStateWithLifecycle()
    val h = habits.firstOrNull { it.habit.id == shell.detailHabitId } ?: return
    val color = Color(h.habit.colorHex)
    val soft = color.copy(alpha = 0.14f)
    var showEdit by remember { mutableStateOf(false) }

    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .border(1.dp, Border, RoundedCornerShape(50))
                    .tapNoRipple { vm.selectTab(Tab.Today) },
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", fontSize = 18.sp)
            }
            Box(
                Modifier.size(42.dp).background(soft, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    h.habit.glyph, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = color,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    h.habit.name, fontFamily = SpaceGrotesk, fontSize = 19.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
                )
                Row {
                    Text("◆ ${h.streak}-day streak", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                    val extra = h.habit.reminderMin?.let { " · ⏰ ${Time.clockAmPm(it)}" } ?: " · ${h.habit.goal}"
                    Text(extra, fontSize = 12.sp, color = Muted)
                }
            }
            Box(
                Modifier
                    .size(34.dp)
                    .border(1.dp, Border, RoundedCornerShape(11.dp))
                    .tapNoRipple { showEdit = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("✎", fontSize = 14.sp, color = Muted)
            }
            HabitCheckButton(h, color, size = 44.dp, corner = 14.dp) { vm.toggleHabit(h.habit.id) }
        }

        if (showEdit) {
            HabitDialog(
                title = "Edit habit",
                customs = customs,
                onAddCustom = vm::addCustomCategory,
                initialName = h.habit.name,
                initialCategory = h.habit.category,
                initialType = h.habit.type,
                initialGoal = h.habit.goal,
                initialReminderMin = h.habit.reminderMin,
                initialColorHex = h.habit.colorHex,
                initialGlyph = h.habit.glyph,
                saveLabel = "Save",
                onSave = { name, category, type, goal, reminderMin, colorHex, glyph ->
                    vm.updateHabit(
                        h.habit.copy(
                            name = name.trim(),
                            category = category,
                            type = type,
                            goal = goal.trim().ifEmpty { "Daily" },
                            reminderMin = reminderMin,
                            colorHex = colorHex,
                            glyph = glyph,
                        )
                    )
                    showEdit = false
                },
                onDismiss = { showEdit = false },
            )
        }

        CalendarCard(h.doneDays, h.doneToday, color, vm.today, h.habit.createdDay)

        // Streak stats
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(Modifier.weight(1f), "${h.streak}", "current streak")
            MiniStat(Modifier.weight(1f), "${h.best}", "best streak")
            MiniStat(Modifier.weight(1f), "${h.rate30}%", "30-day rate")
        }

        // Notes
        Column {
            Text(
                "NOTES", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                color = Faint, modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 8.dp),
            )
            var noteInput by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .background(Card, RoundedCornerShape(13.dp))
                        .border(1.dp, Border, RoundedCornerShape(13.dp))
                        .padding(horizontal = 13.dp, vertical = 11.dp)
                ) {
                    BasicTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            vm.addNote(noteInput); noteInput = ""
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (noteInput.isEmpty()) {
                        Text("Add a note for today…", fontSize = 13.sp, color = Faint)
                    }
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .background(Ink, RoundedCornerShape(13.dp))
                        .tapNoRipple { vm.addNote(noteInput); noteInput = "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↑", fontSize = 17.sp, color = Cream)
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notes.forEach { note ->
                    TendCard(Modifier.fillMaxWidth(), corner = 14.dp) {
                        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
                            Text(
                                noteStamp(note.timestamp),
                                fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Faint,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(note.text, fontSize = 13.5.sp, lineHeight = 19.5.sp)
                        }
                    }
                }
            }
        }

        AttachmentsSection(vm, ownerType = TendRepository.OWNER_HABIT, ownerId = h.habit.id)

        // Danger zone — two taps to delete, history and notes included
        var confirmDelete by remember { mutableStateOf(false) }
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, if (confirmDelete) Terracotta else Border, RoundedCornerShape(14.dp))
                .tapNoRipple {
                    if (confirmDelete) vm.deleteHabit(h.habit.id) else confirmDelete = true
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (confirmDelete) "Tap again to delete \"${h.habit.name}\" and its history"
                else "Delete habit",
                fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                color = if (confirmDelete) Terracotta else Muted,
            )
        }
    }
}

private fun noteStamp(millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val day = dt.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
    val time = dt.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    return "$day · $time"
}

@Composable
private fun MiniStat(modifier: Modifier, big: String, label: String) {
    TendCard(modifier, corner = 16.dp) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(big, fontFamily = SpaceGrotesk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        }
    }
}

@Composable
private fun CalendarCard(doneDays: Set<Long>, doneToday: Boolean, color: Color, today: Long, createdDay: Long) {
    var monthOffset by remember { mutableIntStateOf(0) }
    val todayDate = LocalDate.ofEpochDay(today)
    val month = todayDate.plusMonths(monthOffset.toLong()).withDayOfMonth(1)

    TendCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹", fontSize = 16.sp, color = Dashed,
                    modifier = Modifier.padding(horizontal = 6.dp).tapNoRipple { monthOffset-- },
                )
                Text(
                    Time.monthTitle(month), fontFamily = SpaceGrotesk,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    "›", fontSize = 16.sp,
                    color = if (monthOffset < 0) Ink else Dashed,
                    modifier = Modifier.padding(horizontal = 6.dp).tapNoRipple {
                        if (monthOffset < 0) monthOffset++
                    },
                )
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU").forEach {
                    Text(
                        it, modifier = Modifier.weight(1f),
                        fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Faint,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            val leadingBlanks = month.dayOfWeek.value - 1 // Monday-first
            val daysInMonth = month.lengthOfMonth()
            val cells: List<Long?> = List(leadingBlanks) { null } +
                (1..daysInMonth).map { month.withDayOfMonth(it).toEpochDay() }
            cells.chunked(7).forEach { week ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    week.forEach { epoch ->
                        CalendarCell(Modifier.weight(1f), epoch, doneDays, doneToday, color, today, createdDay)
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    modifier: Modifier,
    epoch: Long?,
    doneDays: Set<Long>,
    doneToday: Boolean,
    color: Color,
    today: Long,
    createdDay: Long,
) {
    if (epoch == null) {
        Spacer(modifier.aspectRatio(1f))
        return
    }
    val dayOfMonth = LocalDate.ofEpochDay(epoch).dayOfMonth
    // Days before the habit existed render like future days — they don't count.
    val outside = epoch > today || epoch < createdDay
    val isToday = epoch == today
    val on = if (isToday) doneToday else epoch in doneDays
    Box(
        modifier
            .aspectRatio(1f)
            .then(
                when {
                    outside -> Modifier.border(1.dp, SegBg, RoundedCornerShape(10.dp))
                    on -> Modifier.background(color, RoundedCornerShape(10.dp))
                    else -> Modifier.background(Track, RoundedCornerShape(10.dp))
                }
            )
            .then(
                if (isToday) Modifier.border(1.5.dp, Ink, RoundedCornerShape(10.dp)) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$dayOfMonth", fontFamily = SpaceGrotesk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = when {
                outside -> Disabled
                on -> Card
                else -> Muted
            },
        )
    }
}
