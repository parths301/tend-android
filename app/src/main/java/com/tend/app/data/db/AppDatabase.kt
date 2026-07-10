package com.tend.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

@Database(
    entities = [Habit::class, HabitLog::class, TaskItem::class, PlanBlock::class, NoteEntry::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao
    abstract fun planDao(): PlanDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 → v2: reminder/due times, habit creation day, plan-block provenance. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val today = LocalDate.now().toEpochDay()
                db.execSQL("ALTER TABLE habits ADD COLUMN createdDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN reminderMin INTEGER")
                db.execSQL(
                    "UPDATE habits SET createdDay = COALESCE(" +
                        "(SELECT MIN(epochDay) FROM habit_logs WHERE habit_logs.habitId = habits.id), $today)"
                )
                db.execSQL("ALTER TABLE tasks ADD COLUMN dueDay INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN dueMin INTEGER")
                db.execSQL("ALTER TABLE plan_blocks ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tend.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
