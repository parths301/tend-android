package com.tend.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tend.app.MainActivity
import com.tend.app.data.TendRepository
import com.tend.app.data.db.AppDatabase
import java.time.LocalDate

/**
 * 4x2 home-screen widget: today's habits with one-tap check-off.
 * Appears in the launcher's widget picker as "Tend · Today".
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val habits = WidgetData.load(context)
        provideContent { TodayContent(habits) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** Checks a habit off (or un-checks it) straight from the widget. */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[habitIdKey] ?: return
        val db = AppDatabase.get(context)
        val habit = db.habitDao().habitsOnce().firstOrNull { it.id == habitId } ?: return
        TendRepository(db).toggleHabitToday(habit, LocalDate.now().toEpochDay())
        TendWidgets.refresh(context)
    }

    companion object {
        val habitIdKey = ActionParameters.Key<Long>("habit_id")
    }
}

@Composable
private fun TodayContent(habits: List<WidgetHabit>) {
    val shown = habits.take(4)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetPalette.bg)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TEND · TODAY",
                style = TextStyle(
                    color = ColorProvider(WidgetPalette.label),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                "${shown.count { it.doneToday }}/${shown.size} done",
                style = TextStyle(color = ColorProvider(WidgetPalette.dim), fontSize = 10.sp),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        if (shown.isEmpty()) {
            Text(
                "Open Tend to add your first habit",
                style = TextStyle(color = ColorProvider(WidgetPalette.dim), fontSize = 12.sp),
            )
        } else {
            shown.forEach { habit -> HabitRow(habit) }
        }
    }
}

@Composable
private fun HabitRow(habit: WidgetHabit) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                habit.name,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(WidgetPalette.cream),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                "◆ ${habit.streak}-day streak",
                style = TextStyle(color = ColorProvider(WidgetPalette.dim), fontSize = 10.sp),
            )
        }
        Box(
            modifier = GlanceModifier
                .size(30.dp)
                .cornerRadius(10.dp)
                .background(if (habit.doneToday) Color(habit.colorHex) else WidgetPalette.checkOff)
                .clickable(
                    actionRunCallback<ToggleHabitAction>(
                        actionParametersOf(ToggleHabitAction.habitIdKey to habit.id)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "✓",
                style = TextStyle(
                    color = ColorProvider(if (habit.doneToday) WidgetPalette.bg else WidgetPalette.dim),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

/** Dark palette shared by the Tend widgets (mirrors the app's widget mock). */
internal object WidgetPalette {
    val bg = Color(0xFF1D2925)
    val cream = Color(0xFFF5F1E8)
    val label = Color(0x80F5F1E8)
    val dim = Color(0x99F5F1E8)
    val checkOff = Color(0x26FFFFFF)
    val cellOff = Color(0x21FFFFFF)
}
