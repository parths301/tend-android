package com.tend.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tend.app.MainActivity

/**
 * 2x2 home-screen widget: your best running streak with a week strip.
 * Appears in the launcher's widget picker as "Tend · Streak".
 */
class StreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val habit = WidgetData.load(context).maxByOrNull { it.streak }
        provideContent { StreakContent(habit) }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

@Composable
private fun StreakContent(habit: WidgetHabit?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetPalette.bg)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
    ) {
        if (habit == null) {
            Text(
                "Open Tend to add your first habit",
                style = TextStyle(color = ColorProvider(WidgetPalette.dim), fontSize = 12.sp),
            )
            return@Column
        }
        Text(
            habit.name.uppercase(),
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(WidgetPalette.label),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            "${habit.streak}",
            style = TextStyle(
                color = ColorProvider(Color(habit.colorHex)),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            "day streak",
            style = TextStyle(color = ColorProvider(WidgetPalette.dim), fontSize = 11.sp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            habit.week.forEach { on ->
                Box(modifier = GlanceModifier.defaultWeight().padding(horizontal = 2.dp)) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .cornerRadius(3.dp)
                            .background(if (on) Color(habit.colorHex) else WidgetPalette.cellOff),
                    ) {}
                }
            }
        }
    }
}
