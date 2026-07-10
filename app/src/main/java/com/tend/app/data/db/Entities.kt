package com.tend.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val colorHex: Long,          // e.g. 0xFFD96F4E
    val glyph: String,           // two-letter badge, e.g. "MS"
    val type: String,            // "check" | "time" | "avoid"
    val goal: String,            // e.g. "Daily · 8:00 AM"
    val sortOrder: Int = 0,
    val createdDay: Long = 0,    // epoch day the habit was created; stats start here
    val reminderMin: Int? = null, // minutes since midnight; null = no reminder
)

@Entity(
    tableName = "habit_logs",
    indices = [Index(value = ["habitId", "epochDay"], unique = true)],
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    val done: Boolean,
    val minutes: Int = 0,        // logged time for "time" habits
)

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val groupName: String,       // "PERSONAL" | "WORK" | "HEALTH" | ...
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val dueDay: Long? = null,    // epoch day the task is due; null = no due date
    val dueMin: Int? = null,     // minutes since midnight; null = no due time
)

@Entity(tableName = "plan_blocks")
data class PlanBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startMin: Int,           // minutes since midnight
    val endMin: Int,
    val title: String,
    val kind: String,            // "habit" | "focus" | "event"
    val done: Boolean = false,
    val source: String = "manual", // "manual" | "habit" | "auto"
)

@Entity(tableName = "notes")
data class NoteEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val timestamp: Long,         // epoch millis
    val text: String,
)
