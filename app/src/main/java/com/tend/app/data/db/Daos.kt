package com.tend.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder")
    fun habits(): Flow<List<Habit>>

    @Query("SELECT * FROM habit_logs")
    fun logs(): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay LIMIT 1")
    suspend fun logFor(habitId: Long, epochDay: Long): HabitLog?

    // One-shot reads for home-screen widgets (no Flow collection outside the app UI)
    @Query("SELECT * FROM habits ORDER BY sortOrder")
    suspend fun habitsOnce(): List<Habit>

    @Query("SELECT * FROM habit_logs")
    suspend fun logsOnce(): List<HabitLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: HabitLog)

    @Insert
    suspend fun insertHabits(habits: List<Habit>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<HabitLog>)

    @Insert
    suspend fun insertHabit(habit: Habit): Long

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun count(): Int
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY sortOrder, id")
    fun tasks(): Flow<List<TaskItem>>

    @Update
    suspend fun update(task: TaskItem)

    @Insert
    suspend fun insert(task: TaskItem): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskItem>)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_blocks WHERE epochDay = :epochDay ORDER BY startMin")
    fun forDay(epochDay: Long): Flow<List<PlanBlock>>

    @Update
    suspend fun update(block: PlanBlock)

    @Insert
    suspend fun insert(block: PlanBlock): Long

    @Insert
    suspend fun insertAll(blocks: List<PlanBlock>)

    @Query("SELECT COUNT(*) FROM plan_blocks WHERE epochDay = :epochDay AND title = :title")
    suspend fun countByTitle(epochDay: Long, title: String): Int
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE habitId = :habitId ORDER BY timestamp DESC")
    fun forHabit(habitId: Long): Flow<List<NoteEntry>>

    @Insert
    suspend fun insert(note: NoteEntry): Long

    @Insert
    suspend fun insertAll(notes: List<NoteEntry>)
}
