package com.tend.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

@Database(
    entities = [
        Habit::class, HabitLog::class, TaskItem::class, PlanBlock::class, NoteEntry::class,
        ChatThread::class, ChatMessage::class, MessageLink::class, Attachment::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao
    abstract fun planDao(): PlanDao
    abstract fun noteDao(): NoteDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * v2 → v3 DDL, kept as constants so `RoomSchemaTest` can diff them against
         * the schema Room actually generates (`app/schemas/…/3.json`).
         *
         * Room validates a migrated database structurally at open time, so a
         * single wrong column type or a missing `ON UPDATE NO ACTION` throws
         * "Migration didn't properly handle" on a real user's upgrade. There is
         * no emulator in CI to catch that, hence the schema diff test.
         */
        val V3_TABLES = listOf(
            "CREATE TABLE IF NOT EXISTS `chat_threads` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`mode` TEXT NOT NULL, " +
                "`pinned` INTEGER NOT NULL, " +
                "`draft` TEXT NOT NULL)",

            "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`threadId` INTEGER NOT NULL, " +
                "`fromAi` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`modelId` TEXT, " +
                "`inContext` INTEGER NOT NULL, " +
                "`personalityId` INTEGER, " +
                "FOREIGN KEY(`threadId`) REFERENCES `chat_threads`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",

            "CREATE TABLE IF NOT EXISTS `message_links` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`messageId` INTEGER NOT NULL, " +
                "`entityType` TEXT NOT NULL, " +
                "`entityId` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`messageId`) REFERENCES `chat_messages`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",

            "CREATE TABLE IF NOT EXISTS `attachments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerType` TEXT NOT NULL, " +
                "`ownerId` INTEGER NOT NULL, " +
                "`uri` TEXT NOT NULL, " +
                "`mime` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, " +
                "`sizeBytes` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )

        val V3_INDICES = listOf(
            "CREATE INDEX IF NOT EXISTS `index_chat_messages_threadId` " +
                "ON `chat_messages` (`threadId`)",
            "CREATE INDEX IF NOT EXISTS `index_message_links_messageId` " +
                "ON `message_links` (`messageId`)",
            "CREATE INDEX IF NOT EXISTS `index_attachments_ownerType_ownerId` " +
                "ON `attachments` (`ownerType`, `ownerId`)",
        )

        /** v2 → v3: persisted chat threads, messages, entity links and attachments. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                (V3_TABLES + V3_INDICES).forEach(db::execSQL)
            }
        }

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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
