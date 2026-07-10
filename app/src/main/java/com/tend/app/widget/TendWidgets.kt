package com.tend.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Re-renders every Tend home-screen widget with fresh data. */
object TendWidgets {
    suspend fun refresh(context: Context) {
        TodayWidget().updateAll(context)
        StreakWidget().updateAll(context)
    }
}
